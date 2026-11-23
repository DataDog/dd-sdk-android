/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.FlagsAssignmentAuthorizationException
import com.datadog.android.flags.FlagsContextSupersededException
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.FlagsInvalidContextException
import com.datadog.android.flags.FlagsResponseSigningException
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.diagnostics.UUIDv7Generator
import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Fetch
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Sdk
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Trigger
import com.datadog.android.flags.internal.net.NetworkRequestFailedException
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsResponse
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsResult
import com.datadog.android.flags.internal.net.SignedAssignmentsResponse
import com.datadog.android.flags.internal.repository.FlagsInstallation
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.internal.selection.logIgnoredFlagSelection
import com.datadog.android.flags.internal.signing.ResponseSigningState
import com.datadog.android.flags.internal.signing.VerifiedFlagsExpiredException
import com.datadog.android.flags.internal.signing.VerifiedResponse
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal fun interface InitializationTimeoutScheduler {
    fun schedule(timeoutMs: Long, action: () -> Unit): () -> Unit
}

private data class InitializationResult(
    val callback: EvaluationContextCallback?,
    val cancelTimeout: (() -> Unit)? = null
)

private data class AcceptedTimeoutFallback(
    val request: ExperimentationOptOut.Request,
    val authorizationProtected: Boolean
)

private class InitializationCompletion(private var callback: EvaluationContextCallback?) {
    private val lock = Any()
    private var isPending = true
    private var cancelTimeout: (() -> Unit)? = null
    private var cancelTimeoutWhenArmed = false

    fun armTimeoutCancellation(cancellation: () -> Unit) {
        val cancelNow = synchronized(lock) {
            if (isPending) {
                cancelTimeout = cancellation
                false
            } else {
                cancelTimeoutWhenArmed
            }
        }
        if (cancelNow) cancellation()
    }

    fun take(shouldCancelTimeout: Boolean = true): InitializationResult? {
        val outcome = synchronized(lock) {
            if (!isPending) return null
            isPending = false
            val timeoutCancellation = cancelTimeout
            cancelTimeoutWhenArmed = shouldCancelTimeout && timeoutCancellation == null
            val outcome = callback to if (shouldCancelTimeout) timeoutCancellation else null
            callback = null
            cancelTimeout = null
            outcome
        }
        return InitializationResult(outcome.first, outcome.second)
    }
}

/**
 * Orchestrates evaluations for a given context and stores the results in the repository.
 *
 * This class coordinates between network operations, data transformation, and local storage
 * to provide atomic updates of flag evaluations. All operations are performed asynchronously
 * on a dedicated executor to avoid blocking the calling thread.
 *
 * @param sdkCore SDK core
 * @param executorService dedicated executor for background operations
 * @param internalLogger logger for debug and error messages
 * @param flagsRepository local storage for flag data and evaluation context
 * @param assignmentsReader handles reading assignments for the context.
 * @param precomputeMapper transforms network responses into internal flag format
 * @param flagStateManager channel for notifying state change listeners
 * @param initializationTimeoutMs optional maximum duration of the first context operation
 * @param initializationTimeoutScheduler schedules the first context timeout
 * @param signing verifies proof lifetime and request scope before admission; null without signing opt-in
 * @param experimentationOptOut validates contexts and prevents superseded installations
 * @param callbackExecutor delivers customer callbacks outside state delivery and network operations
 * @param assignmentAuthorization extracts assignment credentials and enforces subject isolation
 * @param unsupportedSigningError fails every fetch without a request; null unless signing is required but unavailable
 */
