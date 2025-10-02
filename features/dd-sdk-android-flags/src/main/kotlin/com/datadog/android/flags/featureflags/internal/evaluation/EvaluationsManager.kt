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
 * Orchestrates the flow from FlagsProvider to FlagsRepository.
 * Takes evaluation context, fetches precomputed flags via network,
 * and atomically stores both context and flags in the repository.
 */
internal class EvaluationsManager(
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    private val flagsRepository: FlagsRepository,
    private val flagsNetworkManager: FlagsNetworkManager,
    private val precomputeMapper: PrecomputeMapper
) {
    /**
     * Processes a new evaluation context by fetching flags and storing atomically.
     *
     * @param context The evaluation context to process
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
                // Log warning to both user and maintainer about network failure
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
                    { NETWORK_REQUEST_FAILED_MESSAGE }
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

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
        private const val NETWORK_REQUEST_FAILED_MESSAGE =
            "Unable to fetch feature flags. Please check your network connection."
    }
}
