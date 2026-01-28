/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.BatchedFlagEvaluations.Context1 as EvaluationEventContext

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
 * @param firstTimestamp the timestamp of the first evaluation
 * @param context the evaluation context
 * @param ddContext the Datadog context (service, RUM application/view)
 * @param reason the resolution reason (null for error evaluations, non-null for success evaluations)
 * @param errorMessage optional error message (detailed, for logging)
 */
internal class AggregationStats(
    firstTimestamp: Long,
    private val context: EvaluationContext,
    private val ddContext: DDContext,
    private val reason: String?,
    errorMessage: String?
) {
    @Volatile
    private var count: Int = 1

    @Volatile
    private var firstEvaluation: Long = firstTimestamp

    @Volatile
    private var lastEvaluation: Long = firstTimestamp

    @Volatile
    private var lastErrorMessage: String? = errorMessage

    /**
     * Records an additional evaluation at the given timestamp.
     *
     * Updates the last error message to the most recent one.
     * Updates the first and last evaluation timestamps as necessary.
     * Thread-safe: can be called concurrently from multiple threads.
     *
     * @param timestamp the timestamp of the evaluation
     * @param errorMessage the error message for this evaluation (updates to latest)
     */
    fun recordEvaluation(timestamp: Long, errorMessage: String?) {
        synchronized(this) {
            count++

            if (timestamp < firstEvaluation) {
                firstEvaluation = timestamp
            }
            if (timestamp > lastEvaluation) {
                lastEvaluation = timestamp
            }

            lastErrorMessage = errorMessage
        }
    }

    /**
     * Converts the aggregated statistics to an EvaluationEvent.
     *
     * Thread-safe: synchronizes to create consistent snapshot of statistics.
     *
     * @param flagKey the flag key
     * @param aggregationKey the aggregation key containing variant, allocation, targeting info
     * @return the evaluation event
     */
    fun toEvaluationEvent(flagKey: String, aggregationKey: AggregationKey): BatchedFlagEvaluations.FlagEvaluation {
        // Take atomic snapshot of statistics
        val snapshotCount: Int
        val snapshotFirst: Long
        val snapshotLast: Long
        val snapshotMessage: String?
        synchronized(this) {
            snapshotCount = count
            snapshotFirst = firstEvaluation
            snapshotLast = lastEvaluation
            snapshotMessage = lastErrorMessage
        }

        // Build context with Datadog-specific information
        val eventContext = EvaluationEventContext(
            evaluation = null, // Evaluation context reserved for future use
            dd = BatchedFlagEvaluations.Dd(
                service = ddContext.service,
                rum = ddContext.applicationId?.let { appId ->
                    BatchedFlagEvaluations.Rum(
                        application = BatchedFlagEvaluations.Application(id = appId),
                        view = ddContext.viewName?.let { viewName ->
                            BatchedFlagEvaluations.View(url = viewName)
                        }
                    )
                }
            )
        )

        return BatchedFlagEvaluations.FlagEvaluation(
            timestamp = snapshotFirst,
            flag = BatchedFlagEvaluations.Identifier(flagKey),
            variant = aggregationKey.variantKey?.let { BatchedFlagEvaluations.Identifier(it) },
            allocation = aggregationKey.allocationKey?.let { BatchedFlagEvaluations.Identifier(it) },
            targetingRule = null, // Not applicable
            targetingKey = aggregationKey.targetingKey,
            context = eventContext,
            error = snapshotMessage?.let { BatchedFlagEvaluations.Error(message = it) },
            evaluationCount = snapshotCount.toLong(),
            firstEvaluation = snapshotFirst,
            lastEvaluation = snapshotLast,
            runtimeDefaultUsed = reason == null || reason == "DEFAULT" || reason == "ERROR"
        )
    }
}
