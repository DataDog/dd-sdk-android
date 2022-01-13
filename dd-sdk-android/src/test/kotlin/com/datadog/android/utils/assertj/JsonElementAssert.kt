/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.core.internal.utils.toJsonArray
import com.datadog.android.core.internal.utils.toJsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber
import java.util.Date
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import org.json.JSONArray
import org.json.JSONObject

internal class JsonElementAssert(actual: JsonElement) :
    AbstractAssert<JsonElementAssert, JsonElement>(
        actual,
        JsonElementAssert::class.java
    ) {

    // region Assert

    override fun isEqualTo(expected: Any?): JsonElementAssert {
        when (expected) {
            null -> Assertions.assertThat(actual).isEqualTo(JsonNull.INSTANCE)
            is Boolean -> Assertions.assertThat(actual.asBoolean).isEqualTo(expected)
            is Int -> Assertions.assertThat(actual.asInt).isEqualTo(expected)
            is Long -> Assertions.assertThat(actual.asLong).isEqualTo(expected)
            is Float -> Assertions.assertThat(actual.asFloat).isEqualTo(expected)
            is Double -> Assertions.assertThat(actual.asDouble).isEqualTo(expected)
            is String -> Assertions.assertThat(actual.asString).isEqualTo(expected)
            is Date -> Assertions.assertThat(actual.asLong).isEqualTo(expected.time)
            is JsonNull -> Assertions.assertThat(actual).isEqualTo(JsonNull.INSTANCE)
            is JsonObject -> Assertions.assertThat(actual.asJsonObject)
                .usingRecursiveComparison()
                .withComparatorForType(jsonPrimitivesComparator, JsonPrimitive::class.java)
                .isEqualTo(expected)
            is JsonArray -> Assertions.assertThat(actual.asJsonArray)
                .usingRecursiveComparison()
                .withComparatorForType(jsonPrimitivesComparator, JsonPrimitive::class.java)
                .isEqualTo(expected)
            is Iterable<*> -> Assertions.assertThat(actual.asJsonArray)
                .usingRecursiveComparison()
                .withComparatorForType(jsonPrimitivesComparator, JsonPrimitive::class.java)
                .isEqualTo(expected.toJsonArray())
            is Map<*, *> -> Assertions.assertThat(actual.asJsonObject)
                .usingRecursiveComparison()
                .withComparatorForType(jsonPrimitivesComparator, JsonPrimitive::class.java)
                .isEqualTo(expected.toJsonObject())
            is JSONArray -> Assertions.assertThat(actual.asJsonArray)
                .usingRecursiveComparison()
                .withComparatorForType(jsonPrimitivesComparator, JsonPrimitive::class.java)
                .isEqualTo(expected.toJsonArray())
            is JSONObject -> Assertions.assertThat(actual.asJsonObject)
                .usingRecursiveComparison()
                .withComparatorForType(jsonPrimitivesComparator, JsonPrimitive::class.java)
                .isEqualTo(expected.toJsonObject())
            else -> Assertions.assertThat(actual.asString).isEqualTo(expected.toString())
        }
        return this
    }

    // endregion

    // region Internal

    private val jsonPrimitivesComparator: (o1: JsonPrimitive, o2: JsonPrimitive) -> Int =
        { o1, o2 ->
            if (comparingFloatAndLazilyParsedNumber(o1, o2)) {
                // when comparing a float with a LazilyParsedNumber the `JsonPrimitive#equals`
                // method uses Double.parseValue(value) to convert the value from the
                // LazilyParsedNumber and this method uses an extra precision. This will
                // create assertion issues because even though the original values
                // are the same the parsed values are no longer matching.
                if (o1.asString.toDouble() == o2.asString.toDouble()) {
                    0
                } else {
                    -1
                }
            } else {
                if (o1.equals(o2)) {
                    0
                } else {
                    -1
                }
            }
        }

    private fun comparingFloatAndLazilyParsedNumber(o1: JsonPrimitive, o2: JsonPrimitive): Boolean {
        return (o1.isNumber && o2.isNumber) &&
            (o1.asNumber is Float || o2.asNumber is Float) &&
            (o1.asNumber is LazilyParsedNumber || o2.asNumber is LazilyParsedNumber)
    }

    // endregion
    companion object {
        fun assertThat(actual: JsonElement): JsonElementAssert {
            return JsonElementAssert(actual)
        }
    }
}
