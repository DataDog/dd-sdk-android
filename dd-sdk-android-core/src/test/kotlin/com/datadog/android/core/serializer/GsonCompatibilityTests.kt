/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.serializer

import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

/**
 * This class is used to test the compatibility of the Gson serializer with our current `.`
 * keys in the JSON payloads. This should make sure that Json properties are added into the
 * `toString` representation in the order they were added in the JSON object.
 */

@Extensions(ExtendWith(ForgeExtension::class))
internal class GsonCompatibilityTests {

    @RepeatedTest(5)
    fun `M add the properties in order in the string W toString()`(forge: Forge) {
        val propertiesKeysValues = forge.aMap(size = forge.anInt(min = 5, max = 50)) {
            val key = forge.aList(size = forge.anInt(min = 1, max = 10)) {
                anAlphabeticalString()
            }.joinToString(".")
            val values = listOf<Any>(
                anInt(),
                aBool(),
                aNumericalString(),
                anHexadecimalString(),
                anAlphabeticalString()
            )
            val value = forge.anElementFrom(values)

            key to value
        }
        val expectedJsonRepresentation = buildString {
            append("{")
            propertiesKeysValues.entries.forEachIndexed { index, entry ->
                when (entry.value) {
                    is Int, is Boolean -> append("\"${entry.key}\":${entry.value}")
                    else -> append("\"${entry.key}\":\"${entry.value}\"")
                }
                if (index < propertiesKeysValues.size - 1) {
                    append(",")
                }
            }
            append("}")
        }
        val jsonObject = JsonObject().apply {
            propertiesKeysValues.forEach { (key, value) ->
                when (value) {
                    is Int -> addProperty(key, value)
                    is Boolean -> addProperty(key, value)
                    is String -> addProperty(key, value)
                }
            }
        }

        // When
        val actualJsonRepresentation = jsonObject.toString()

        // Then
        assertThat(actualJsonRepresentation).isEqualTo(expectedJsonRepresentation)
    }
}
