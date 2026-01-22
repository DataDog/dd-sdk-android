/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.BatchedFlagEvaluations
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.flags.model.UnparsedFlag

/**
 * Aggregation statistics for evaluation logging.
 *
 * Tracks aggregated statistics for multiple evaluations of the same flag configuration:
 * - Evaluation count
 * - First/last timestamps
 * - Last error message (updated on each evaluation)
 * - Runtime default usage
 *
 * Thread-safe for concurrent recordEvaluation() calls.
 *
 * @param firstTimestamp the timestamp of the first evaluation
 * @param context the evaluation context
 * @param data the flag data
 * @param errorMessage optional error message (detailed, for logging)
 */
internal class AggregationStats(
    firstTimestamp: Long,
    private val context: EvaluationContext,
    private val data: UnparsedFlag,
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
     * Thread-safe: can be called concurrently from multiple threads.
     *
     * @param timestamp the timestamp of the evaluation
     * @param errorMessage the error message for this evaluation (updates to latest)
     */
    fun recordEvaluation(timestamp: Long, errorMessage: String?) {
        synchronized(this) {
            count++
            lastEvaluation = timestamp
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
        @Suppress("SwallowedException") // Intentionally default to ERROR for unknown reasons
        val reason = try {
            ResolutionReason.valueOf(data.reason)
        } catch (e: IllegalArgumentException) {
            // If reason is not a valid ResolutionReason, default to ERROR
            ResolutionReason.ERROR
        }

        val isDefaultOrError = reason == ResolutionReason.DEFAULT || reason == ResolutionReason.ERROR

        // Take atomic snapshot of statistics to prevent torn reads
        val (snapshotCount, snapshotFirst, snapshotLast, snapshotMessage) = synchronized(this) {
            listOf(count, firstEvaluation, lastEvaluation, lastErrorMessage)
        }

        return BatchedFlagEvaluations.FlagEvaluation(
            timestamp = snapshotFirst as Long,
            flag = BatchedFlagEvaluations.Identifier(flagKey),
            variant = aggregationKey.variantKey?.let { BatchedFlagEvaluations.Identifier(it) },
            allocation = aggregationKey.allocationKey?.let { BatchedFlagEvaluations.Identifier(it) },
            targetingRule = null, // Not tracked in current implementation
            targetingKey = aggregationKey.targetingKey,
            context = null, // Context logging reserved for future use
            error = (snapshotMessage as String?)?.let { BatchedFlagEvaluations.Error(message = it) },
            evaluationCount = (snapshotCount as Int).toLong(),
            firstEvaluation = snapshotFirst,
            lastEvaluation = snapshotLast as Long,
            runtimeDefaultUsed = isDefaultOrError
        )
    }
}
