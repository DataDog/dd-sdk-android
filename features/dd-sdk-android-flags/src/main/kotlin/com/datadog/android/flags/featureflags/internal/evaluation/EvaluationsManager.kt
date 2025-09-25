/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.featureflags.internal.model.DatadogEvaluationContext
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.FlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
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
 * @param flagsNetworkManager handles network requests to the Datadog service
 * @param precomputeMapper transforms network responses into internal flag format
 */
internal class EvaluationsManager(
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    private val flagsRepository: FlagsRepository,
    private val flagsNetworkManager: FlagsNetworkManager,
    private val precomputeMapper: PrecomputeMapper
) {

    // region EvaluationsManager

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
     */
    fun updateEvaluationsForContext(context: DatadogEvaluationContext) {
        executorService.executeSafe(
            operationName = FETCH_AND_STORE_OPERATION_NAME,
            internalLogger = internalLogger
        ) {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Processing evaluation context: ${context.targetingKey}" }
            )

            val response = flagsNetworkManager.downloadPrecomputedFlags(context)
            val flagsMap = if (response != null) {
                precomputeMapper.map(response)
            } else {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { "Network request failed for context ${context.targetingKey}, using empty flags" }
                )
                emptyMap()
            }

            flagsRepository.setFlagsAndContext(context, flagsMap)
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
            )
        }
    }

    // endregion

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
    }
}
