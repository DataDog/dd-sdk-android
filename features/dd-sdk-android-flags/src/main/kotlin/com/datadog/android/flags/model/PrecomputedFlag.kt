/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

import com.datadog.android.lint.InternalApi
import org.json.JSONObject

/**
 * Represents a precomputed flag.
 *
 * This is an internal API and should not be used by public consumers.
 *
 * @param variationType The variation type.
 * @param variationValue The variation value.
 * @param doLog Whether to log.
 * @param allocationKey The allocation key.
 * @param variationKey The variation key.
 * @param extraLogging Extra logging metadata.
 * @param reason The evaluation reason.
 */
@InternalApi
data class PrecomputedFlag(
    val variationType: String,
    val variationValue: String,
    val doLog: Boolean,
    val allocationKey: String,
    val variationKey: String,
    val extraLogging: JSONObject,
    val reason: String
)
