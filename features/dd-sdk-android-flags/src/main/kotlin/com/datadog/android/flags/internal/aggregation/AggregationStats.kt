/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagEvaluation
import com.datadog.android.flags.model.ResolutionReason

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
 * @param firstTimestamp the timestamp of the first evaluation
 * @param context the evaluation context
 * @param ddContext the Datadog context (service, RUM application/view)
 * @param reason the resolution reason (null for error evaluations, non-null for success evaluations)
 * @param errorMessage optional error message (detailed, for logging)
 */
internal class AggregationStats(
    private val aggregationKey: AggregationKey,
    firstTimestamp: Long,
    private val context: EvaluationContext,
    private val ddContext: DDContext,
    private val reason: String?,
    errorMessage: String?
) {
    // All field access is synchronized - see recordEvaluation() and toEvaluationEvent()
    private var count: Int = 1
    private var firstEvaluation: Long = firstTimestamp
    private var lastEvaluation: Long = firstTimestamp
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
     * Converts the aggregated statistics to a FlagEvaluation.
     *
     * Thread-safe: synchronizes to create consistent snapshot of statistics.
     *
     * @return the flag evaluation event
     */
    fun toEvaluationEvent(): FlagEvaluation {
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
        val eventContext = FlagEvaluation.Context(
            evaluation = null, // Evaluation context reserved for future use
            dd = FlagEvaluation.Dd(
                service = ddContext.service,
                rum = ddContext.applicationId?.let { appId ->
                    FlagEvaluation.Rum(
                        application = FlagEvaluation.Application(id = appId),
                        view = ddContext.viewName?.let { viewName ->
                            FlagEvaluation.View(url = viewName)
                        }
                    )
                }
            )
        )

        return FlagEvaluation(
            timestamp = snapshotFirst,
            flag = FlagEvaluation.Flag(aggregationKey.flagKey),
            variant = aggregationKey.variantKey?.let { FlagEvaluation.Flag(it) },
            allocation = aggregationKey.allocationKey?.let { FlagEvaluation.Flag(it) },
            targetingRule = null, // Not applicable
            targetingKey = aggregationKey.targetingKey,
            context = eventContext,
            error = snapshotMessage?.let { FlagEvaluation.Error(message = it) },
            evaluationCount = snapshotCount.toLong(),
            firstEvaluation = snapshotFirst,
            lastEvaluation = snapshotLast,
            runtimeDefaultUsed = reason == null ||
                reason == ResolutionReason.DEFAULT.name ||
                reason == ResolutionReason.ERROR.name
        )
    }
}
