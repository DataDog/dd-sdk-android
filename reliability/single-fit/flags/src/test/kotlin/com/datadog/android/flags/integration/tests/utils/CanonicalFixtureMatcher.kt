/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration.tests.utils

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import java.math.BigDecimal

/**
 * Matching rules of the shared precomputed fixture runner.
 * JSON types are compared strictly: `true` never matches `1`, and `"1"` never matches `1`.
 */
internal object CanonicalFixtureMatcher {

    /**
     * Consumes the events that each applicable matcher of [expectations]`[field]` requires.
     * Returns the events that no matcher consumed.
     */
    fun consume(
        events: List<JsonElement>,
        expectations: JsonObject,
        field: String,
        applies: (JsonObject) -> Boolean
    ): List<JsonElement> {
        val remaining = events.toMutableList()
        expectations.getAsJsonArray(field)?.map { it.asJsonObject }?.filter(applies)?.forEach { matcher ->
            val fields = JsonObject().apply {
                matcher.entrySet().filterNot { it.key.startsWith("_") }.forEach { add(it.key, it.value) }
            }
            repeat(count(matcher)) {
                val index = remaining.indexOfFirst { event -> matches(event, fields) }
                assertThat(index).describedAs("Missing event for $matcher; remaining $remaining")
                    .isGreaterThanOrEqualTo(0)
                remaining.removeAt(index)
            }
        }
        return remaining
    }

    /** Returns `_count`, which must be a positive whole JSON number. Any other value fails the test. */
    fun count(matcher: JsonObject): Int {
        val value = matcher["_count"]
        val number = (value as? JsonPrimitive)?.takeIf { it.isNumber }?.asBigDecimal
        val isPositiveInt = number != null && number.signum() > 0 && number <= MAX_COUNT &&
            (number.scale() <= 0 || number.stripTrailingZeros().scale() <= 0)
        if (!isPositiveInt) fail<Unit>("Invalid _count $value in matcher $matcher: expected a positive integer")
        return checkNotNull(number).toInt()
    }

    /** [expected] fields must be a subset of [actual]; a `null` field matches an absent or null value. */
    fun matches(actual: JsonElement?, expected: JsonElement): Boolean = when {
        expected.isJsonNull -> actual == null || actual.isJsonNull
        expected.isJsonObject -> actual?.isJsonObject == true && expected.asJsonObject.entrySet().all {
            matches(actual.asJsonObject[it.key], it.value)
        }
        else -> jsonEquals(actual, expected)
    }

    // Gson compares numbers through Double, which hides rounding and underflow.
    fun jsonEquals(actual: JsonElement?, expected: JsonElement): Boolean {
        val value = actual ?: JsonNull.INSTANCE
        return when {
            isNumber(value) && isNumber(expected) ->
                decimalForms(value.asJsonPrimitive).any { it.compareTo(expected.asBigDecimal) == 0 }
            isNumber(value) || isNumber(expected) -> false
            value.isJsonPrimitive && expected.isJsonPrimitive ->
                value.asJsonPrimitive.isBoolean == expected.asJsonPrimitive.isBoolean &&
                    value.asJsonPrimitive.isString == expected.asJsonPrimitive.isString &&
                    value == expected
            value.isJsonObject && expected.isJsonObject ->
                value.asJsonObject.keySet() == expected.asJsonObject.keySet() &&
                    expected.asJsonObject.entrySet().all { jsonEquals(value.asJsonObject[it.key], it.value) }
            value.isJsonArray && expected.isJsonArray ->
                value.asJsonArray.size() == expected.asJsonArray.size() &&
                    value.asJsonArray.zip(expected.asJsonArray).all { (a, e) -> jsonEquals(a, e) }
            else -> value == expected
        }
    }

    private fun isNumber(value: JsonElement) = value.isJsonPrimitive && value.asJsonPrimitive.isNumber

    // Fixtures write a double as its exact value or its shortest round-trip form; either names one double.
    private fun decimalForms(primitive: JsonPrimitive): List<BigDecimal> = when (val number = primitive.asNumber) {
        is Double, is Float -> listOf(BigDecimal(number.toDouble()), BigDecimal(number.toDouble().toString()))
        else -> listOf(primitive.asBigDecimal)
    }

    private val MAX_COUNT = BigDecimal(Int.MAX_VALUE)
}
