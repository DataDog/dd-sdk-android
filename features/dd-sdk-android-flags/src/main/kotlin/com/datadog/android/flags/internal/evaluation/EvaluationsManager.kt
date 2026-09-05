/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
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

internal typealias InitializationTimeoutCancellation = () -> Unit

internal fun interface InitializationTimeoutScheduler {
    fun schedule(timeoutMs: Long, action: () -> Unit): InitializationTimeoutCancellation
}

internal fun interface InitializationTimeoutCallbackDispatcher {
    fun dispatch(action: () -> Unit)
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
 * @param initializationTimeoutCallbackDispatcher dispatches a timed-out caller's callback
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
    private val initializationTimeoutCallbackDispatcher: InitializationTimeoutCallbackDispatcher =
        InitializationTimeoutCallbackDispatcher { it() }
) {
    private enum class InitializationTimeoutPhase {
        PENDING,
        CLAIMED,
        PUBLISHED,
        COMPLETED
    }

    private val contextUpdateLock = Any()
    private var didStartInitialization = false
    private var contextUpdateCount = 0L
    private val pendingContextCallbacks = linkedMapOf<Long, EvaluationContextCallback>()
    private var initializationTimeoutGeneration: Long? = null
    private var initializationTimeoutPhase: InitializationTimeoutPhase? = null
    private var initializationTimeoutCancellation: InitializationTimeoutCancellation? = null
    private var deferredInitializationNetworkCompletion: (() -> Unit)? = null

    private data class ContextUpdate(val generation: Long, val ownsInitialization: Boolean)

    /**
     * Processes a new evaluation context by fetching flags and storing atomically.
     *
     * This method asynchronously fetches precomputed flag evaluations for the given context
     * and atomically updates both the context and flag data in the repository. Network failures
     * result in an empty flag set being stored with the context, allowing graceful degradation.
     *
     * The operation is performed on the configured executor service and will not block the
     * calling thread. Errors are logged but do not propagate to the caller.
     *
     * @param context The evaluation context to process. Must be non-null and contain
     * a valid targeting key.
     * @param callback Optional callback invoked when the context is set and the flags have been fetched successfully or not.
     */
    fun updateEvaluationsForContext(context: EvaluationContext, callback: EvaluationContextCallback? = null) {
        val contextUpdate = synchronized(contextUpdateLock) {
            contextUpdateCount += 1
            val generation = contextUpdateCount
            val ownsInitialization = !didStartInitialization
            didStartInitialization = true
            callback?.let { pendingContextCallbacks[generation] = it }
            if (ownsInitialization && initializationTimeoutMs != null) {
                initializationTimeoutGeneration = generation
                initializationTimeoutPhase = InitializationTimeoutPhase.PENDING
            }
            flagStateManager.updateState(FlagsClientState.Reconciling)
            ContextUpdate(generation, ownsInitialization)
        }
        startInitializationTimeout(context, contextUpdate)

        sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)
            ?.withContext(withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)) { datadogContext ->
                executorService.executeSafe(
                    operationName = FETCH_AND_STORE_OPERATION_NAME,
                    internalLogger = internalLogger
                ) {
                    processContext(
                        context,
                        datadogContext,
                        contextUpdate
                    )
                }
            }
    }

    @WorkerThread
    private fun processContext(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        contextUpdate: ContextUpdate
    ) {
        internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { "Processing evaluation context: ${context.targetingKey}" }
        )

        val hadFlags = flagsRepository.hasFlags()
        val response = assignmentsReader.readPrecomputedFlags(context, datadogContext)
        if (response != null) {
            val flagsMap = precomputeMapper.map(response)
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
            )
            finish(
                contextUpdate = contextUpdate.generation,
                publishState = {
                    publishSuccess(
                        context,
                        flagsMap,
                        contextUpdate.generation
                    )
                },
                notify = EvaluationContextCallback::onSuccess
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NETWORK_REQUEST_FAILED_MESSAGE }
            )
            val error = NetworkRequestFailedException(NETWORK_REQUEST_FAILED_MESSAGE)
            val cachedContextMatches = flagsRepository.getEvaluationContext() == context
            finish(
                contextUpdate = contextUpdate.generation,
                publishState = {
                    publishFailure(
                        hadFlags,
                        cachedContextMatches,
                        error,
                        contextUpdate.generation
                    )
                },
                notify = { it.onFailure(error) }
            )
        }
    }

    private fun finish(
        contextUpdate: Long,
        publishState: () -> Unit,
        notify: (EvaluationContextCallback) -> Unit
    ) {
        var timeoutCancellation: InitializationTimeoutCancellation? = null
        var callbacks: List<EvaluationContextCallback> = emptyList()
        var deferred = false
        var publicationFailure: Exception? = null
        synchronized(contextUpdateLock) {
            if (contextUpdateCount != contextUpdate) return@synchronized

            if (initializationTimeoutPhase == InitializationTimeoutPhase.CLAIMED) {
                deferredInitializationNetworkCompletion = {
                    finish(contextUpdate, publishState, notify)
                }
                deferred = true
                return@synchronized
            }

            if (initializationTimeoutPhase == InitializationTimeoutPhase.PENDING) {
                initializationTimeoutPhase = InitializationTimeoutPhase.COMPLETED
                timeoutCancellation = initializationTimeoutCancellation
                initializationTimeoutCancellation = null
            }

            try {
                publishState()
            } catch (@Suppress("TooGenericExceptionCaught") throwable: Exception) {
                publicationFailure = throwable
            } finally {
                callbacks = drainCallbacksThrough(contextUpdate)
            }
        }

        if (deferred) return
        timeoutCancellation?.invoke()
        callbacks.forEach(notify)
        val reportedFailure = publicationFailure
        if (reportedFailure != null) {
            @Suppress("ThrowingInternalException") // propagate the state listener's exception
            throw reportedFailure
        }
    }

    private fun drainCallbacksThrough(contextUpdate: Long): List<EvaluationContextCallback> {
        val completedGenerations = pendingContextCallbacks.keys.filter { it <= contextUpdate }
        return completedGenerations.mapNotNull { generation ->
            @Suppress("UnsafeThirdPartyFunctionCall") // the generation key is non-null
            pendingContextCallbacks.remove(generation)
        }
    }

    private fun startInitializationTimeout(
        context: EvaluationContext,
        contextUpdate: ContextUpdate
    ) {
        val timeoutMs = initializationTimeoutMs
        if (!contextUpdate.ownsInitialization || timeoutMs == null) return

        val cancelTimeout = initializationTimeoutScheduler.schedule(timeoutMs) {
            initializationDidTimeOut(context, contextUpdate.generation, timeoutMs)
        }

        val cancelNow = synchronized(contextUpdateLock) {
            if (initializationTimeoutGeneration == contextUpdate.generation &&
                initializationTimeoutPhase == InitializationTimeoutPhase.PENDING
            ) {
                initializationTimeoutCancellation = cancelTimeout
                false
            } else {
                true
            }
        }
        if (cancelNow) cancelTimeout()
    }

    private fun initializationDidTimeOut(context: EvaluationContext, contextUpdate: Long, timeoutMs: Long) {
        val timeoutCallback = synchronized(contextUpdateLock) {
            if (initializationTimeoutGeneration != contextUpdate ||
                initializationTimeoutPhase != InitializationTimeoutPhase.PENDING
            ) {
                return
            }
            initializationTimeoutPhase = InitializationTimeoutPhase.CLAIMED
            initializationTimeoutCancellation = null
            @Suppress("UnsafeThirdPartyFunctionCall") // the generation key is non-null
            pendingContextCallbacks.remove(contextUpdate)
        }

        initializationTimeoutCallbackDispatcher.dispatch {
            val error = FlagsInitializationTimeoutException(timeoutMs)
            var deferredNetworkCompletion: (() -> Unit)? = null
            var publicationFailure: Exception? = null
            synchronized(contextUpdateLock) {
                if (initializationTimeoutGeneration == contextUpdate &&
                    initializationTimeoutPhase == InitializationTimeoutPhase.CLAIMED
                ) {
                    initializationTimeoutPhase = InitializationTimeoutPhase.PUBLISHED
                    deferredNetworkCompletion = deferredInitializationNetworkCompletion
                    deferredInitializationNetworkCompletion = null
                    try {
                        internalLogger.log(
                            InternalLogger.Level.WARN,
                            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                            { INITIALIZATION_TIMEOUT_MESSAGE },
                            error,
                            onlyOnce = true
                        )
                        publishInitializationTimeout(context, error, contextUpdate)
                    } catch (@Suppress("TooGenericExceptionCaught") throwable: Exception) {
                        publicationFailure = throwable
                    }
                }
            }
            try {
                deferredNetworkCompletion?.invoke()
            } finally {
                timeoutCallback?.onFailure(error)
            }
            val reportedFailure = publicationFailure
            if (reportedFailure != null) {
                @Suppress("ThrowingInternalException") // propagate the state listener's exception
                throw reportedFailure
            }
        }
    }

    private fun publishSuccess(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        contextUpdate: Long
    ) {
        synchronized(contextUpdateLock) {
            if (contextUpdateCount != contextUpdate) return
            flagsRepository.setFlagsAndContext(context, flags)
            flagStateManager.updateState(FlagsClientState.Ready)
        }
    }

    private fun publishFailure(
        hadFlags: Boolean,
        cachedContextMatches: Boolean,
        error: Throwable,
        contextUpdate: Long
    ) {
        synchronized(contextUpdateLock) {
            if (contextUpdateCount != contextUpdate) return
            if (hadFlags && cachedContextMatches) {
                flagStateManager.updateState(FlagsClientState.Stale)
            } else {
                flagStateManager.updateState(FlagsClientState.Error(error))
            }
        }
    }

    private fun publishInitializationTimeout(
        context: EvaluationContext,
        error: Throwable,
        contextUpdate: Long
    ) {
        synchronized(contextUpdateLock) {
            if (contextUpdateCount != contextUpdate) return
            val hasMatchingCache = flagsRepository.hasFlagsForContext(context)
            if (hasMatchingCache) {
                flagStateManager.updateState(FlagsClientState.Stale)
            } else {
                flagsRepository.clear()
                flagStateManager.updateState(FlagsClientState.Error(error))
            }
        }
    }

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
        private const val INITIALIZATION_TIMEOUT_MESSAGE =
            "Flags initialization did not complete before the configured timeout. " +
                "The operation continues and can make the client ready later."
    }
}
