/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import com.datadog.tools.unit.assertj.JsonElementAssert.Companion.assertThat
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import org.assertj.core.api.AbstractIterableAssert

/**
 * Assertion methods for [JsonArray].
 */
class JsonArrayAssert(actual: JsonArray) :
    AbstractIterableAssert<JsonArrayAssert, JsonArray, JsonElement, JsonElementAssert>(
        actual,
        JsonArrayAssert::class.java
    ) {

    /**
     *  Verifies that the actual jsonArray contains a JsonElement matching all elements of the
     *  expected list.
     *  @param expectedList the expected list
     */
    fun isJsonArrayOf(expectedList: List<*>) {
        expectedList.forEachIndexed { index, value ->
            val element = actual.get(index)
            assertThat(element).isJsonValueOf(value)
        }
    }

    /**
     * This methods is needed to build a new concrete instance of AbstractIterableAssert after a filtering operation is executed.
     *
     *
     * If you create your own subclass of AbstractIterableAssert, simply returns an instance of it in this method.
     *
     * @param iterable the iterable used to build the concrete instance of AbstractIterableAssert
     * @return concrete instance of AbstractIterableAssert
     */
    override fun newAbstractIterableAssert(
        iterable: MutableIterable<JsonElement>?
    ): JsonArrayAssert {
        throw UnsupportedOperationException("Not implemented")
    }

    /**
     * This method is used in navigating assertions like [.first], [.last] and [.element] to build the
     * assertion for the given element navigated to.
     *
     *
     * Typical implementation is returning an [ObjectAssert] but it is possible to return a more specialized assertions
     * should you know what type of elements the iterables contain.
     *
     * @param value the element value
     * @param description describes the element, ex: "check first element" for [.first], used in assertion description.
     * @return the assertion for the given element
     */
    override fun toAssert(value: JsonElement?, description: String?): JsonElementAssert {
        return JsonElementAssert(value ?: JsonNull.INSTANCE)
    }

    companion object {
        /**
         * Create assertion for [JsonArray].
         * @param actual the actual array to assert on
         * @return the created assertion object.
         */
        fun assertThat(actual: JsonArray): JsonArrayAssert = JsonArrayAssert(actual)
    }
}
