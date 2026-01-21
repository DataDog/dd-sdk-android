/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Reason codes explaining why a particular flag value was resolved.
 *
 * These values indicate the evaluation path taken to determine the flag value,
 * providing context for debugging and analytics.
 */
enum class ResolutionReason {
    /**
     * No targeting rules matched; the fallthrough/default allocation was used.
     * This is the value configured in the flag's default variation when no rules apply.
     */
    STATIC,

    /**
     * The resolved value is the default value provided in code.
     * Used when the flag is not found or the provider is not ready.
     */
    DEFAULT,

    /**
     * The resolved value matched targeting rules.
     */
    TARGETING_MATCH,

    /**
     * The resolved value matched a specific evaluation rule.
     */
    RULE_MATCH,

    /**
     * A prerequisite flag evaluation failed, causing this flag to use a fallback value.
     */
    PREREQUISITE_FAILED,

    /**
     * An error occurred during flag evaluation.
     */
    ERROR
}
