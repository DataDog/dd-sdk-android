/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.lint.InternalApi
import com.datadog.android.v2.api.InternalLogger
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

internal fun retryWithDelay(
    times: Int,
    retryDelayNs: Long,
    internalLogger: InternalLogger,
    block: () -> Boolean
): Boolean {
    return retryWithDelay(block, times, retryDelayNs, internalLogger)
}

@Suppress("TooGenericExceptionCaught")
internal inline fun retryWithDelay(
    block: () -> Boolean,
    times: Int,
    loopsDelayInNanos: Long,
    internalLogger: InternalLogger
): Boolean {
    var retryCounter = 1
    var wasSuccessful = false
    var loopTimeOrigin = System.nanoTime() - loopsDelayInNanos
    while (retryCounter <= times && !wasSuccessful) {
        if ((System.nanoTime() - loopTimeOrigin) >= loopsDelayInNanos) {
            wasSuccessful = try {
                block()
            } catch (e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    targets = listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { "Internal I/O operation failed" },
                    e
                )
                return false
            }
            loopTimeOrigin = System.nanoTime()
            retryCounter++
        }
    }
    return wasSuccessful
}

@Suppress("UndocumentedPublicClass")
object JsonSerializer {
    // it could be an extension function, but since the scope is very wide (Any?) in order to avoid
    // polluting user-space, we are going to encapsulate it. Maybe later if we have an internal
    // package it can be converted back to the extension function.

    /**
     * Converts arbitrary object to the [JsonElement] with the best effort. [Any.toString] is
     * used as a fallback.
     */
    @InternalApi
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
            is Map<*, *> -> item.toJsonObject()
            is JsonObject -> item
            is JsonPrimitive -> item
            is JSONObject -> item.toJsonObject()
            is JSONArray -> item.toJsonArray()
            else -> JsonPrimitive(item.toString())
        }
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
        is JsonObject -> this.asDeepMap()
        else -> this
    }
}

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

internal fun JSONObject.toJsonObject(): JsonElement {
    val obj = JsonObject()
    for (key in keys()) {
        @Suppress("UnsafeThirdPartyFunctionCall") // iteration over keys which exist
        obj.add(key, JsonSerializer.toJsonElement(get(key)))
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

internal fun JsonObject.asDeepMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    entrySet().forEach {
        map[it.key] = it.value.fromJsonElement()
    }
    return map
}

internal fun JsonElement?.asDeepMap(): Map<String, Any?> {
    return if (this is JsonObject) {
        this.asDeepMap()
    } else {
        emptyMap()
    }
}
