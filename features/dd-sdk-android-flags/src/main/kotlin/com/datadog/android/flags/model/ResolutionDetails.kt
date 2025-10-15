/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Details about the resolution of a feature flag, aligned with the OpenFeature specification.
 *
 * This structure contains the resolved flag value along with metadata about how the value
 * was determined. It provides rich diagnostic information for debugging and analytics.
 *
 * @param T The type of the resolved flag value (Boolean, String, Int, Double, JSONObject).
 * @property value The resolved flag value. This is always present, either from flag evaluation or the default value.
 * @property variant Optional string identifier for the resolved variant (e.g., "control", "treatment").
 * @property reason Optional string explaining why this value was resolved (e.g., "TARGETING_MATCH", "DEFAULT", "ERROR").
 * @property errorCode Optional error code if the resolution failed. Null indicates successful resolution.
 * @property errorMessage Optional human-readable error message providing additional context about failures.
 * @property flagMetadata Optional map of arbitrary metadata associated with the flag (string keys, primitive values).
 */
data class ResolutionDetails<T>(
    val value: T,
    val variant: String? = null,
    val reason: String? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val flagMetadata: Map<String, Any>? = null
)
