/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Represents the context used for evaluating feature flags.
 *
 * This context contains a targeting key and optional attributes that determine which flag values
 * should be returned for a given user or session. The targeting key ensures consistent flag
 * evaluation across requests for the same entity.
 *
 * @param targetingKey The unique identifier used for targeting and bucketing. This key is critical
 *   for consistent flag evaluation - an inconsistent targeting key will lead to inconsistent
 *   bucketing for that user. Common examples include user ID (consistent treatment per user),
 *   company ID (consistent treatment for entire company), or device ID (consistent treatment
 *   per device). The targeting key may also be used in targeting rules for flag evaluation.
 * @param attributes Additional attributes used for targeting flag evaluation. All values must be
 *   strings - you are responsible for converting numbers, booleans, and other types to their
 *   string representation before passing them to the context. Examples:
 *   `mapOf("email" to "user@example.com", "age" to "25", "premium" to "true")`.
 *   These attributes provide additional context for flag evaluation rules and can include
 *   user properties, device information, or any other relevant contextual data.
 */
data class EvaluationContext(
    /**
     * The unique identifier used for targeting and bucketing flag evaluation.
     *
     * Must be consistent for the same entity to ensure consistent flag behavior across requests.
     * Examples: user ID, company ID, device ID.
     */
    val targetingKey: String,
    /**
     * Additional attributes used for targeting flag evaluation.
     *
     * All values must be strings. You are responsible for converting numbers, booleans,
     * and other types to strings before passing them to the context.
     *
     * Example: `mapOf("email" to "user@example.com", "age" to "25", "premium" to "true")`
     */
    val attributes: Map<String, String> = emptyMap()
) {

    companion object {
        /**
         * An evaluation context with no targeting key and no attributes.
         *
         * Use this when you want to evaluate flags without any targeting or user attributes.
         * This constant provides a standard empty context for such evaluations.
         */
        val EMPTY: EvaluationContext = EvaluationContext(targetingKey = "")
    }
}
