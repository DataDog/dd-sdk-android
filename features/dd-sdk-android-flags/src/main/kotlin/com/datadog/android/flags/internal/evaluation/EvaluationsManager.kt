/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsInitializationTimeoutException
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.NetworkRequestFailedException
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

internal fun interface InitializationTimeoutScheduler {
    fun schedule(timeoutMs: Long, action: () -> Unit): () -> Unit
}

private data class InitializationResult(
    val callback: EvaluationContextCallback?,
    val cancelTimeout: (() -> Unit)? = null
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
 * @param experimentationOptOut validates contexts and prevents superseded installations
 */
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
    private val experimentationOptOut: ExperimentationOptOut = ExperimentationOptOut()
) {
    private val didStartInitialization = AtomicBoolean(false)
    private val initializationTerminalLock = experimentationOptOut.stateLock
    private val effects = EvaluationEffects(initializationTerminalLock, internalLogger)
    private var currentState: FlagsClientState = FlagsClientState.NotReady

    /**
     * Processes a new evaluation context by fetching flags and storing atomically.
     *
     * This method asynchronously fetches precomputed flag evaluations for the given context
     * and atomically updates both the context and flag data in the repository. A failed or unreadable
     * response keeps the last known good flags and their context.
     *
     * The operation is performed on the configured executor service and will not block the
     * calling thread. Errors are logged but do not propagate to the caller.
     *
     * @param context The evaluation context to process. Must be non-null and contain
     * a valid targeting key.
     * @param callback Optional callback invoked when the context is set and the flags have been fetched successfully or not.
     */
    fun updateEvaluationsForContext(context: EvaluationContext, callback: EvaluationContextCallback? = null) {
        val request = experimentationOptOut.prepare(context)
        if (request.invalid) {
            val error = IllegalArgumentException(ExperimentationOptOut.INVALID_CONTEXT_MESSAGE)
            experimentationOptOut.ifCurrent(request) {
                enqueueState(FlagsClientState.Error(error))
            }
            effects.drain()
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { ExperimentationOptOut.INVALID_CONTEXT_MESSAGE }
            )
            callback?.onFailure(error)
            return
        }
        fetchEvaluations(request, callback)
    }

    private fun fetchEvaluations(request: ExperimentationOptOut.Request, callback: EvaluationContextCallback?) {
        val context = request.context
        val matchingCachedAssignments = AtomicBoolean(false)
        val initializationCompletion = startInitializationTimeout(request, callback, matchingCachedAssignments) {
            experimentationOptOut.ifCurrent(request) { enqueueState(FlagsClientState.Reconciling) }
            effects.drain()
        }
        if (initializationCompletion == null) {
            experimentationOptOut.ifCurrent(request) { enqueueState(FlagsClientState.Reconciling) }
            effects.drain()
        }

        sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)
            ?.withContext(withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)) { datadogContext ->
                executorService.executeSafe(
                    operationName = FETCH_AND_STORE_OPERATION_NAME,
                    internalLogger = internalLogger
                ) {
                    internalLogger.log(
                        InternalLogger.Level.DEBUG,
                        InternalLogger.Target.MAINTAINER,
                        { "Processing evaluation context: ${context.targetingKey}" }
                    )

                    val hadFlags = flagsRepository.hasFlags()
                    matchingCachedAssignments.set(
                        hadFlags && flagsRepository.getEvaluationContext() == context
                    )
                    val response = assignmentsReader.readPrecomputedFlags(context, datadogContext)
                    val flagsMap = response?.let { rawResponse ->
                        precomputeMapper.map(rawResponse, request.takeIf { it.optedOut })
                    }
                    if (flagsMap != null) {
                        installFlags(request, flagsMap, initializationCompletion, callback)
                    } else {
                        handleRequestFailure(
                            request,
                            initializationCompletion,
                            callback,
                            matchingCachedAssignments.get(),
                            response == null
                        )
                    }
                }
            }
    }

    private fun handleRequestFailure(
        request: ExperimentationOptOut.Request,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?,
        matchingCachedAssignments: Boolean,
        networkFailed: Boolean
    ) {
        val message = if (networkFailed) NETWORK_REQUEST_FAILED_MESSAGE else INVALID_RESPONSE_MESSAGE
        internalLogger.log(InternalLogger.Level.WARN, InternalLogger.Target.USER, { message })
        val throwable = NetworkRequestFailedException(message)
        synchronized(initializationTerminalLock) {
            val result = takeCompletion(initializationCompletion, callback)
            experimentationOptOut.ifCurrent(request) {
                if (matchingCachedAssignments) {
                    enqueueState(FlagsClientState.Stale)
                } else {
                    enqueueState(FlagsClientState.Error(throwable))
                }
            }
            enqueueCompletion(result, throwable)
        }
        effects.drain()
    }

    private fun installFlags(
        request: ExperimentationOptOut.Request,
        flagsMap: Map<String, PrecomputedFlag>,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?
    ) {
        val context = request.context
        synchronized(initializationTerminalLock) {
            val installed = experimentationOptOut.ifCurrent(request) {
                val installation = flagsRepository.installFlagsAndContext(context, flagsMap)
                val result = takeCompletion(initializationCompletion, callback)
                currentState = FlagsClientState.Ready
                effects.enqueue {
                    installation.complete {
                        effects.notifySafely { flagStateManager.updateState(FlagsClientState.Ready) }
                        complete(result)
                    }
                }
            }
            if (!installed) {
                enqueueCompletion(
                    takeCompletion(initializationCompletion, callback),
                    IllegalStateException("A newer evaluation context replaced this request.")
                )
            }
        }
        effects.drain()
        internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
        )
    }

    private fun startInitializationTimeout(
        request: ExperimentationOptOut.Request,
        callback: EvaluationContextCallback?,
        matchingCachedAssignments: AtomicBoolean,
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
                    experimentationOptOut.ifCurrent(request) {
                        if (currentState != FlagsClientState.Ready && currentState != FlagsClientState.Stale) {
                            val hasMatchingCachedAssignments = matchingCachedAssignments.get() ||
                                flagsRepository.hasLoadedFlagsForContext(request.context)
                            val timeoutState = if (hasMatchingCachedAssignments) {
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

    private fun enqueueCompletion(result: InitializationResult?, error: Throwable) {
        effects.enqueue { complete(result, error) }
    }

    private fun complete(result: InitializationResult?, error: Throwable? = null) {
        effects.notifySafely { result?.cancelTimeout?.invoke() }
        effects.notifySafely {
            if (error == null) result?.callback?.onSuccess() else result?.callback?.onFailure(error)
        }
    }

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val INVALID_RESPONSE_MESSAGE = "Unable to read the feature flags response."
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
