/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import org.json.JSONObject

internal interface FlagsRepository {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getString(key: String, defaultValue: String): String
    fun getInt(key: String, defaultValue: Int): Int
    fun getDouble(key: String, defaultValue: Double): Double
    fun getJsonObject(key: String, defaultValue: JSONObject): JSONObject
}
