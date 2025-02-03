/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.ktx

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * A utility method to retrieve a nested attribute using the dotted notation.
 * E.g.: assuming the `this` JsonObject represents the json below, calling
 * `getString("foo.bar[0].spam")` will return the String `"lorem ipsum"`.
 *
 * {
 *   "foo": {
 *     "bar" : [
 *       {
 *         "spam": "lorem ipsum"
 *       }
 *     ]
 *   }
 * }
 */
fun JsonObject.getString(path: String): String? {
    return getAtPath(path)?.asJsonPrimitive?.asString
}

/**
 * A utility method to retrieve a nested attribute using the dotted notation.
 * E.g.: assuming the `this` JsonObject represents the json below, calling
 * `getString("foo.bar[0].spam")` will return the long `42L`.
 *
 * {
 *   "foo": {
 *     "bar" : [
 *       {
 *         "spam": 42
 *       }
 *     ]
 *   }
 * }
 */
fun JsonObject.getInt(path: String): Int? {
    return getAtPath(path)?.asJsonPrimitive?.asInt
}

/**
 * A utility method to retrieve a nested attribute using the dotted notation.
 * E.g.: assuming the `this` JsonObject represents the json below, calling
 * `getString("foo.bar[0].spam")` will return the long `42L`.
 *
 * {
 *   "foo": {
 *     "bar" : [
 *       {
 *         "spam": 42
 *       }
 *     ]
 *   }
 * }
 */
fun JsonObject.getLong(path: String): Long? {
    return getAtPath(path)?.asJsonPrimitive?.asLong
}

/**
 * A utility method to retrieve a nested attribute using the dotted notation.
 * E.g.: assuming the `this` JsonObject represents the json below, calling
 * `getString("foo.bar[0].spam")` will return the double `3.14`.
 *
 * {
 *   "foo": {
 *     "bar" : [
 *       {
 *         "spam": 3.14
 *       }
 *     ]
 *   }
 * }
 */
@Suppress("UnsafeThirdPartyFunctionCall")
fun JsonObject.getDouble(path: String): Double? {
    return getAtPath(path)?.asJsonPrimitive?.asDouble
}

/**
 * A utility method to retrieve a nested attribute using the dotted notation.
 * E.g.: assuming the `this` JsonObject represents the json below, calling
 * `getString("foo.bar[0].spam")` will return the value `3.14` as [JsonElement].
 *
 * {
 *   "foo": {
 *     "bar" : [
 *       {
 *         "spam": 3.14
 *       }
 *     ]
 *   }
 * }
 */
fun JsonObject.getAtPath(path: String): JsonElement? {
    val matchResult = Regex("""(\w+)\[(\d+)]""").matchEntire(path)
    return if (matchResult != null) {
        val arrayName = matchResult.groupValues[1]
        val index = matchResult.groupValues[2].toInt()
        getAsJsonArray(arrayName).get(index)
    } else if (has(path)) {
        get(path)
    } else if (path.contains('.')) {
        val head = path.substringBefore('.')
        val tail = path.substringAfter('.')
        getAtPath(head)?.asJsonObject?.getAtPath(tail)
    } else {
        null
    }
}
