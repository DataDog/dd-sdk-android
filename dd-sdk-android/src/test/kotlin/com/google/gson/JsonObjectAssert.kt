/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.google.gson

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

class JsonObjectAssert(actual: JsonObject) :
    AbstractObjectAssert<JsonObjectAssert, JsonObject>(actual, JsonObjectAssert::class.java) {

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
                    "Expected json object to have field $name value \"$expectedValue\" " +
                        "but was \"$value\""
                )
                .isEqualTo(expectedValue)
            return this
        }
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

    companion object {

        internal fun assertThat(actual: JsonObject): JsonObjectAssert = JsonObjectAssert(actual)
    }
}
