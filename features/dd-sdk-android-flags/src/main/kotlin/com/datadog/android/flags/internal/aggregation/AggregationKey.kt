/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

/**
 * Aggregation key for evaluation logging.
 *
 * Evaluations are aggregated on:
 * - flag key
 * - variant key (or null when no variant is assigned)
 * - allocation key (or null when no allocation is assigned)
 * - targeting key
 * - RUM view ID (or null when RUM is not active)
 * - error code (ErrorCode enum name for aggregation, e.g., "FLAG_NOT_FOUND")
 *
 * Note: Error messages are stored in AggregationStats but not used for aggregation
 * to keep the number of unique keys bounded while preserving debugging information.
 *
 * Context aggregation is reserved for future use and not implemented.
 */
internal data class AggregationKey(
    val flagKey: String,
    val variantKey: String? = null,
    val allocationKey: String? = null,
    val targetingKey: String?,
    val rumViewId: String? = null,
    val errorCode: String? = null
)
