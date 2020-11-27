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
import java.util.Date

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
            wasSuccessful = block()
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
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Date -> JsonPrimitive(this.time)
        is Iterable<*> -> this.toJsonArray()
        is JsonObject -> this
        is JsonArray -> this
        is JsonPrimitive -> this
        else -> JsonPrimitive(toString())
    }
}

internal fun Iterable<*>.toJsonArray(): JsonElement {
    val array = JsonArray()
    forEach {
        array.add(it.toJsonElement())
    }
    return array
}
