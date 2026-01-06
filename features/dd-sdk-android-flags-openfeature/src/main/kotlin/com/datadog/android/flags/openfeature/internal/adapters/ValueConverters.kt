/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.api.InternalLogger
import dev.openfeature.kotlin.sdk.Value

/**
 * Converts various value types to OpenFeature [Value] types.
 *
 * Supports conversion of:
 * - Primitives ([String], [Boolean], [Int], [Double])
 * - [Map<*, *>] → [Value.Structure]
 * - [List<*>] → [Value.List]
 * - null → [Value.Null]
 *
 * Long values are intelligently converted: within Int range they become [Value.Integer],
 * outside that range they become [Value.Double] to prevent truncation.
 *
 * Unexpected types are converted to strings via [toString()] with a warning logged.
 *
 * @param value The value to convert to an OpenFeature [Value]
 * @param internalLogger Logger for diagnostic messages (optional, for unexpected type warnings)
 * @return The converted OpenFeature [Value]
 */
internal fun convertToValue(value: Any?, internalLogger: InternalLogger): Value = when (value) {
    null -> Value.Null
    is Map<*, *> -> convertMapToValue(value, internalLogger)
    is List<*> -> convertListToValue(value, internalLogger)
    is String -> Value.String(value)
    is Boolean -> Value.Boolean(value)
    is Int -> Value.Integer(value)
    is Long -> when {
        value in Int.MIN_VALUE..Int.MAX_VALUE -> Value.Integer(value.toInt())
        else -> Value.Double(value.toDouble())
    }
    is Short -> Value.Integer(value.toInt())
    is Byte -> Value.Integer(value.toInt())
    is Float -> Value.Double(value.toDouble())
    is Double -> Value.Double(value)
    is Number -> Value.Double(value.toDouble())
    else -> {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            {
                "Unexpected type ${value.javaClass.name} converted to string via toString(). " +
                    "Expected primitive types or Kotlin collections (Map, List)."
            }
        )
        Value.String(value.toString())
    }
}

/**
 * Converts a Kotlin [Map] to [Value.Structure] by recursively converting all values.
 *
 * @param map The Map to convert
 * @param internalLogger Logger for diagnostic messages
 * @return [Value.Structure] containing the converted values
 */
internal fun convertMapToValue(map: Map<*, *>, internalLogger: InternalLogger): Value = Value.Structure(
    map.mapKeys { (k, _) -> k.toString() }
        .mapValues { (_, v) -> convertToValue(v, internalLogger) }
)

/**
 * Converts a Kotlin [List] to [Value.List] by recursively converting all elements.
 *
 * @param list The List to convert
 * @param internalLogger Logger for diagnostic messages
 * @return [Value.List] containing the converted elements
 */
internal fun convertListToValue(list: List<*>, internalLogger: InternalLogger): Value = Value.List(
    list.map { element -> convertToValue(element, internalLogger) }
)