// Each step of the fetch, install and completion lifecycle is a separate function.
@Suppress("TooManyFunctions", "LargeClass")
internal class EvaluationsManager(
    private val sdkCore: FeatureSdkCore,
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    private val flagsRepository: FlagsRepository,
    private val assignmentsReader: PrecomputedAssignmentsReader,
    private val precomputeMapper: PrecomputeMapper,
    private val flagStateManager: FlagsStateManager,
    private val initializationTimeoutMs: Long?,
    private val initializationTimeoutScheduler: InitializationTimeoutScheduler,
    private val signing: ResponseSigningState? = null,
    private val experimentationOptOut: ExperimentationOptOut,
    private val callbackExecutor: Executor = InlineExecutor,
    private val assignmentAuthorization: AssignmentAuthorization =
        AssignmentAuthorization(internalLogger, experimentationOptOut.stateLock),
    private val unsupportedSigningError: Throwable? = null
) {
    private val instanceId = UUIDv7Generator.generate(sdkCore.timeProvider.getDeviceTimestampMillis()).toString()
    private val fetchSequence = AtomicLong(0)
    private val didStartInitialization = AtomicBoolean(false)
    private val initializationTerminalLock = experimentationOptOut.stateLock
    private val effects = EvaluationEffects(initializationTerminalLock, internalLogger)
    private var currentState: FlagsClientState = FlagsClientState.NotReady
    private var acceptedTimeoutFallback: AcceptedTimeoutFallback? = null
    private var proofExpiryToken: Any? = null
    private var proofExpiresAt: Long = Long.MAX_VALUE
    private var cancelProofExpiry: (() -> Unit)? = null

    /**
     * Processes a new evaluation context by fetching flags and storing atomically.
     *
     * This method asynchronously fetches precomputed flag evaluations for the given context
     * and atomically updates the context, flag data, and encoding descriptor in the repository. A failed or unreadable
     * response keeps the last known good flags and their context.
     *
     * The operation is performed on the configured executor service and will not block the
     * calling thread. Errors are logged but do not propagate to the caller.
     *
     * @param context The evaluation context to process. Must be non-null and contain
     * a valid targeting key.
     * @param callback Optional callback invoked when the context is set and the flags have been fetched successfully or not.
     * @param onFetchFinished releases pending request state when the fetch ends, including rejected dispatch.
     */
    fun updateEvaluationsForContext(
        context: EvaluationContext,
        callback: EvaluationContextCallback? = null,
        onFetchFinished: () -> Unit = {}
    ) {
        updatePreparedEvaluations(prepare(context), callback, onFetchFinished)
    }

    internal fun prepare(context: EvaluationContext): ExperimentationOptOut.Request {
        return synchronized(initializationTerminalLock) {
            acceptedTimeoutFallback = null
            val authorization = assignmentAuthorization.prepare(context)
            experimentationOptOut.prepare(authorization.context, authorization)
        }
    }

    fun updatePreparedEvaluations(
        request: ExperimentationOptOut.Request,
        callback: EvaluationContextCallback?,
        onFetchFinished: () -> Unit
    ) {
        var generation: Long? = null
        if (signing != null && !experimentationOptOut.ifLatest(request) {
                generation = signing.begin(request.context)
            }
        ) {
            synchronized(initializationTerminalLock) {
                enqueueCompletion(InitializationResult(callback), FlagsContextSupersededException(SUPERSEDED_MESSAGE))
            }
            effects.drain()
            onFetchFinished()
            return
        }
        // Unsupported required signing fails before any request; the client logged it once at build.
        val rejection = unsupportedSigningError ?: if (request.invalid) {
            FlagsInvalidContextException(ExperimentationOptOut.INVALID_CONTEXT_MESSAGE)
        } else {
            null
        }
        if (rejection != null) {
            synchronized(initializationTerminalLock) {
                ifCurrent(request) { enqueueState(FlagsClientState.Error(rejection)) }
                enqueueCompletion(InitializationResult(callback), rejection)
            }
            effects.drain()
            if (rejection is FlagsInvalidContextException) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { ExperimentationOptOut.INVALID_CONTEXT_MESSAGE }
                )
            }
            onFetchFinished()
            return
        }
        val registeredGeneration = generation
        if (signing != null && registeredGeneration != null) {
            fetchSignedEvaluations(request, registeredGeneration, callback, onFetchFinished, signing)
        } else {
            fetchEvaluations(request, callback, onFetchFinished)
        }
    }

    @Suppress("LongMethod") // Keep diagnostics and opt-out checks with the fetch lifecycle.
    private fun fetchEvaluations(
        request: ExperimentationOptOut.Request,
        callback: EvaluationContextCallback?,
        onFetchFinished: () -> Unit
    ) {
        val context = request.context
        val sequence = fetchSequence.incrementAndGet()
        val diagnostics = Diagnostics(
            Sdk(instanceId),
            Fetch(sequence, if (sequence == 1L) Trigger.INITIALIZATION else Trigger.CONTEXT_CHANGE)
        )
        val cachedContext = AtomicReference<EvaluationContext?>()
        val initializationCompletion = startInitializationTimeout(request, callback, cachedContext) {
            ifCurrent(request) { enqueueState(FlagsClientState.Reconciling) }
            effects.drain()
        }
        if (initializationCompletion == null) {
            ifCurrent(request) { enqueueState(FlagsClientState.Reconciling) }
            effects.drain()
        }

        completionAwareExecutor(onFetchFinished).executeSafe(FETCH_AND_STORE_OPERATION_NAME, internalLogger) {
            try {
                // Read on the fetch executor so unavailable core context still releases pending telemetry state.
                // withContext can silently skip its callback after shutdown or rejected dispatch.
                val datadogContext = if (sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME) != null) {
                    (sdkCore as? InternalSdkCore)?.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))
                } else {
                    null
                }
                // The React Native bridge requires original flag names, which encoded data omits.
                val supportsObfuscation = datadogContext?.source == "android"
                internalLogger.log(
                    InternalLogger.Level.DEBUG,
                    InternalLogger.Target.MAINTAINER,
                    { "Processing evaluation context: ${context.targetingKey}" }
                )
                @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts the repository's nullable snapshot.
                cachedContext.set(
                    if (flagsRepository.hasConfiguration()) flagsRepository.getEvaluationContext() else null
                )
                val result = datadogContext?.let { datadogContext ->
                    val assignmentToken = request.assignmentAuthorization?.token
                    if (assignmentToken == null) {
                        assignmentsReader.readPrecomputedFlags(context, datadogContext, diagnostics)
                    } else {
                        assignmentsReader.readPrecomputedFlags(
                            context,
                            datadogContext,
                            diagnostics,
                            assignmentToken
                        )
                    }
                }
                when (result) {
                    is PrecomputedAssignmentsResponse -> {
                        val response = result
                        val assignments = mapAssignments(response.body, request, supportsObfuscation)
                        if (assignments != null) {
                            installFlags(
                                request,
                                assignments,
                                response.flagSelectionIgnoredReason,
                                initializationCompletion,
                                callback
                            )
                        } else {
                            handleRequestFailure(
                                request,
                                initializationCompletion,
                                callback,
                                cachedContext.get(),
                                networkFailed = false,
                                authorizationRejected = false
                            )
                        }
                    }

                    PrecomputedAssignmentsResult.AuthorizationRejected -> handleRequestFailure(
                        request,
                        initializationCompletion,
                        callback,
                        cachedContext.get(),
                        networkFailed = false,
                        authorizationRejected = true
                    )

                    // Only the signed read returns these types; an unsigned fetch treats them as a network failure.
                    is SignedAssignmentsResponse,
                    is PrecomputedAssignmentsResult.SigningRejected,
                    null -> handleRequestFailure(
                        request,
                        initializationCompletion,
                        callback,
                        cachedContext.get(),
                        networkFailed = true,
                        authorizationRejected = false
                    )
                }
            } finally {
                // Timeouts complete the public callback but do not stop this fetch.
                onFetchFinished()
            }
        }
    }

    private fun mapAssignments(
        body: String,
        request: ExperimentationOptOut.Request,
        supportsObfuscation: Boolean
    ): PrecomputedAssignments? {
        val experimentationOptOut = request.takeIf { it.optedOut }
        val assignments = if (request.assignmentAuthorization?.token == null) {
            precomputeMapper.map(body, experimentationOptOut)
        } else {
            precomputeMapper.map(body, experimentationOptOut, redactErrors = true)
        }
        return assignments?.takeIf { it.obfuscation == null || supportsObfuscation }
    }

    // Keep proof, cancellation and context gates atomic with installation.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun fetchSignedEvaluations(
        request: ExperimentationOptOut.Request,
        generation: Long,
        callback: EvaluationContextCallback?,
        onFetchFinished: () -> Unit,
        signing: ResponseSigningState
    ) {
        val context = request.context
        val sequence = fetchSequence.incrementAndGet()
        val diagnostics = Diagnostics(
            Sdk(instanceId),
            Fetch(sequence, if (sequence == 1L) Trigger.INITIALIZATION else Trigger.CONTEXT_CHANGE)
        )
        val cachedContext = AtomicReference<EvaluationContext?>()
        val completion = startInitializationTimeout(
            request,
            callback,
            cachedContext,
            // A newer context keeps the initialization deadline; only a lifecycle change cancels its transition.
            { signing.isIdentityCurrent() }
        ) {
            ifCurrent(request) { enqueueState(FlagsClientState.Reconciling) }
            effects.drain()
        }
        if (completion == null) {
            ifCurrent(request) { enqueueState(FlagsClientState.Reconciling) }
            effects.drain()
        }
        completionAwareExecutor(onFetchFinished).executeSafe(FETCH_AND_STORE_OPERATION_NAME, internalLogger) {
            try {
                val datadogContext = if (sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME) != null) {
                    (sdkCore as? InternalSdkCore)?.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))
                } else {
                    null
                }
                val supportsObfuscation = datadogContext?.source == "android"
                val result = datadogContext?.let {
                    assignmentsReader.readSignedPrecomputedFlags(
                        context,
                        it,
                        diagnostics,
                        request.assignmentAuthorization?.token,
                        generation
                    ) {
                        flagsRepository.waitForPersistenceLoad()
                        @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts a nullable snapshot.
                        cachedContext.set(
                            if (flagsRepository.hasConfiguration()) flagsRepository.getEvaluationContext() else null
                        )
                    }
                }
                val response = result as? SignedAssignmentsResponse
                val assignments = response?.let { mapAssignments(it.json, request, supportsObfuscation) }
                val authorizationRejected = result === PrecomputedAssignmentsResult.AuthorizationRejected
                val eligible = assignments != null && sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME) != null
                val signingRejected = result as? PrecomputedAssignmentsResult.SigningRejected
                // The signing state already logged the reason; the caller gets a signing error, not a network error.
                val throwable = signingRejected?.let { FlagsResponseSigningException(it.reason) }
                    ?: failure(networkFailed = result == null, authorizationRejected = authorizationRejected)
                var installed = false
                val admitted = signing.admit(generation) {
                    synchronized(initializationTerminalLock) {
                        val outcome = takeCompletion(completion, callback)
                        val current = ifCurrent(request) {
                            val installation = if (eligible && !response.isCancelled()) {
                                flagsRepository.installSignedFlagsAndContext(
                                    context,
                                    assignments.flags,
                                    assignments.obfuscation,
                                    response.proof,
                                    assignments.observeFullEvaluationData
                                )
                            } else {
                                null
                            }
                            if (installation != null) {
                                completeInstallation(request, installation, outcome)
                                installed = true
                            } else {
                                flagsRepository.restoreVerifiedFlags()?.let { effects.enqueue { it.complete {} } }
                                applyRequestFailure(
                                    request,
                                    outcome,
                                    cachedContext.get(),
                                    authorizationRejected,
                                    throwable
                                )
                            }
                        }
                        if (!current) enqueueCompletion(outcome, FlagsContextSupersededException(SUPERSEDED_MESSAGE))
                    }
                }
                if (!admitted) {
                    // A newer context superseded this request; a lifecycle change keeps the ordinary failure.
                    val failure = if (signing.isLatest(generation)) {
                        throwable
                    } else {
                        FlagsContextSupersededException(SUPERSEDED_MESSAGE)
                    }
                    synchronized(initializationTerminalLock) {
                        enqueueCompletion(takeCompletion(completion, callback), failure)
                    }
                }
                effects.drain()
                if (installed) {
                    response?.proof?.let { scheduleProofExpiry(signing, it) }
                    response?.flagSelectionIgnoredReason?.let { internalLogger.logIgnoredFlagSelection(it) }
                } else if (admitted && signingRejected == null) {
                    val message = failureMessage(result == null, authorizationRejected)
                    internalLogger.log(InternalLogger.Level.WARN, InternalLogger.Target.USER, { message })
                }
            } finally {
                onFetchFinished()
            }
        }
    }

    // Required mode moves Ready to Stale, or to Error with expireVerifiedFlags, when the installed proof expires.
    private fun scheduleProofExpiry(signing: ResponseSigningState, proof: VerifiedResponse) {
        if (signing.configuration.mode != ResponseSigningMode.REQUIRED) return
        val token = Any()
        val previous = synchronized(initializationTerminalLock) {
            proofExpiryToken = token
            proofExpiresAt = proof.expiresAt
            cancelProofExpiry.also { cancelProofExpiry = null }
        }
        previous?.invoke()
        val delayMs = (proof.expiresAt - signing.now()).coerceAtLeast(0) * MILLIS_PER_SECOND
        val cancel = initializationTimeoutScheduler.schedule(delayMs) { onProofExpired(signing, token) }
        val superseded = synchronized(initializationTerminalLock) {
            if (proofExpiryToken === token) cancelProofExpiry = cancel
            proofExpiryToken !== token
        }
        if (superseded) cancel()
    }

    /** Read-time expiry check; the timer at expires-at does the same when no read happens. */
    fun checkProofExpiry() {
        val signing = signing ?: return
        val token = synchronized(initializationTerminalLock) {
            proofExpiryToken?.takeIf { signing.now() >= proofExpiresAt }
        } ?: return
        onProofExpired(signing, token)
    }

    // Ready goes Stale by default; with expireVerifiedFlags, Ready and Stale go to Error.
    private fun onProofExpired(signing: ResponseSigningState, token: Any) {
        val expireFlags = signing.configuration.expireVerifiedFlags
        val cancelTimer = synchronized(initializationTerminalLock) {
            val applies = currentState == FlagsClientState.Ready ||
                (expireFlags && currentState == FlagsClientState.Stale)
            if (proofExpiryToken !== token || !applies) return
            proofExpiryToken = null
            proofExpiresAt = Long.MAX_VALUE
            val cancel = cancelProofExpiry
            cancelProofExpiry = null
            enqueueState(
                if (expireFlags) {
                    FlagsClientState.Error(VerifiedFlagsExpiredException(VERIFIED_FLAGS_EXPIRED_MESSAGE))
                } else {
                    FlagsClientState.Stale
                }
            )
            cancel
        }
        cancelTimer?.invoke()
        effects.drain()
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { if (expireFlags) VERIFIED_FLAGS_EXPIRED_MESSAGE else VERIFIED_FLAGS_STALE_MESSAGE },
            onlyOnce = true
        )
    }

    private fun failureMessage(networkFailed: Boolean, authorizationRejected: Boolean): String = when {
        authorizationRejected -> AssignmentAuthorization.AUTHORIZATION_FAILED
        networkFailed -> NETWORK_REQUEST_FAILED_MESSAGE
        else -> INVALID_RESPONSE_MESSAGE
    }

    private fun failure(networkFailed: Boolean, authorizationRejected: Boolean): Throwable =
        if (authorizationRejected) {
            FlagsAssignmentAuthorizationException()
        } else {
            NetworkRequestFailedException(failureMessage(networkFailed, authorizationRejected = false))
        }

    // Executor construction does not run the task. Rejection is rethrown only
    // to executeSafe, which logs it without exposing it to the caller.
    @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException")
    private fun completionAwareExecutor(onFetchFinished: () -> Unit): Executor = Executor { task ->
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // Rejection releases request state before executeSafe logs it.
            executorService.execute(task)
        } catch (exception: RejectedExecutionException) {
            onFetchFinished()
            throw exception
        }
    }

    private fun handleRequestFailure(
        request: ExperimentationOptOut.Request,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?,
        cachedContext: EvaluationContext?,
        networkFailed: Boolean,
        authorizationRejected: Boolean
    ) {
        val message = failureMessage(networkFailed, authorizationRejected)
        internalLogger.log(InternalLogger.Level.WARN, InternalLogger.Target.USER, { message })
        val throwable = failure(networkFailed, authorizationRejected)
        synchronized(initializationTerminalLock) {
            val result = takeCompletion(initializationCompletion, callback)
            val current = ifCurrent(request) {
                applyRequestFailure(request, result, cachedContext, authorizationRejected, throwable)
            }
            if (!current) {
                enqueueCompletion(result, FlagsContextSupersededException(SUPERSEDED_MESSAGE))
            }
        }
        effects.drain()
    }

    // Callers hold initializationTerminalLock and checked that the request is current.
    private fun applyRequestFailure(
        request: ExperimentationOptOut.Request,
        result: InitializationResult?,
        cachedContext: EvaluationContext?,
        authorizationRejected: Boolean,
        throwable: Throwable
    ) {
        val timeoutFallback = acceptedTimeoutFallback?.takeIf { it.request === request }
        if (timeoutFallback != null) acceptedTimeoutFallback = null
        if (authorizationRejected) {
            assignmentAuthorization.onAuthorizationRejected(request.assignmentAuthorization)
        }
        val installedContext = fallbackContext(request, cachedContext)
        val fallback = assignmentAuthorization.fallback(
            request.assignmentAuthorization,
            installedContext,
            experimentationOptOut.enabled
        )
        if (fallback.usable) {
            if (fallback.authorizationProtected) {
                assignmentAuthorization.onAssignmentsAccepted(request.assignmentAuthorization)
            }
        }
        // The timeout already published Stale for this request. A late failure cannot make that accepted
        // cache ineligible, but a request that had no timeout fallback still uses the terminal result.
        if (timeoutFallback == null) {
            if (fallback.usable) {
                enqueueState(FlagsClientState.Stale)
            } else {
                enqueueState(FlagsClientState.Error(throwable))
            }
        }
        enqueueCompletion(result, throwable)
    }

    private fun installFlags(
        request: ExperimentationOptOut.Request,
        assignments: PrecomputedAssignments,
        flagSelectionIgnoredReason: String?,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?
    ) {
        val context = request.context
        val installed = synchronized(initializationTerminalLock) {
            val isCurrent = ifCurrent(request) {
                installCurrentFlags(request, context, assignments, initializationCompletion, callback)
            }
            if (!isCurrent) {
                enqueueCompletion(
                    takeCompletion(initializationCompletion, callback),
                    FlagsContextSupersededException(SUPERSEDED_MESSAGE)
                )
            }
            isCurrent
        }
        effects.drain()
        // A superseded response is not installed, so its ignored reason would describe flags that never loaded.
        if (installed) {
            flagSelectionIgnoredReason?.let { internalLogger.logIgnoredFlagSelection(it) }
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Successfully processed context ${context.targetingKey} with ${assignments.flags.size} flags" }
            )
        }
    }

    // Callers hold initializationTerminalLock; the Ready notification and callback run later from effects.drain().
    private fun installCurrentFlags(
        request: ExperimentationOptOut.Request,
        context: EvaluationContext,
        assignments: PrecomputedAssignments,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?
    ) {
        val installation = flagsRepository.installFlagsAndContext(
            context,
            assignments.flags,
            assignments.obfuscation,
            assignments.observeFullEvaluationData
        )
        completeInstallation(request, installation, takeCompletion(initializationCompletion, callback))
    }

    // Callers hold initializationTerminalLock and checked that the request is current.
    private fun completeInstallation(
        request: ExperimentationOptOut.Request,
        installation: FlagsInstallation,
        result: InitializationResult?
    ) {
        assignmentAuthorization.onAssignmentsAccepted(request.assignmentAuthorization)
        if (acceptedTimeoutFallback?.request === request) acceptedTimeoutFallback = null
        currentState = FlagsClientState.Ready
        effects.enqueue {
            installation.complete {
                effects.notifySafely { flagStateManager.updateState(FlagsClientState.Ready) }
                complete(result, FlagsClientState.Ready)
            }
        }
    }

    // Timeout completion atomically reconciles request, state, and fallback policy.
    @Suppress("CyclomaticComplexMethod")
    private fun startInitializationTimeout(
        request: ExperimentationOptOut.Request,
        callback: EvaluationContextCallback?,
        cachedContext: AtomicReference<EvaluationContext?>,
        isCurrent: () -> Boolean = { true },
        beforeScheduling: () -> Unit
    ): InitializationCompletion? {
        val timeoutMs = initializationTimeoutMs
        if (!didStartInitialization.compareAndSet(false, true) || timeoutMs == null) return null

        beforeScheduling()
        return InitializationCompletion(callback).also { completion ->
            val cancelTimeout = initializationTimeoutScheduler.schedule(timeoutMs) {
                val error = FlagsInitializationTimeoutException(timeoutMs)
                synchronized(initializationTerminalLock) {
                    val claimedResult = completion.take(shouldCancelTimeout = false)
                        ?: return@synchronized null
                    val target = if (
                        experimentationOptOut.enabled || assignmentAuthorization.isStaleResponseProtectionEnabled()
                    ) {
                        experimentationOptOut.currentValidRequest()
                    } else {
                        request
                    }
                    val newerFailed = target !== request && currentState is FlagsClientState.Error
                    if (isCurrent() && target != null && !newerFailed) {
                        if (currentState != FlagsClientState.Ready && currentState != FlagsClientState.Stale) {
                            val installedContext = fallbackContext(
                                target,
                                cachedContext.get().takeIf { target === request }
                            )
                            val fallback = assignmentAuthorization.fallback(
                                target.assignmentAuthorization,
                                installedContext,
                                experimentationOptOut.enabled
                            )
                            if (fallback.usable) {
                                acceptedTimeoutFallback = AcceptedTimeoutFallback(
                                    target,
                                    fallback.authorizationProtected
                                )
                            }
                            if (fallback.authorizationProtected) {
                                assignmentAuthorization.onAssignmentsAccepted(target.assignmentAuthorization)
                            }
                            val timeoutState = if (fallback.usable) {
                                FlagsClientState.Stale
                            } else {
                                FlagsClientState.Error(error)
                            }
                            enqueueState(timeoutState)
                        }
                    }
                    enqueueCompletion(claimedResult, error)
                }
                effects.drain()
            }
            completion.armTimeoutCancellation(cancelTimeout)
        }
    }

    private fun fallbackContext(
        request: ExperimentationOptOut.Request,
        capturedContext: EvaluationContext?
    ): EvaluationContext? {
        val installedContext = flagsRepository.getAssignmentsSnapshot()?.context ?: capturedContext
        return when {
            installedContext != null -> installedContext
            flagsRepository.hasConfigurationForContext(request.context) -> request.context
            else -> null
        }
    }

    // Called under the policy lock. State delivery must not acquire the state-holder lock here.
    private fun enqueueState(state: FlagsClientState) {
        currentState = state
        effects.enqueue { flagStateManager.updateState(state) }
    }

    private fun takeCompletion(
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?
    ): InitializationResult? = if (initializationCompletion == null) {
        InitializationResult(callback)
    } else {
        initializationCompletion.take()
    }

    private fun enqueueCompletion(result: InitializationResult?, error: Throwable?) {
        val state = currentState
        effects.enqueue { complete(result, state, error) }
    }

    private fun ifCurrent(request: ExperimentationOptOut.Request, action: () -> Unit): Boolean {
        var authorizationCurrent = false
        val optOutCurrent = experimentationOptOut.ifCurrent(request) {
            authorizationCurrent = assignmentAuthorization.ifCurrent(request.assignmentAuthorization, action)
        }
        return optOutCurrent && authorizationCurrent
    }

    private fun complete(
        result: InitializationResult?,
        state: FlagsClientState,
        error: Throwable? = null
    ) {
        effects.notifySafely { result?.cancelTimeout?.invoke() }
        val callback = result?.callback ?: return
        callbackExecutor.executeSafe("Deliver evaluation context callback", internalLogger) {
            effects.notifySafely {
                when {
                    callback is EvaluationContextOutcomeCallback && error == null -> callback.onSuccess(state)
                    callback is EvaluationContextOutcomeCallback && error != null -> callback.onFailure(error, state)
                    error == null -> callback.onSuccess()
                    else -> callback.onFailure(error)
                }
            }
        }
    }

    private object InlineExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    companion object {
        private const val SUPERSEDED_MESSAGE = "A newer evaluation context replaced this request."
        private const val MILLIS_PER_SECOND = 1000L
        internal const val VERIFIED_FLAGS_STALE_MESSAGE =
            "The proof of the last verified flag assignments expired. The client keeps serving them as stale" +
                " until a new verified response arrives."
        internal const val VERIFIED_FLAGS_EXPIRED_MESSAGE =
            "The proof of the last verified flag assignments expired. Evaluations return defaults" +
                " until a new verified response arrives."
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val INVALID_RESPONSE_MESSAGE = "Unable to read the feature flags response."
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
