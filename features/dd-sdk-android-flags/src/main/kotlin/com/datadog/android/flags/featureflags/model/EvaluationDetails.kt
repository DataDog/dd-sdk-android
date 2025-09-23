/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.model

import org.json.JSONObject

/**
 * Represents the evaluation details for a feature flag.
 * Contains the flag value, metadata about the evaluation, and additional context.
 *
 * @param value The evaluated flag value as a string.
 * @param variationKey The key identifying the specific variation returned.
 * @param reason The reason for the evaluation result (e.g., "DEFAULT", "TARGETING_MATCH").
 * @param flagKey The key of the flag that was evaluated.
 * @param metadata Additional metadata about the flag evaluation.
 */
data class EvaluationDetails(
    /** The evaluated flag value as a string. */
    val value: String,
    /** The key identifying the specific variation returned. */
    val variationKey: String,
    /** The reason for the evaluation result. */
    val reason: String,
    /** The key of the flag that was evaluated. */
    val flagKey: String,
    /** Additional metadata about the flag evaluation. */
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Evaluation reason constants aligned with OpenFeature specification.
         */
        object Reason {
            const val DEFAULT = "DEFAULT"
            const val TARGETING_MATCH = "TARGETING_MATCH"
            const val SPLIT = "SPLIT"
            const val DISABLED = "DISABLED"
            const val ERROR = "ERROR"
        }

        /**
         * Creates an EvaluationDetails instance representing a default value response.
         * Used when no flag configuration is available and the default value is returned.
         *
         * @param flagKey The key of the flag that was requested.
         * @param defaultValue The default value being returned.
         * @return EvaluationDetails with DEFAULT reason and the provided default value.
         */
        fun defaultValue(flagKey: String, defaultValue: Any): EvaluationDetails {
            return EvaluationDetails(
                value = defaultValue.toString(),
                variationKey = "default",
                reason = Reason.DEFAULT,
                flagKey = flagKey,
                metadata = mapOf("isDefault" to true)
            )
        }
    }
}