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
    private val initializationTerminalLock = Any()
    private var initialCompletion: InitializationCompletion? = null
    private var contextGeneration = 0L
    private var stopped = false

    init {
        flagsRepository.addConfigurationChangeListener(::onConfigurationInstalled)
        onConfigurationInstalled()
    }

    private fun onConfigurationInstalled() {
        val callback = synchronized(initializationTerminalLock) {
            if (stopped) return
            if (clientReadyPolicy != ClientReadyPolicy.CACHE_OR_NETWORK || contextGeneration > 1 ||
                !flagsRepository.hasLoadedConfiguration()
            ) {
                return
            }
            val result = initialCompletion?.take()?.callback
            publishReady()
            result
        }
        callback?.onSuccess()
    }

    private fun publishReady() {
        if (flagStateManager.getCurrentState() != FlagsClientState.Ready) {
            flagStateManager.updateState(FlagsClientState.Ready)
        }
    }

    fun stop() {
        val callback = synchronized(initializationTerminalLock) {
            stopped = true
            ++contextGeneration
            flagsRepository.close()
            val result = initialCompletion?.take()?.callback
            flagStateManager.updateState(FlagsClientState.NotReady)
            result
        }
        callback?.onFailure(IllegalStateException("Flags client stopped"))
    }

    /**
     * Processes a new evaluation context by fetching flags and storing atomically.
     *
     * This method asynchronously fetches precomputed flag evaluations for the given context
     * and atomically updates both the context and flag data in the repository. Network failures
     * retain installed assignments. The first callback follows the configured readiness policy;
     * subsequent callbacks report completion of their network operation.
     *
     * The operation is performed on the configured executor service and will not block the
     * calling thread. Errors are logged but do not propagate to the caller.
     *
     * @param context The evaluation context to process. Must be non-null and contain
     * a valid targeting key.
     * @param callback Optional callback invoked when the context is set and the flags have been fetched successfully or not.
     */
    fun updateEvaluationsForContext(context: EvaluationContext, callback: EvaluationContextCallback? = null) {
        val matchingCachedAssignments = AtomicBoolean(false)
        val (generation, initializationCompletion) = synchronized(initializationTerminalLock) {
            if (stopped) {
                callback?.onFailure(IllegalStateException("Flags client stopped"))
                return
            }
            val generation = ++contextGeneration
            val completion = startInitializationTimeout(context, callback, matchingCachedAssignments, generation) {
                if (clientReadyPolicy != ClientReadyPolicy.CACHE_OR_NETWORK ||
                    !flagsRepository.hasLoadedConfiguration()
                ) {
                    flagStateManager.updateState(FlagsClientState.Reconciling)
                }
            }
            if (completion == null) flagStateManager.updateState(FlagsClientState.Reconciling)
            generation to completion
        }
        onConfigurationInstalled()

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
                    if (response != null) {
                        val flagsMap = precomputeMapper.map(response)
                        internalLogger.log(
                            InternalLogger.Level.DEBUG,
                            InternalLogger.Target.MAINTAINER,
                            { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
                        )

                        completeSuccess(generation, initializationCompletion, callback, context, flagsMap)
                    } else {
                        internalLogger.log(
                            InternalLogger.Level.WARN,
                            InternalLogger.Target.USER,
                            { NETWORK_REQUEST_FAILED_MESSAGE }
                        )

                        completeFailure(generation, initializationCompletion, callback, matchingCachedAssignments.get())
                    }
                }
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
                flagsRepository.setFlagsAndContext(context, flags)
                // A listener may request another context while the configuration is published.
                if (generation == contextGeneration) publishReady()
            }
            result
        }
        completionCallback?.onSuccess()
    }

    private fun completeFailure(
        generation: Long,
        initializationCompletion: InitializationCompletion?,
        callback: EvaluationContextCallback?,
        matchingCachedAssignments: Boolean
    ) {
        val throwable = NetworkRequestFailedException(NETWORK_REQUEST_FAILED_MESSAGE)
        // Initialization can settle using existing assignments; subsequent context failures
        // retain the existing matching-context stale/error behavior.
        val (completionCallback, usableInitialization) = synchronized(initializationTerminalLock) {
            val usableInitialization = initializationCompletion != null &&
                flagsRepository.hasLoadedConfiguration()
            val result = initializationCompletion?.take()?.callback
                ?: if (initializationCompletion == null) callback else null
            if (generation == contextGeneration) {
                val newState = when {
                    usableInitialization -> FlagsClientState.Ready
                    matchingCachedAssignments -> FlagsClientState.Stale
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

    @Suppress("ReturnCount") // First initialization and disabled timeout are separate early exits.
    private fun startInitializationTimeout(
        context: EvaluationContext,
        callback: EvaluationContextCallback?,
        matchingCachedAssignments: AtomicBoolean,
        generation: Long,
        beforeScheduling: () -> Unit
    ): InitializationCompletion? {
        val timeoutMs = initializationTimeoutMs
        if (!didStartInitialization.compareAndSet(false, true)) return null
        val completion = InitializationCompletion(callback)
        synchronized(initializationTerminalLock) { initialCompletion = completion }
        beforeScheduling()
        onConfigurationInstalled()
        if (timeoutMs == null) return completion
        return completion.also {
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
    }

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
