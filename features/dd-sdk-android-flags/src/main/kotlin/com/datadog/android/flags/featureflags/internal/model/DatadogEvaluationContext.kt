/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.model

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.model.EvaluationContext

/**
 * Internal representation of evaluation context that contains only normalized
 * attributes that Datadog's targeting system can parse and use in evaluation rules.
 *
 * This class handles the conversion from the flexible public [EvaluationContext]
 * to the strictly typed format required by the API (`Map<String, String>`).
 *
 * Key features:
 * - Validates targeting key is not blank/whitespace-only
 * - Normalizes all attribute values to String type
 * - Filters out unsupported types and null values
 * - Provides validation methods for context integrity
 *
 * @param targetingKey The identifier used for bucketing and targeting. Must remain consistent
 *                     for the same entity to ensure consistent flag evaluation across requests.
 *                     Must not be blank or whitespace-only.
 * @param attributes Map of normalized attributes where all values are [String] representations
 *                   of the original attribute values from the public [EvaluationContext].
 */
internal class DatadogEvaluationContext constructor(
    /** The identifier used for bucketing and targeting in flag evaluation. */
    val targetingKey: String,
    val attributes: Map<String, String>
) {

    /**
     * Validates the Datadog evaluation context.
     * @return true if the context is valid, false otherwise.
     */
    fun isValid(): Boolean = targetingKey.isNotBlank()

    companion object {
        private const val UNSUPPORTED_VALUE_WARNING =
            "Unsupported attribute value type for key '%s': %s. " +
                "Only String, Number, and Boolean values are supported. Attribute skipped."
        private const val NULL_VALUE_WARNING =
            "Null attribute value for key '%s'. Attribute skipped."

        /**
         * Creates a [DatadogEvaluationContext] from a public [EvaluationContext],
         * normalizing all attributes to [String] key-value pairs that the API can handle.
         *
         * This method performs validation and normalization:
         * - Validates that the targeting key is not blank or whitespace-only
         * - Converts all attribute values to [String] representations
         * - Filters out null values and unsupported types (logs warnings)
         * - Returns null for invalid contexts (blank targeting keys)
         *
         * @param context The public evaluation context to convert
         * @param logger Logger for reporting validation failures and normalization issues
         * @return A new DatadogEvaluationContext with normalized attributes, or null if
         *         the targeting key is invalid (blank/whitespace-only)
         */
        fun from(context: EvaluationContext, logger: InternalLogger): DatadogEvaluationContext? {
            val normalizedAttributes = normalizeAttributes(context.attributes, logger)
            val datadogContext = DatadogEvaluationContext(context.targetingKey, normalizedAttributes)

            return if (datadogContext.isValid()) {
                datadogContext
            } else {
                logger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { "Invalid evaluation context: targeting key is blank or whitespace-only" }
                )
                null
            }
        }

        /**
         * Normalizes a map of attributes to String key-value pairs.
         * Logs warnings and skips unsupported types or null values.
         */
        private fun normalizeAttributes(attributes: Map<String, Any?>, logger: InternalLogger): Map<String, String> {
            val normalized = mutableMapOf<String, String>()

            attributes.forEach { (key, value) ->
                val stringValue = normalizeValue(key, value, logger)
                if (stringValue != null) {
                    normalized[key] = stringValue
                }
            }

            return normalized
        }

        /**
         * Normalizes a single attribute value to a String.
         * Returns null for unsupported types or null values, logging appropriate warnings.
         */
        private fun normalizeValue(key: String, value: Any?, logger: InternalLogger): String? = when (value) {
            null -> {
                logger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { NULL_VALUE_WARNING.format(key) }
                )
                null
            }
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> {
                logger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { UNSUPPORTED_VALUE_WARNING.format(key, value::class.simpleName) }
                )
                null
            }
        }
    }
}
