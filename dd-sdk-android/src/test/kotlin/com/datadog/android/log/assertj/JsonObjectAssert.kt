/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import java.math.BigInteger

class JsonObjectAssert(actual: JsonObject) :
    AbstractObjectAssert<JsonObjectAssert, JsonObject>(actual, JsonObjectAssert::class.java) {

    fun doesNotHaveField(name: String): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to not have field named $name but found ${actual[name]}"
            )
            .isFalse()

        return this
    }

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

    fun hasStringField(name: String, nullable: Boolean = true): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(
            (element is JsonPrimitive && element.isString) ||
                (element is JsonNull && nullable)
        )
            .overridingErrorMessage(
                "Expected json object to have field $name with String value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        return this
    }

    fun hasNoField(name: String): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to not have a field named $name " +
                    "but found one ($name:${actual.get(name)})"
            )
            .isFalse()

        return this
    }

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
        internal fun assertThat(actual: JsonObject): JsonObjectAssert =
            JsonObjectAssert(actual)
    }
}
