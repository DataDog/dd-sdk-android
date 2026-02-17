/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses a JSON string and converts it to a Map with no JSON types.
 *
 * The returned Map contains only primitives (String, Int, Long, Double, Boolean),
 * null, nested Maps, and nested Lists. Nested JSONObjects and JSONArrays are
 * recursively converted.
 *
 * @return A map with primitive types, null, nested Maps, and nested Lists
 * @throws JSONException if the string is not valid JSON
 */
internal fun String.toMap(): Map<String, Any?> {
    // Safe: Input validation happens at call site; JSONException is expected to propagate
    @Suppress("UnsafeThirdPartyFunctionCall")
    val jsonObject = JSONObject(this)
    return jsonObject.toMap()
}

/**
 * Recursively converts a [JSONObject] to a [Map] with no JSON types.
 *
 * Nested JSONObjects and JSONArrays are recursively converted to Maps and Lists.
 * The returned Map contains only primitives (String, Int, Long, Double, Boolean),
 * null, nested Maps, and nested Lists.
 *
 * JSONObject.NULL values are converted to null.
 *
 * @return A map with primitive types, null, nested Maps, and nested Lists
 */
internal fun JSONObject.toMap(): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    val keys = this.keys()

    // Safe: Standard iterator pattern - hasNext() checks state, next() is safe after hasNext() returns true
    @Suppress("UnsafeThirdPartyFunctionCall")
    while (keys.hasNext()) {
        @Suppress("UnsafeThirdPartyFunctionCall")
        val key = keys.next()
        // Safe: Key exists because it came from keys() iterator
        @Suppress("UnsafeThirdPartyFunctionCall")
        result[key] = convertJsonValue(this.get(key))
    }

    return result
}

/**
 * Recursively converts a [JSONArray] to a [List] with no JSON types.
 *
 * Nested JSONObjects and JSONArrays are recursively converted to Maps and Lists.
 * The returned List contains only primitives (String, Int, Long, Double, Boolean),
 * null, nested Maps, and nested Lists.
 *
 * JSONObject.NULL values are converted to null.
 *
 * @return A list with primitive types, null, nested Maps, and nested Lists
 */
internal fun JSONArray.toList(): List<Any?> {
    val result = mutableListOf<Any?>()

    // Safe: Iterating within bounds (0 until length)
    @Suppress("UnsafeThirdPartyFunctionCall")
    for (i in 0 until this.length()) {
        // Safe: Index is within bounds (0 until length)
        @Suppress("UnsafeThirdPartyFunctionCall")
        result.add(convertJsonValue(this.get(i)))
    }

    return result
}

/**
 * Converts a Map to a JSONObject, recursively converting nested Maps and Lists.
 *
 * Nested Maps and Lists are recursively converted to JSONObjects and JSONArrays.
 * Null values are converted to JSONObject.NULL.
 *
 * @return A JSONObject with all nested structures converted
 */
internal fun Map<String, Any?>.toJSONObject(): JSONObject {
    val jsonObject = JSONObject()

    forEach { (key, value) ->
        // Safe: convertToJsonValue ensures valid types (primitives, JSONObject.NULL, JSONObject, JSONArray)
        @Suppress("UnsafeThirdPartyFunctionCall")
        jsonObject.put(key, convertToJsonValue(value))
    }

    return jsonObject
}

/**
 * Converts a List to a JSONArray, recursively converting nested Maps and Lists.
 *
 * Nested Maps and Lists are recursively converted to JSONObjects and JSONArrays.
 * Null values are converted to JSONObject.NULL.
 *
 * @return A JSONArray with all nested structures converted
 */
internal fun List<*>.toJSONArray(): JSONArray {
    // Safe: JSONArray constructor does not throw exceptions with empty initialization
    @Suppress("UnsafeThirdPartyFunctionCall")
    val jsonArray = JSONArray()

    forEach { value ->
        // Safe: convertToJsonValue ensures valid types (primitives, JSONObject.NULL, JSONObject, JSONArray)
        @Suppress("UnsafeThirdPartyFunctionCall")
        jsonArray.put(convertToJsonValue(value))
    }

    return jsonArray
}

/**
 * Converts a Kotlin value to a JSON-compatible value.
 *
 * Recursively handles nested structures:
 * - Map<*, *> → JSONObject (non-String keys converted via toString())
 * - List<*> → JSONArray
 * - null → JSONObject.NULL
 * - Primitives → unchanged
 *
 * @param value The Kotlin value to convert
 * @return The JSON-compatible value
 */
private fun convertToJsonValue(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> {
        // Convert keys to String (supports non-String keys via toString())
        val stringMap = value.entries.associate { (k, v) -> k.toString() to v }
        stringMap.toJSONObject()
    }
    is List<*> -> value.toJSONArray()
    else -> value // Primitives: String, Int, Long, Double, Boolean
}

/**
 * Converts a JSON value (from JSONObject.get or JSONArray.get) to a plain Kotlin type.
 *
 * Recursively handles nested structures:
 * - JSONObject → Map<String, Any?>
 * - JSONArray → List<Any?>
 * - Primitives → String, Int, Long, Double, Boolean
 * - JSONObject.NULL → null
 *
 * @param value The value from JSONObject or JSONArray
 * @return The converted value with no JSON types
 */
private fun convertJsonValue(value: Any?): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> value.toMap()
    is JSONArray -> value.toList()
    else -> value // Primitives: String, Int, Long, Double, Boolean, or null
}
