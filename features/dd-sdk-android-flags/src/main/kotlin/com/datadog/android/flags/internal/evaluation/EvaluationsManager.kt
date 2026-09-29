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
import com.datadog.android.flags.ClientReadyPolicy
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

private data class InitializationResult(val callback: EvaluationContextCallback?)

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
        outcome.second?.invoke()
        return InitializationResult(outcome.first)
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
 * @param clientReadyPolicy when initial loading can complete successfully
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
    private val clientReadyPolicy: ClientReadyPolicy = ClientReadyPolicy.NETWORK
) {
    private val didStartInitialization = AtomicBoolean(false)
    private val initializationTerminalLock = flagStateManager.lifecycleLock
    private var initialCompletion: InitializationCompletion? = null
    private var contextGeneration = 0L
    private var stopped = false
    private var initialNetworkSucceeded = false
    private var initialNetworkFailureGeneration: Long? = null

    init {
        flagsRepository.setOnCacheLoadCompletedListener(::onCacheLoadCompleted)
        onCacheLoadCompleted()
    }

    private fun onCacheLoadCompleted() {
        val (callback, succeeded) = synchronized(initializationTerminalLock) {
            val failureGeneration = initialNetworkFailureGeneration
            if (!canSettleFromDisk(failureGeneration)) return
            // Completion is published after disk installation; sample it before snapshot presence.
            val cachePending = flagsRepository.isCacheLoadPending()
            val usable = flagsRepository.hasLoadedConfiguration()
            val canComplete = usable &&
                (clientReadyPolicy == ClientReadyPolicy.CACHE_OR_NETWORK || failureGeneration != null)
            val exhaustedAfterFailure = failureGeneration != null && !cachePending
            if (!canComplete && !exhaustedAfterFailure) return
            val result = initialCompletion?.take()?.callback
            // An old initial waiter can settle without publishing status for a newer request.
            if (contextGeneration <= 1 && (failureGeneration == null || failureGeneration == contextGeneration)) {
                val state = when {
                    !usable -> FlagsClientState.Error(networkFailure())
                    failureGeneration != null -> FlagsClientState.Stale
                    else -> FlagsClientState.Ready
                }
                publishState(state)
            }
            result to usable
        }
        if (succeeded) callback?.onSuccess() else callback?.onFailure(networkFailure())
    }

    // Called only under the shared lifecycle lock. Obsolete initial waiters may still settle,
    // but their disk callback must not publish status over a newer request.
    private fun canSettleFromDisk(failureGeneration: Long?): Boolean =
        !stopped && !initialNetworkSucceeded && (contextGeneration <= 1 || failureGeneration != null)

    private fun publishState(state: FlagsClientState) {
        if (flagStateManager.getCurrentState() != state) flagStateManager.updateState(state)
    }

    private fun networkFailure() = NetworkRequestFailedException(NETWORK_REQUEST_FAILED_MESSAGE)

    fun stop() {
        val callback = synchronized(initializationTerminalLock) {
            stopped = true
            ++contextGeneration
            flagsRepository.close()
            val result = initialCompletion?.take()?.callback
            flagStateManager.updateState(FlagsClientState.NotReady)
            result
        }
        // Drain admitted operations so their callbacks settle; release the dedicated worker afterward.
        @Suppress("UnsafeThirdPartyFunctionCall") // Android does not use a SecurityManager.
        executorService.shutdown()
        callback?.onFailure(IllegalStateException(CLIENT_STOPPED_MESSAGE))
    }

    /**
     * Processes a new evaluation context by fetching flags and storing atomically.
     *
     * This method asynchronously fetches precomputed flag evaluations for the given context
     * and atomically updates both the context and flag data if the request is still current. Network failures
     * retain installed assignments. The first callback follows the configured readiness policy;
     * subsequent callbacks report completion of their network operation.
     *
     * The operation is performed on the configured executor service and will not block the
     * calling thread. Errors are logged but do not propagate to the caller.
     *
     * @param requestedContext The evaluation context to process. Must be non-null and contain
     * a valid targeting key.
     * @param callback Optional callback invoked when the context is set and the flags have been fetched successfully or not.
     */
    fun updateEvaluationsForContext(requestedContext: EvaluationContext, callback: EvaluationContextCallback? = null) {
        // Own the attributes before asynchronous work or reentrant lifecycle callbacks can run.
        val context = requestedContext.copy(attributes = requestedContext.attributes.toMap())
        val matchingCachedAssignments = AtomicBoolean(false)
        val admission = synchronized(initializationTerminalLock) {
            if (stopped) return@synchronized null
            val generation = ++contextGeneration
            flagsRepository.setRequestedContext(context)
            val completion = if (didStartInitialization.compareAndSet(false, true)) {
                InitializationCompletion(callback).also { initialCompletion = it }
            } else {
                null
            }
            if (completion == null || clientReadyPolicy != ClientReadyPolicy.CACHE_OR_NETWORK ||
                !flagsRepository.hasLoadedConfiguration()
            ) {
                flagStateManager.updateState(FlagsClientState.Reconciling)
            }
            generation to completion
        }
        if (admission == null) {
            callback?.onFailure(IllegalStateException(CLIENT_STOPPED_MESSAGE))
            return
        }
        val (generation, initializationCompletion) = admission
        // Settle an available cache before arming the deadline, outside the admission lock.
        onCacheLoadCompleted()
        if (initializationCompletion != null) {
            scheduleInitializationTimeout(context, matchingCachedAssignments, generation, initializationCompletion)
        }
        fetchEvaluationsForContext(context, callback, matchingCachedAssignments, generation, initializationCompletion)
    }

    private fun fetchEvaluationsForContext(
        context: EvaluationContext,
        callback: EvaluationContextCallback?,
        matchingCachedAssignments: AtomicBoolean,
        generation: Long,
        initializationCompletion: InitializationCompletion?
    ) {
        sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)
            ?.withContext(withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)) { datadogContext ->
                val stoppedCallback = synchronized(initializationTerminalLock) {
                    if (stopped) {
                        initializationCompletion?.take()?.callback
                            ?: if (initializationCompletion == null) callback else null
                    } else {
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
                            if (response != null) {
                                val flagsMap = precomputeMapper.map(response)
                                internalLogger.log(
                                    InternalLogger.Level.DEBUG,
                                    InternalLogger.Target.MAINTAINER,
                                    {
                                        "Successfully processed context ${context.targetingKey} " +
                                            "with ${flagsMap.size} flags"
                                    }
                                )

                                completeSuccess(generation, initializationCompletion, callback, context, flagsMap)
                            } else {
                                internalLogger.log(
                                    InternalLogger.Level.WARN,
                                    InternalLogger.Target.USER,
                                    { NETWORK_REQUEST_FAILED_MESSAGE }
                                )

                                completeFailure(generation, initializationCompletion, callback, context)
                            }
                        }
                        null
                    }
                }
                stoppedCallback?.onFailure(IllegalStateException(CLIENT_STOPPED_MESSAGE))
            }
    }

    private fun completeSuccess(
        generation: Long,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?,
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>
    ) {
        val completionCallback = synchronized(initializationTerminalLock) {
            val result = initializationCompletion?.take()?.callback
                ?: if (initializationCompletion == null) callback else null
            if (generation == contextGeneration) {
                if (initializationCompletion != null) initialNetworkSucceeded = true
                flagsRepository.setFlagsAndContext(context, flags)
                // A cache-first client can already be Ready. Notify again for the accepted network snapshot.
                flagStateManager.updateState(FlagsClientState.Ready)
            }
            result
        }
        completionCallback?.onSuccess()
    }

    private fun completeFailure(
        generation: Long,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?,
        context: EvaluationContext
    ) {
        val throwable = networkFailure()
        // Initialization can settle using existing assignments; subsequent context failures
        // retain the existing matching-context stale/error behavior.
        val (completionCallback, usableInitialization) = synchronized(initializationTerminalLock) {
            val cachePending = flagsRepository.isCacheLoadPending()
            val usableInitialization = initializationCompletion != null && generation == contextGeneration &&
                flagsRepository.hasLoadedConfiguration()
            if (initializationCompletion != null && generation == contextGeneration) {
                initialNetworkFailureGeneration = generation
                if (!usableInitialization && cachePending) return
            }
            val result = initializationCompletion?.take()?.callback
                ?: if (initializationCompletion == null) callback else null
            if (generation == contextGeneration) {
                val newState = when {
                    usableInitialization -> FlagsClientState.Stale
                    flagsRepository.hasLoadedConfigurationForContext(context) -> FlagsClientState.Stale
                    else -> FlagsClientState.Error(throwable)
                }
                if (newState != flagStateManager.getCurrentState()) flagStateManager.updateState(newState)
            }
            result to usableInitialization
        }
        if (usableInitialization) {
            completionCallback?.onSuccess()
        } else {
            completionCallback?.onFailure(throwable)
        }
    }

    private fun scheduleInitializationTimeout(
        context: EvaluationContext,
        matchingCachedAssignments: AtomicBoolean,
        generation: Long,
        completion: InitializationCompletion
    ) {
        val timeoutMs = initializationTimeoutMs ?: return
        val cancelTimeout = initializationTimeoutScheduler.schedule(timeoutMs) {
            val error = FlagsInitializationTimeoutException(timeoutMs)
            val result = synchronized(initializationTerminalLock) {
                val claimedResult = completion.take(shouldCancelTimeout = false)
                    ?: return@synchronized null
                val currentState = flagStateManager.getCurrentState()
                if (generation == contextGeneration &&
                    currentState != FlagsClientState.Ready && currentState != FlagsClientState.Stale
                ) {
                    val hasMatchingCachedAssignments = matchingCachedAssignments.get() ||
                        flagsRepository.hasLoadedFlagsForContext(context)
                    val timeoutState = if (hasMatchingCachedAssignments) {
                        FlagsClientState.Stale
                    } else {
                        FlagsClientState.Error(error)
                    }
                    flagStateManager.updateState(timeoutState)
                }
                claimedResult
            }
            result?.callback?.onFailure(error)
        }
        completion.armTimeoutCancellation(cancelTimeout)
    }

    companion object {
        private const val CLIENT_STOPPED_MESSAGE = "Flags client stopped"
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
