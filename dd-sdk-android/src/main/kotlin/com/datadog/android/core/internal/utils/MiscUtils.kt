/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

internal fun retryWithDelay(
    times: Int,
    retryDelayNs: Long,
    block: () -> Boolean
): Boolean {
    return retryWithDelay(block, times, retryDelayNs)
}

@Suppress("TooGenericExceptionCaught")
internal inline fun retryWithDelay(
    block: () -> Boolean,
    times: Int,
    loopsDelayInNanos: Long
): Boolean {
    var retryCounter = 1
    var wasSuccessful = false
    var loopTimeOrigin = System.nanoTime() - loopsDelayInNanos
    while (retryCounter <= times && !wasSuccessful) {
        if ((System.nanoTime() - loopTimeOrigin) >= loopsDelayInNanos) {
            wasSuccessful = try {
                block()
            } catch (e: Exception) {
                sdkLogger.errorWithTelemetry("Internal operation failed", e)
                return false
            }
            loopTimeOrigin = System.nanoTime()
            retryCounter++
        }
    }
    return wasSuccessful
}

internal fun Any?.toJsonElement(): JsonElement {
    return when (this) {
        NULL_MAP_VALUE -> JsonNull.INSTANCE
        null -> JsonNull.INSTANCE
        JsonNull.INSTANCE -> JsonNull.INSTANCE
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Date -> JsonPrimitive(this.time)
        // this line should come before Iterable, otherwise this branch is never executed
        is JsonArray -> this
        is Iterable<*> -> this.toJsonArray()
        is Map<*, *> -> this.toJsonObject()
        is JsonObject -> this
        is JsonPrimitive -> this
        is JSONObject -> this.toJsonObject()
        is JSONArray -> this.toJsonArray()
        else -> JsonPrimitive(toString())
    }
}

internal fun Any?.fromJsonElement(): Any? {
    return when (this) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (this.isBoolean) {
                this.asBoolean
            } else if (this.isNumber) {
                this.asNumber
            } else if (this.isString) {
                this.asString
            } else {
                this
            }
        }
        is JsonObject -> this.asMap()
        else -> this
    }
}

internal fun Iterable<*>.toJsonArray(): JsonElement {
    val array = JsonArray()
    forEach {
        array.add(it.toJsonElement())
    }
    return array
}

internal fun Map<*, *>.toJsonObject(): JsonElement {
    val obj = JsonObject()
    forEach {
        obj.add(it.key.toString(), it.value.toJsonElement())
    }
    return obj
}

internal fun JSONObject.toJsonObject(): JsonElement {
    val obj = JsonObject()
    for (key in keys()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // iteration over keys which exist
        obj.add(key, get(key).toJsonElement())
    }
    return obj
}

internal fun JSONArray.toJsonArray(): JsonElement {
    val obj = JsonArray()
    for (index in 0 until length()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // iteration over indexes which exist
        obj.add(get(index).toJsonElement())
    }
    return obj
}

internal fun JsonObject.asMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    entrySet().forEach {
        map[it.key] = it.value.fromJsonElement()
    }
    return map
}

internal fun JsonElement?.asMap(): Map<String, Any?> {
    return if (this is JsonObject) {
        this.asMap()
    } else {
        emptyMap()
    }
}
