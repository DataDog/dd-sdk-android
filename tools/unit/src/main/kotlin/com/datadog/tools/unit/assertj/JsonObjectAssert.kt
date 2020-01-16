/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import java.math.BigInteger

/**
 * Assertion methods for [JsonObject].
 */
@Suppress("StringLiteralDuplication", "MethodOverloading", "TooManyFunctions")
class JsonObjectAssert(actual: JsonObject) :
    AbstractObjectAssert<JsonObjectAssert, JsonObject>(actual, JsonObjectAssert::class.java) {

    /**
     *  Verifies that the actual jsonObject does not contain any field with the given name.
     *  @param name the field name
     */
    fun doesNotHaveField(name: String): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to not have field named $name but found ${actual[name]}"
            )
            .isFalse()

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and `null` value.
     *  @param name the field name
     */
    fun hasNullField(name: String): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonNull)
            .overridingErrorMessage(
                "Expected json object to have field $name with null value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and nullable String value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: String?): JsonObjectAssert {
        if (expectedValue == null) {
            return hasNullField(name)
        } else {
            assertThat(actual.has(name))
                .overridingErrorMessage(
                    "Expected json object to have field named $name but couldn't find one"
                )
                .isTrue()

            val element = actual.get(name)
            assertThat(element is JsonPrimitive && element.isString)
                .overridingErrorMessage(
                    "Expected json object to have field $name with String value " +
                        "but was ${element.javaClass.simpleName}"
                )
                .isTrue()

            val value = (element as JsonPrimitive).asString
            assertThat(value)
                .overridingErrorMessage(
                    "Expected json object to have field $name with value \"%s\" " +
                        "but was \"%s\"",
                    expectedValue, value
                )
                .isEqualTo(expectedValue)
            return this
        }
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and value matching the given pattern.
     *  @param name the field name
     *  @param regex the pattern to match the value
     */
    fun hasStringFieldMatching(name: String, regex: String): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat((element is JsonPrimitive && element.isString))
            .overridingErrorMessage(
                "Expected json object to have field $name with String value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asString
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name with value matching \"%s\" " +
                    "but was \"%s\"",
                regex, value
            )
            .matches(regex)

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and Boolean value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: Boolean): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isBoolean)
            .overridingErrorMessage(
                "Expected json object to have field $name with Boolean value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asBoolean
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name value $expectedValue " +
                    "but was $value"
            )
            .isEqualTo(expectedValue)
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and Int value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: Int): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isNumber)
            .overridingErrorMessage(
                "Expected json object to have field $name with Int value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asInt
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name value $expectedValue " +
                    "but was $value"
            )
            .isEqualTo(expectedValue)
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and Long value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: Long): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isNumber)
            .overridingErrorMessage(
                "Expected json object to have field $name with Long value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asLong
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name value $expectedValue " +
                    "but was $value"
            )
            .isEqualTo(expectedValue)
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and Float value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: Float): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isNumber)
            .overridingErrorMessage(
                "Expected json object to have field $name with Float value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asFloat
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name value $expectedValue " +
                    "but was $value"
            )
            .isEqualTo(expectedValue)
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and Double value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: Double): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isNumber)
            .overridingErrorMessage(
                "Expected json object to have field $name with Double value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asDouble
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name value $expectedValue " +
                    "but was $value"
            )
            .isEqualTo(expectedValue)
        return this
    }

    fun hasField(name: String, expectedValue: BigInteger): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isNumber)
            .overridingErrorMessage(
                "Expected json object to have field $name with BigInteger value " +
                        "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asBigInteger
        assertThat(value)
            .overridingErrorMessage(
                "Expected json object to have field $name value $expectedValue " +
                        "but was $value"
            )
            .isEqualTo(expectedValue)
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and JsonObject value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(
        name: String,
        expectedValue: JsonElement
    ): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        when (expectedValue) {
            is JsonPrimitive -> {
                assertThat(element is JsonPrimitive)
                    .overridingErrorMessage(
                        "Expected json object to have primitive field named $name but was $element"
                    ).isTrue()
            }
            is JsonObject -> {
                assertThat(element is JsonObject)
                    .overridingErrorMessage(
                        "Expected json object to have object field named $name but was $element"
                    ).isTrue()
                expectedValue.keySet().forEach {
                    assertThat(element as JsonObject).hasField(it, expectedValue.get(it))
                }
            }
            is JsonArray -> {
                assertThat(element is JsonArray)
                    .overridingErrorMessage(
                        "Expected json object to have array field named $name but was $element"
                    ).isTrue()
                expectedValue.forEach {
                    assertThat(element as JsonArray).contains(it)
                }
            }
            is JsonNull -> {
                assertThat(element is JsonNull)
                    .overridingErrorMessage(
                        "Expected json object to have null field named $name but was $element"
                    ).isTrue()
            }
        }

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and JsonObject value.
     *  @param name the field name
     *  @param withAssertions a lambda verifying the value of the JsonObject
     */
    fun hasField(
        name: String,
        withAssertions: JsonObjectAssert.() -> Unit
    ): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonObject)
            .overridingErrorMessage(
                "Expected json object to have object field $name " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        JsonObjectAssert(element as JsonObject).withAssertions()

        return this
    }

    companion object {

        /**
         * Create assertion for [JsonObject].
         *
         * @return the created assertion object.
         */
        fun assertThat(actual: JsonObject): JsonObjectAssert =
            JsonObjectAssert(actual)
    }
}
