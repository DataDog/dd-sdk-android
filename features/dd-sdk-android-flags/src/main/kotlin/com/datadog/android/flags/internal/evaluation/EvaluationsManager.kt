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
import com.datadog.android.flags.internal.FlagsStateManager
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

internal class FlagsInitializationTimeoutException(timeoutMs: Long) :
    RuntimeException("Flags initialization timed out after ${timeoutMs}ms")

private class InitializationCompletion(private var callback: EvaluationContextCallback?) {
    private val lock = Any()
    private var isPending = true
    private var cancelTimeout: (() -> Unit)? = null

    fun armTimeoutCancellation(cancellation: () -> Unit) {
        val cancelNow = synchronized(lock) {
            if (isPending) {
                cancelTimeout = cancellation
                false
            } else {
                true
            }
        }
        if (cancelNow) cancellation()
    }

    fun take(): EvaluationContextCallback? {
        val outcome = synchronized(lock) {
            if (!isPending) return null
            isPending = false
            val outcome = callback to cancelTimeout
            callback = null
            cancelTimeout = null
            outcome
        }
        outcome.second?.invoke()
        return outcome.first
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
 * @param initializationTimeoutMs maximum duration of the first context operation
 * @param initializationTimeoutScheduler schedules the first context timeout
 */
internal class EvaluationsManager(
    private val sdkCore: FeatureSdkCore,
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    private val flagsRepository: FlagsRepository,
    private val assignmentsReader: PrecomputedAssignmentsReader,
    private val precomputeMapper: PrecomputeMapper,
    private val flagStateManager: FlagsStateManager,
    private val initializationTimeoutMs: Long,
    private val initializationTimeoutScheduler: InitializationTimeoutScheduler
) {
    private val didStartInitialization = AtomicBoolean(false)

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
        val initializationCompletion = if (didStartInitialization.compareAndSet(false, true)) {
            InitializationCompletion(callback).also { completion ->
                val cancelTimeout = initializationTimeoutScheduler.schedule(initializationTimeoutMs) {
                    completion.take()?.onFailure(FlagsInitializationTimeoutException(initializationTimeoutMs))
                }
                completion.armTimeoutCancellation(cancelTimeout)
            }
        } else {
            null
        }
        flagStateManager.updateState(FlagsClientState.Reconciling)

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
                    val response = assignmentsReader.readPrecomputedFlags(context, datadogContext)
                    if (response != null) {
                        val flagsMap = precomputeMapper.map(response)
                        flagsRepository.setFlagsAndContext(context, flagsMap)
                        internalLogger.log(
                            InternalLogger.Level.DEBUG,
                            InternalLogger.Target.MAINTAINER,
                            { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
                        )

                        flagStateManager.updateState(FlagsClientState.Ready)
                        val completionCallback = initializationCompletion?.take()
                            ?: if (initializationCompletion == null) callback else null
                        completionCallback?.onSuccess()
                    } else {
                        internalLogger.log(
                            InternalLogger.Level.WARN,
                            InternalLogger.Target.USER,
                            { NETWORK_REQUEST_FAILED_MESSAGE }
                        )

                        val throwable = NetworkRequestFailedException(NETWORK_REQUEST_FAILED_MESSAGE)
                        // Only use cached flags if they match the requested context to avoid
                        // serving flags from a different user/context.
                        val cachedContextMatches = flagsRepository.getEvaluationContext() == context
                        if (hadFlags && cachedContextMatches) {
                            flagStateManager.updateState(FlagsClientState.Stale)
                        } else {
                            flagStateManager.updateState(FlagsClientState.Error(throwable))
                        }
                        val completionCallback = initializationCompletion?.take()
                            ?: if (initializationCompletion == null) callback else null
                        completionCallback?.onFailure(throwable)
                    }
                }
            }
    }

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
