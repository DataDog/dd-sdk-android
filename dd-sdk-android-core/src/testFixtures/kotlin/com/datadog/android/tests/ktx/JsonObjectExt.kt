/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.ktx

import com.google.gson.JsonObject

/**
 * A utility method to retrieve a nested attribute using the dotted notation.
 * E.g.: assuming the `this` JsonObject represents the json below, calling
 * `getString("foo.bar.spam")` will return the String `"42"`.
 *
 * {
 *   "foo": {
 *     "bar" : {
 *       "spam": "42"
 *     }
 *   }
 * }
 */
fun JsonObject.getString(path: String): String? {
    return if (has(path)) {
        getAsJsonPrimitive(path)?.asString
    } else if (path.contains('.')) {
        val head = path.substringBefore('.')
        val tail = path.substringAfter('.')
        getAsJsonObject(head)?.getString(tail)
    } else {
        getAsJsonPrimitive(path)?.asString
    }
}
