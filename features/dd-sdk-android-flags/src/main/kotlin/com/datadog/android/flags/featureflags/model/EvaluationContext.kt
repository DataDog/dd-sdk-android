/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.model

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
 * @param attributes Additional attributes used for targeting flag evaluation. These can include
 *   user properties (e.g., email, role, subscription tier), device information (e.g., OS version,
 *   device type), or any other contextual data relevant for flag targeting rules.
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
     * These attributes provide additional context for flag evaluation rules and can include
     * user properties, device information, or any other relevant contextual data.
     */
    val attributes: Map<String, Any?> = emptyMap()
)
