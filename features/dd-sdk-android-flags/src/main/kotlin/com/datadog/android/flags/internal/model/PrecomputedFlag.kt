/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import com.datadog.android.flags.model.UnparsedFlag
import org.json.JSONObject

data class PrecomputedFlag(
    override val variationType: String,
    override val variationValue: String,
    override val doLog: Boolean,
    override val allocationKey: String,
    override val variationKey: String,
    override val extraLogging: JSONObject,
    override val reason: String
) : UnparsedFlag
