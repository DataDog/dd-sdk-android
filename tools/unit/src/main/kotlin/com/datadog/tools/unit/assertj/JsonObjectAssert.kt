/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.math.BigInteger
import java.util.Date
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

/**
 * Assertion methods for [JsonObject].
 */
@Suppress("StringLiteralDuplication", "MethodOverloading", "TooManyFunctions")
class JsonObjectAssert(actual: JsonObject) :
    AbstractObjectAssert<JsonObjectAssert, JsonObject>(actual, JsonObjectAssert::class.java) {

    // region Assertions

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
     *  This assertions is also valid if the named field is absent altogether
     *  @param name the field name
     */
    fun hasNullField(name: String): JsonObjectAssert {
        val element = actual.get(name)
        assertThat(element == null || element is JsonNull)
            .overridingErrorMessage(
                "Expected json object to have field $name with null value " +
                    "but was ${element?.javaClass?.simpleName}"
            )
            .isTrue()

        return this
    }

    /**
     * Verifies that the actual jsonObject contains a field with the given name and nullable value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasNullableField(name: String, expectedValue: Any?): JsonObjectAssert {

        when (expectedValue) {
            null -> hasNullField(name)
            is String -> hasField(name, expectedValue)
            is Boolean -> hasField(name, expectedValue)
            is Int -> hasField(name, expectedValue)
            is Long -> hasField(name, expectedValue)
            is Float -> hasField(name, expectedValue)
            is Double -> hasField(name, expectedValue)
            else -> throw IllegalStateException(
                "Cannot assert on field type ${expectedValue.javaClass}"
            )
        }

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and String value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: String): JsonObjectAssert {
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
                expectedValue,
                value
            )
            .isEqualTo(expectedValue)
        return this
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
                regex,
                value
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
     *  @param offset the ot
     */
    fun hasField(
        name: String,
        expectedValue: Long,
        offset: Offset<Long> = Offset.offset(0L)
    ): JsonObjectAssert {
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
            .isCloseTo(expectedValue, offset)
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

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and BigInteger value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
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
                "Expected json object to have field named %s but couldn't find one",
                name
            )
            .isTrue()

        val element = actual.get(name)
        when (expectedValue) {
            is JsonPrimitive -> {
                assertThat(element is JsonPrimitive)
                    .overridingErrorMessage(
                        "Expected json object to have primitive field named %s but was %s",
                        name,
                        element
                    ).isTrue()
            }
            is JsonObject -> {
                assertThat(element is JsonObject)
                    .overridingErrorMessage(
                        "Expected json object to have object field named %s but was %s",
                        name,
                        element
                    ).isTrue()
                expectedValue.keySet().forEach {
                    assertThat(element as JsonObject).hasField(it, expectedValue.get(it))
                }
            }
            is JsonArray -> {
                assertThat(element is JsonArray)
                    .overridingErrorMessage(
                        "Expected json object to have array field named %s but was %s",
                        name,
                        element
                    ).isTrue()
                expectedValue.forEach {
                    assertThat(element as JsonArray).contains(it)
                }
            }
            is JsonNull -> {
                assertThat(element is JsonNull)
                    .overridingErrorMessage(
                        "Expected json object to have null field named %s but was %s",
                        name,
                        element
                    ).isTrue()
            }
        }

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name and Number value.
     *  @param name the field name
     *  @param expectedValue the expected value of the field
     */
    fun hasField(name: String, expectedValue: Number): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named $name but couldn't find one"
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonPrimitive && element.isNumber)
            .overridingErrorMessage(
                "Expected json object to have field $name with Number value " +
                    "but was ${element.javaClass.simpleName}"
            )
            .isTrue()

        val value = (element as JsonPrimitive).asNumber
        assertThat(value)
            .usingComparator(numberTypeComparator)
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
     *  @param withAssertions a lambda verifying the value of the JsonObject
     */
    fun hasField(
        name: String,
        withAssertions: JsonObjectAssert.() -> Unit
    ): JsonObjectAssert {
        assertThat(actual.has(name))
            .overridingErrorMessage(
                "Expected json object to have field named %s but couldn't find one",
                name
            )
            .isTrue()

        val element = actual.get(name)
        assertThat(element is JsonObject)
            .overridingErrorMessage(
                "Expected json object to have object field %s but was %s",
                name,
                element.javaClass.simpleName
            )
            .isTrue()

        JsonObjectAssert(element as JsonObject).withAssertions()

        return this
    }

    /**
     *  Verifies that the actual jsonObject contains a field with the given name,
     *  and that field contains attributes matching the given map.
     *  @param name the field name
     *  @param expectedValue the expected attributes of the field
     */
    fun hasField(name: String, expectedValue: Map<String, Any>): JsonObjectAssert {
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

        assertThat(element.asJsonObject).containsAttributes(expectedValue)
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains attributes matching the given map.
     *  @param expectedValue the Map to be asserted
     */
    fun containsAttributes(expectedValue: Map<String, Any?>): JsonObjectAssert {
        expectedValue.forEach {
            val value = it.value
            val key = it.key
            when (value) {
                is Boolean -> hasField(key, value)
                is Int -> hasField(key, value)
                is Long -> hasField(key, value)
                is Float -> hasField(key, value)
                is Double -> hasField(key, value)
                is String -> hasField(key, value)
                is Date -> hasField(key, value.time)
                is BigInteger -> hasField(key, value)
            }
        }
        return this
    }

    /**
     *  Verifies that the actual jsonObject contains attributes matching the
     *  predicates in the given map.
     *  @param expectedValue the Map of predicates to be asserted
     */
    @SuppressWarnings("FunctionMaxLength")
    fun containsAttributesMatchingPredicate(expectedValue: Map<String, (JsonElement) -> Boolean>) {
        expectedValue.forEach {
            val predicate = it.value
            val key = it.key
            assertThat(actual.has(key))
                .overridingErrorMessage(
                    "Expected json object to have field named $key " +
                        "but couldn't find one"
                )
                .isTrue()
            assertThat(predicate(actual.get(key))).overridingErrorMessage(
                "Expected json to validate the provided predicate " +
                    "for field name $key but it did not"
            ).isTrue()
        }
    }

    // endregion

    // region Internal

    private val numberTypeComparator = Comparator<Number> { t1, t2 ->
        when (t2) {
            is Long -> t2.compareTo(t1.toLong())
            is Double -> t2.compareTo(t1.toDouble())
            is Float -> t2.compareTo(t1.toFloat())
            else -> (t2 as Int).compareTo(t1.toInt())
        }
    }

    // endregion

    companion object {

        /**
         * Create assertion for [JsonObject].
         * @param actual the actual object to assert on
         * @return the created assertion object.
         */
        fun assertThat(actual: JsonObject): JsonObjectAssert =
            JsonObjectAssert(actual)
    }
}
