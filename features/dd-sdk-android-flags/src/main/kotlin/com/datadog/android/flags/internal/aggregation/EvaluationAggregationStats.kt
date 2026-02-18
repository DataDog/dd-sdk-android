/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagEvaluation

/**
 * Immutable aggregation statistics for evaluation logging.
 *
 * @param aggregationKey the aggregation key for this stats bundle
 * @param count the number of evaluations aggregated
 * @param firstEvaluation timestamp of the earliest evaluation
 * @param lastEvaluation timestamp of the latest evaluation
 * @param context the evaluation context
 * @param rumApplicationId the RUM application ID (null if RUM not active)
 * @param errorMessage the most recent error message (null if no error)
 */
internal data class EvaluationAggregationStats(
    internal val aggregationKey: EvaluationAggregationKey,
    internal val count: Int,
    internal val firstEvaluation: Long,
    internal val lastEvaluation: Long,
    private val context: EvaluationContext,
    private val rumApplicationId: String?,
    internal val errorMessage: String?
) {
    /**
     * Converts the aggregated statistics to a [FlagEvaluation].
     */
    fun toEvaluationEvent(datadogContext: DatadogContext): FlagEvaluation {
        // Build context with Datadog-specific information
        val eventContext = FlagEvaluation.Context(
            evaluation = null, // Evaluation context reserved for future use
            dd = FlagEvaluation.Dd(
                service = datadogContext.service,
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
