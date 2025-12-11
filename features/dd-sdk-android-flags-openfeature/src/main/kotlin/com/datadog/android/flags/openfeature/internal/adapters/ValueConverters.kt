/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.api.InternalLogger
import dev.openfeature.kotlin.sdk.Value
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Converts any value to an OpenFeature Value.
 *
 * Handles structural types (objects, arrays) by delegating to specialized converters,
 * and primitive types through direct type matching.
 *
 * Long values are intelligently converted: within Int range they become Value.Integer,
 * outside that range they become Value.Double to prevent truncation.
 *
 * JSONObject.NULL (a sentinel object) is converted to Value.Null to preserve null semantics.
 *
 * Unexpected types are converted to strings via toString() with a warning logged.
 *
 * @param value The value to convert to an OpenFeature Value
 * @param internalLogger Logger for diagnostic messages (optional, for unexpected type warnings)
 * @return The converted OpenFeature Value
 */
internal fun convertToValue(value: Any?, internalLogger: InternalLogger? = null): Value = when (value) {
    null, JSONObject.NULL -> Value.Null
    is JSONObject -> convertObjectToValue(value, internalLogger)
    is JSONArray -> convertArrayToValue(value, internalLogger)
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
        internalLogger?.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            {
                "Unexpected type ${value.javaClass.name} converted to string via toString(). " +
                    "Expected primitive types or JSON structures."
            }
        )
        Value.String(value.toString())
    }
}

/**
 * Converts a JSONObject to Value.Structure by recursively converting all values.
 *
 * @param jsonObject The JSONObject to convert
 * @param internalLogger Logger for diagnostic messages (optional)
 * @return Value.Structure containing the converted values
 */
internal fun convertObjectToValue(jsonObject: JSONObject, internalLogger: InternalLogger? = null): Value =
    Value.Structure(
        jsonObject.toMap(internalLogger).mapValues { (_, v) -> convertToValue(v, internalLogger) }
    )

/**
 * Converts a JSONArray to Value.List by recursively converting all elements.
 *
 * Elements that fail conversion are logged and skipped.
 *
 * @param jsonArray The JSONArray to convert
 * @param internalLogger Logger for diagnostic messages (optional)
 * @return Value.List containing the converted elements
 */
@Suppress("UnsafeThirdPartyFunctionCall")
internal fun convertArrayToValue(jsonArray: JSONArray, internalLogger: InternalLogger? = null): Value {
    val list = mutableListOf<Value>()
    for (i in 0 until jsonArray.length()) {
        try {
            // Safe: index i is within bounds (0 until jsonArray.length())
            list.add(convertToValue(jsonArray.get(i), internalLogger))
        } catch (e: JSONException) {
            internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "Failed to convert array element at index $i" },
                e
            )
            // Skip individual items that fail to convert
        }
    }
    return Value.List(list)
}

/**
 * Converts an OpenFeature Value to a primitive type suitable for JSONObject.
 *
 * Preserves type information by converting to appropriate JSON primitives:
 * - Value.Integer → Int
 * - Value.Double → Double
 * - Value.Boolean → Boolean
 * - Value.String → String
 * - Value.Null → null
 * - Value.Instant → String (ISO-8601 format)
 * - Value.List → JSONArray (recursive)
 * - Value.Structure → JSONObject (recursive)
 *
 * Note: JSONArray.put() and JSONObject.put() can throw JSONException for non-finite
 * Double values (NaN, Infinity). These are suppressed as they represent valid OpenFeature
 * values that should be converted.
 */
@Suppress("UnsafeThirdPartyFunctionCall")
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun convertValueToJson(value: Value): Any? = when (value) {
    is Value.Null -> null
    is Value.Boolean -> value.asBoolean()
    is Value.Integer -> value.asInteger()
    is Value.Double -> value.asDouble()
    is Value.String -> value.asString()
    is Value.Instant -> value.asInstant()?.toString()
    is Value.List -> {
        val jsonArray = JSONArray()
        value.asList()?.forEach { element ->
            jsonArray.put(convertValueToJson(element))
        }
        jsonArray
    }
    is Value.Structure -> {
        val jsonObject = JSONObject()
        value.asStructure()?.forEach { (key, v) ->
            val jsonValue = convertValueToJson(v)
            if (jsonValue != null) {
                jsonObject.put(key, jsonValue)
            }
        }
        jsonObject
    }
}

/**
 * Converts an OpenFeature [Value] to a Kotlin Map/List structure.
 *
 * Recursively converts:
 * - Value.Structure → Map<String, Any?>
 * - Value.List → List<Any?>
 * - Primitives → native Kotlin types
 * - Value.Null → null
 * - Value.Instant → ISO-8601 string
 *
 * Returns null for incompatible types.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun convertValueToMap(value: Value): Any? = when (value) {
    is Value.Null -> null
    is Value.Boolean -> value.asBoolean()
    is Value.Integer -> value.asInteger()
    is Value.Double -> value.asDouble()
    is Value.String -> value.asString()
    is Value.Instant -> value.asInstant()?.toString()
    is Value.List -> {
        value.asList()?.mapNotNull { element ->
            convertValueToMap(element)
        }
    }
    is Value.Structure -> {
        value.asStructure()?.mapValues { (_, v) ->
            convertValueToMap(v)
        }?.filterValues { it != null }
    }
}
