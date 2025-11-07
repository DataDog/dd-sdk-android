/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import org.json.JSONObject

internal data class PrecomputedFlag(
    val variationType: String,
    val variationValue: String,
    val doLog: Boolean,
    val allocationKey: String,
    val variationKey: String,
    val extraLogging: JSONObject,
    val reason: String
)
