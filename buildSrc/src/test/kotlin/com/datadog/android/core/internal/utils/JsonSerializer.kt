/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

object JsonSerializer {
    val NULL_MAP_VALUE: Object = Object()

    fun toJsonElement(item: Any?): JsonElement {
        return when (item) {
            NULL_MAP_VALUE -> JsonNull.INSTANCE
            null -> JsonNull.INSTANCE
            JsonNull.INSTANCE -> JsonNull.INSTANCE
            is Boolean -> JsonPrimitive(item)
            is Int -> JsonPrimitive(item)
            is Long -> JsonPrimitive(item)
            is Float -> JsonPrimitive(item)
            is Double -> JsonPrimitive(item)
            is String -> JsonPrimitive(item)
            is Date -> JsonPrimitive(item.time)
            // this line should come before Iterable, otherwise this branch is never executed
            is JsonArray -> item
            is Iterable<*> -> item.toJsonArray()
            is Map<*, *> -> item.toJsonElement()
            is JsonObject -> item
            is JsonPrimitive -> item
            is JSONObject -> item.toJsonElement()
            is JSONArray -> item.toJsonArray()
            else -> JsonPrimitive(item.toString())
        }
    }
}
