/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.FlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.model.EvaluationContext
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
    fun updateEvaluationsForContext(context: EvaluationContext) {
        executorService.executeSafe(
            operationName = FETCH_AND_STORE_OPERATION_NAME,
            internalLogger = internalLogger
        ) {
            try {
                internalLogger.log(
                    InternalLogger.Level.DEBUG,
                    InternalLogger.Target.MAINTAINER,
                    { "Processing evaluation context: ${context.targetingKey}" }
                )

                // Make network request to fetch precomputed flags
                val response = flagsNetworkManager.downloadPrecomputedFlags(context)

                if (response != null) {
                    // Parse the response into flags map
                    val flagsMap = precomputeMapper.map(response)

                    // Atomically store context and flags together
                    flagsRepository.setFlagsAndContext(context, flagsMap)

                    internalLogger.log(
                        InternalLogger.Level.DEBUG,
                        InternalLogger.Target.MAINTAINER,
                        { "Successfully processed context ${context.targetingKey} with ${flagsMap.size} flags" }
                    )
                } else {
                    // Network request failed, store context with empty flags
                    flagsRepository.setFlagsAndContext(context, emptyMap())

                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.MAINTAINER,
                        { "Network request failed for context ${context.targetingKey}, stored with empty flags" }
                    )
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Error processing evaluation context: ${context.targetingKey}" },
                    e
                )

                // On error, still store the context with empty flags to maintain state
                try {
                    flagsRepository.setFlagsAndContext(context, emptyMap())
                } catch (@Suppress("TooGenericExceptionCaught") storageException: Exception) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { "Failed to store context after error: ${context.targetingKey}" },
                        storageException
                    )
                }
            }
        }
    }

    companion object {
        private const val FETCH_AND_STORE_OPERATION_NAME = "Fetch and store flags for evaluation context"
    }
}
