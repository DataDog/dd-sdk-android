/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.core.internal.utils.toJsonArray
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.util.Date
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions

internal class DatadogJsonElementAssert(actual: JsonElement) :
    AbstractAssert<DatadogJsonElementAssert, JsonElement>(
        actual,
        DatadogJsonElementAssert::class.java
    ) {

    override fun isEqualTo(expected: Any?): DatadogJsonElementAssert {
        when (expected) {
            null -> Assertions.assertThat(actual).isEqualTo(
                JsonNull.INSTANCE
            )
            is Boolean -> Assertions.assertThat(actual.asBoolean).isEqualTo(expected)
            is Int -> Assertions.assertThat(actual.asInt).isEqualTo(expected)
            is Long -> Assertions.assertThat(actual.asLong).isEqualTo(expected)
            is Float -> Assertions.assertThat(actual.asFloat).isEqualTo(expected)
            is Double -> Assertions.assertThat(actual.asDouble).isEqualTo(expected)
            is String -> Assertions.assertThat(actual.asString).isEqualTo(expected)
            is Date -> Assertions.assertThat(actual.asLong).isEqualTo(expected.time)
            is JsonObject ->
                Assertions.assertThat(actual.asJsonObject.toString())
                    .isEqualTo(expected.toString())
            is JsonArray -> Assertions.assertThat(actual.asJsonArray).isEqualTo(expected)
            is Iterable<*> -> Assertions.assertThat(actual.asJsonArray).isEqualTo(
                expected.toJsonArray()
            )
            else -> Assertions.assertThat(actual.asString).isEqualTo(expected.toString())
        }
        return this
    }

    companion object {
        fun assertThat(actual: JsonElement): DatadogJsonElementAssert {
            return DatadogJsonElementAssert(actual)
        }
    }
}
