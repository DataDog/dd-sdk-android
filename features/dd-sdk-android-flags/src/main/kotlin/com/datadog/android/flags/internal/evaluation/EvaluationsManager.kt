/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.internal.FlagsStateManager
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.FlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import java.util.concurrent.ExecutorService

/**
 * Orchestrates evaluations for a given context and stores the results in the repository.
 *
 * This class coordinates between network operations, data transformation, and local storage
 * to provide atomic updates of flag evaluations. All operations are performed asynchronously
 * on a dedicated executor to avoid blocking the calling thread.
 *
 * @param executorService dedicated executor for background operations
 * @param internalLogger logger for debug and error messages
 * @param flagsRepository local storage for flag data and evaluation context
 * @param assignmentsReader handles reading assignments for the context.
 * @param precomputeMapper transforms network responses into internal flag format
 * @param flagStateManager channel for notifying state change listeners
 */
internal class EvaluationsManager(
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    private val flagsRepository: FlagsRepository,
    private val assignmentsReader: PrecomputedAssignmentsReader,
    private val precomputeMapper: PrecomputeMapper,
    private val flagStateManager: FlagsStateManager
) {
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
        flagStateManager.updateState(FlagsClientState.Reconciling)

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
            val response = assignmentsReader.readPrecomputedFlags(context)
            if (response != null) {
                val flagsMap = precomputeMapper.map(response)
                flagsRepository.setFlagsAndContext(context, flagsMap)
                internalLogger.log(
                    InternalLogger.Level.DEBUG,
                    InternalLogger.Target.MAINTAINER,
                    { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
                )

                flagStateManager.updateState(FlagsClientState.Ready)
                callback?.onSuccess()
            } else {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { NETWORK_REQUEST_FAILED_MESSAGE }
                )

                val throwable = Throwable(NETWORK_REQUEST_FAILED_MESSAGE)
                if (hadFlags) {
                    flagStateManager.updateState(FlagsClientState.Stale)
                } else {
                    flagStateManager.updateState(FlagsClientState.Error(throwable))
                }
                callback?.onFailure(throwable)
            }
        }
    }

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
