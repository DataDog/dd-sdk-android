/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import com.google.gson.JsonElement
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

/**
 * Assertion methods for [JsonElement].
 */
class JsonElementAssert(actual: JsonElement) :
    AbstractObjectAssert<JsonElementAssert, JsonElement>(actual, JsonElementAssert::class.java) {

    /**
     *  Verifies that the actual jsonElement matches the expected value.
     *  @param expected the expected
     */
    fun isJsonValueOf(expected: Any?): JsonElementAssert {
        when (expected) {
            null -> assertThat(actual.isJsonNull()).isTrue()
            is Boolean -> assertThat(actual.asBoolean).isEqualTo(expected)
            is Int -> assertThat(actual.asInt).isEqualTo(expected)
            is Long -> assertThat(actual.asLong).isEqualTo(expected)
            is Float -> assertThat(actual.asFloat).isEqualTo(expected)
            is Double -> assertThat(actual.asDouble).isEqualTo(expected)
            is String -> assertThat(actual.asString).isEqualTo(expected)
            is Date -> assertThat(actual.asString).isEqualTo(expected)
            is BigInteger -> assertThat(actual.asBigInteger).isEqualTo(expected)
            is BigDecimal -> assertThat(actual.asBigDecimal).isEqualTo(expected)
            is List<*> -> JsonArrayAssert(actual.asJsonArray).isJsonArrayOf(expected)
            else -> {
                error(
                    "Cannot assert on element type ${expected.javaClass}"
                )
            }
        }
        return this
    }

    companion object {

        /**
         * Create assertion for [JsonElement].
         * @param actual the actual element to assert on
         * @return the created assertion object.
         */
        fun assertThat(actual: JsonElement): JsonElementAssert = JsonElementAssert(actual)
    }
}
