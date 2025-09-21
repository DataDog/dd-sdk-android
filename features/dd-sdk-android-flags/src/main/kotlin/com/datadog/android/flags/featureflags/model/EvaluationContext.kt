/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.model

import com.datadog.android.api.InternalLogger

/**
 * Represents the context used for evaluating feature flags.
 * Contains a targeting key and attributes that are used to determine
 * which flag values should be returned for a given user or session.
 *
 * @param targetingKey The unique identifier used for targeting (e.g., user ID, session ID)
 * @param attributes Additional attributes for targeting (e.g., user properties, device info)
 */
data class EvaluationContext(
    /** The unique identifier used for targeting flag evaluation. */
    val targetingKey: String,
    /** Additional attributes used for targeting flag evaluation. */
    val attributes: Map<String, Any> = emptyMap()
) {

    /**
     * Builder class for creating EvaluationContext instances with validation.
     * Ensures that only supported attribute types are added and validates the targeting key.
     */
    class Builder(private val targetingKey: String, private val internalLogger: InternalLogger) {
        private val attributes = mutableMapOf<String, Any>()

        /**
         * Adds a string attribute to the evaluation context.
         *
         * @param key The attribute key
         * @param value The string value
         * @return This builder instance for method chaining
         */
        fun addAttribute(key: String, value: String): Builder {
            attributes[key] = value
            return this
        }

        /**
         * Adds a numeric attribute to the evaluation context.
         *
         * @param key The attribute key
         * @param value The numeric value
         * @return This builder instance for method chaining
         */
        fun addAttribute(key: String, value: Number): Builder {
            attributes[key] = value
            return this
        }

        /**
         * Adds a boolean attribute to the evaluation context.
         *
         * @param key The attribute key
         * @param value The boolean value
         * @return This builder instance for method chaining
         */
        fun addAttribute(key: String, value: Boolean): Builder {
            attributes[key] = value
            return this
        }

        /**
         * Generic addAttribute method for Any type with validation.
         * Only String, Number, and Boolean values are supported.
         * Unsupported types will be logged as warnings and omitted.
         *
         * @param key The attribute key
         * @param value The attribute value (must be String, Number, or Boolean)
         * @return This builder instance for method chaining
         */
        fun addAttribute(key: String, value: Any): Builder {
            if (isSupportedAttributeType(value)) {
                attributes[key] = value
            } else {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { UNSUPPORTED_ATTRIBUTE_WARNING.format(key, value::class.simpleName) }
                )
            }
            return this
        }

        /**
         * Adds multiple attributes to the evaluation context.
         * Each attribute is validated individually - only String, Number, and Boolean
         * values are supported. Unsupported types will be logged as warnings and omitted.
         *
         * @param contextAttributes Map of attributes to add
         * @return This builder instance for method chaining
         */
        fun addAll(contextAttributes: Map<String, Any>): Builder {
            // filter out invalid types.
            contextAttributes.forEach {
                addAttribute(it.key, it.value)
            }
            return this
        }

        /**
         * Builds the EvaluationContext with the configured targeting key and attributes.
         * Validates that the targeting key is not blank.
         *
         * @return A new EvaluationContext instance
         * @throws IllegalArgumentException if the targeting key is blank
         */
        fun build(): EvaluationContext {
            require(targetingKey.isNotBlank()) { TARGETING_KEY_BLANK_ERROR }
            return EvaluationContext(targetingKey, attributes.toMap())
        }
    }

    /**
     * Companion object containing utility functions and constants for EvaluationContext.
     */
    companion object {
        private const val UNSUPPORTED_ATTRIBUTE_WARNING =
            "Unsupported attribute type for key '%s': %s." +
                "Only String, Number, and Boolean are supported. Attribute omitted."
        private const val TARGETING_KEY_BLANK_ERROR = "Targeting key cannot be blank"

        /**
         * Creates a new Builder instance for constructing an EvaluationContext.
         *
         * @param targetingKey The targeting key for the context
         * @param internalLogger Logger for validation warnings
         * @return A new Builder instance
         */
        fun builder(targetingKey: String, internalLogger: InternalLogger) = Builder(targetingKey, internalLogger)

        internal fun isSupportedAttributeType(value: Any): Boolean =
            value is String || value is Number || value is Boolean
    }
}
