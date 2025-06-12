/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.datadog.android.core.internal.utils.JsonSerializer
import com.datadog.android.internal.utils.NULL_MAP_VALUE
import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.json.JSONArray
import org.json.JSONObject
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
                is JSONArray -> hasField(key, value.toJsonArray())
                is JSONObject -> hasField(key, value.toJsonObject())
                else -> hasField(key, value.toString())
            }
        }
}

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal fun Iterable<*>.toJsonArray(): JsonElement {
    val array = JsonArray()
    forEach {
        array.add(JsonSerializer.toJsonElement(it))
    }
    return array
}

internal fun Map<*, *>.toJsonObject(): JsonElement {
    val obj = JsonObject()
    forEach {
        obj.add(it.key.toString(), JsonSerializer.toJsonElement(it.value))
    }
    return obj
}

internal fun JSONArray.toJsonArray(): JsonElement {
    val obj = JsonArray()
    for (index in 0 until length()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // iteration over indexes which exist
        obj.add(JsonSerializer.toJsonElement(get(index)))
    }
    return obj
}

internal fun JSONObject.toJsonObject(): JsonElement {
    val obj = JsonObject()
    for (key in keys()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // iteration over keys which exist
        obj.add(key, JsonSerializer.toJsonElement(get(key)))
    }
    return obj
}
