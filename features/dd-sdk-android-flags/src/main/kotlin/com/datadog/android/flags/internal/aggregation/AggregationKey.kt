/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.UnparsedFlag

/**
 * Aggregation key for evaluation logging.
 *
 * Evaluations are aggregated on:
 * - flag key
 * - variant key (null for DEFAULT/ERROR reasons)
 * - allocation key (null for DEFAULT/ERROR reasons)
 * - targeting key
 * - error code (ErrorCode enum name for aggregation, e.g., "FLAG_NOT_FOUND")
 *
 * Note: Error messages are stored in AggregationStats but not used for aggregation
 * to keep the number of unique keys bounded while preserving debugging information.
 *
 * Context aggregation is reserved for future use and not implemented.
 */
internal data class AggregationKey(
    val flagKey: String,
    val variantKey: String?,
    val allocationKey: String?,
    val targetingKey: String?,
    val errorCode: String?
) {
    companion object {
        /**
         * Creates an aggregation key from an evaluation.
         *
         * @param flagName the flag name
         * @param context the evaluation context
         * @param data the flag data
         * @param errorCode optional error code (ErrorCode enum name for aggregation)
         * @return the aggregation key
         */
        fun fromEvaluation(
            flagName: String,
            context: EvaluationContext,
            data: UnparsedFlag,
            errorCode: String?
        ): AggregationKey {
            // Omit variant/allocation for DEFAULT/ERROR reasons
            // Use string comparison to be forward-compatible with new reason values
            val isDefaultOrError = data.reason == "DEFAULT" || data.reason == "ERROR"

            return AggregationKey(
                flagKey = flagName,
                variantKey = if (isDefaultOrError) null else data.variationKey,
                allocationKey = if (isDefaultOrError) null else data.allocationKey,
                targetingKey = context.targetingKey,
                errorCode = errorCode
            )
        }
    }
}
