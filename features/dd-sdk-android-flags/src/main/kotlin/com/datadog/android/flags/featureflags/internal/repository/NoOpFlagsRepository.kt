/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import org.json.JSONObject

class NoOpFlagsRepository: FlagsRepository {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return defaultValue
    }

    override fun getString(key: String, defaultValue: String): String {
        return defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return defaultValue
    }

    override fun getDouble(key: String, defaultValue: Double): Double {
        return defaultValue
    }

    override fun getJsonObject(key: String, defaultValue: JSONObject): JSONObject {
        return defaultValue
    }
}
