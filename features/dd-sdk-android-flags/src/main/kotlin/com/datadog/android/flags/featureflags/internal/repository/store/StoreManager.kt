/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.store

import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.tools.annotation.NoOpImplementation
import org.json.JSONObject

@NoOpImplementation
internal interface StoreManager {
    fun getBooleanValue(key: String, defaultValue: Boolean): Boolean
    fun getStringValue(key: String, defaultValue: String): String
    fun getIntValue(key: String, defaultValue: Int): Int
    fun getDoubleValue(key: String, defaultValue: Double): Double
    fun getJsonObjectValue(key: String, defaultValue: JSONObject): JSONObject
    fun updateFlagsState(flags: Map<String, PrecomputedFlag>)
}
