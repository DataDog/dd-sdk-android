/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.core.internal.utils.toJsonArray
import com.datadog.android.core.internal.utils.toJsonObject
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date

fun JsonObjectAssert.containsExtraAttributes(
    attributes: Map<String, Any?>,
    keyNamePrefix: String = ""
) {
    attributes.filter { it.key.isNotBlank() }
        .forEach {
            val value = it.value
            val key = keyNamePrefix + it.key
            when (value) {
                NULL_MAP_VALUE -> hasNullField(key)
                null -> hasNullField(key)
                is Boolean -> hasField(key, value)
                is Int -> hasField(key, value)
                is Long -> hasField(key, value)
                is Float -> hasField(key, value)
                is Double -> hasField(key, value)
                is String -> hasField(key, value)
                is Date -> hasField(key, value.time)
                is JsonObject -> hasField(key, value)
                is JsonArray -> hasField(key, value)
                is Iterable<*> -> hasField(key, value.toJsonArray())
                is Map<*, *> -> hasField(key, value.toJsonObject())
                else -> hasField(key, value.toString())
            }
        }
}
