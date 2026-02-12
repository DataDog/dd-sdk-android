/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagEvaluation

/**
 * Aggregation statistics for evaluation logging.
 *
 * Tracks aggregated statistics for multiple evaluations of the same flag configuration:
 * - Evaluation count
 * - First/last timestamps
 * - Last error message (updated on each evaluation)
 * - Runtime default usage
 * - Datadog context (service, RUM application/view)
 *
 * Thread Safe
 *
 * @param aggregationKey the aggregation key for this stats bundle.
 * @param firstEvaluation the timestamp of the first evaluation
 * @param context the evaluation context
 * @param service the service name from DatadogContext
 * @param rumApplicationId the RUM application ID (null if RUM not active)
 * @param errorMessage optional error message (detailed, for logging)
 */
internal data class AggregationStats(
    private val aggregationKey: AggregationKey,
    internal val count: Int,
    private val firstEvaluation: Long,
    internal val lastEvaluation: Long,
    private val context: EvaluationContext,
    private val service: String?,
    private val rumApplicationId: String?,
    internal val errorMessage: String?
) {
    /**
     * Converts the aggregated statistics to a FlagEvaluation.
     *
     * Thread-safe: synchronizes to create consistent snapshot of statistics.
     *
     * @return the flag evaluation event
     */
    fun toEvaluationEvent(): FlagEvaluation {
        // Build context with Datadog-specific information
        val eventContext = FlagEvaluation.Context(
            evaluation = null, // Evaluation context reserved for future use
            dd = FlagEvaluation.Dd(
                service = service,
                rum = rumApplicationId?.let { appId ->
                    FlagEvaluation.Rum(
                        application = FlagEvaluation.Application(id = appId),
                        view = aggregationKey.viewName?.let { viewName ->
                            FlagEvaluation.View(url = viewName)
                        }
                    )
                }
            )
        )

        return FlagEvaluation(
            timestamp = firstEvaluation,
            flag = FlagEvaluation.Identifier(aggregationKey.flagKey),
            variant = aggregationKey.variantKey?.let { FlagEvaluation.Identifier(it) },
            allocation = aggregationKey.allocationKey?.let { FlagEvaluation.Identifier(it) },
            targetingRule = null, // Not applicable
            targetingKey = aggregationKey.targetingKey,
            context = eventContext,
            error = errorMessage?.let { FlagEvaluation.Error(message = it) },
            evaluationCount = count.toLong(),
            firstEvaluation = firstEvaluation,
            lastEvaluation = lastEvaluation,
            runtimeDefaultUsed = aggregationKey.variantKey == null
        )
    }
}
