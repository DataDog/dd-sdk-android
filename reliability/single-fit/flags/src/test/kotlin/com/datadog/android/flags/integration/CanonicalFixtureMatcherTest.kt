/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import com.datadog.android.flags.integration.tests.utils.CanonicalFixtureMatcher
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
internal class CanonicalFixtureMatcherTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "true|1",
            "1|true",
            "false|0",
            "\"1\"|1",
            "1|\"1\"",
            "\"true\"|true",
            "null|0",
            "{\"a\":true}|{\"a\":1}",
            "[1]|[true]"
        ]
    )
    fun `M not match different JSON types W jsonEquals()`(actual: String, expected: String) {
        // When
        val result = CanonicalFixtureMatcher.jsonEquals(json(actual), json(expected))

        // Then
        assertThat(result).isFalse()
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = ["true|true", "1|1.0", "\"1\"|\"1\"", "null|null", "{\"a\":[1,true]}|{\"a\":[1.0,true]}"]
    )
    fun `M match equal JSON values W jsonEquals()`(actual: String, expected: String) {
        // When
        val result = CanonicalFixtureMatcher.jsonEquals(json(actual), json(expected))

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M not consume a numeric event W consume() {boolean matcher field}`() {
        // Given
        val expectations = json("{\"exposures\":[{\"_count\":1,\"value\":true}]}").asJsonObject

        // When + Then
        assertThatThrownBy {
            CanonicalFixtureMatcher.consume(listOf(json("{\"value\":1}")), expectations, "exposures") { true }
        }.isInstanceOf(AssertionError::class.java)
    }

    @Test
    fun `M return the count W count() {positive integer, with or without a fraction}`(
        @IntForgery(min = 1, max = 1000) fakeCount: Int
    ) {
        // When
        val results = listOf("$fakeCount", "$fakeCount.0").map {
            CanonicalFixtureMatcher.count(json("{\"_count\":$it}").asJsonObject)
        }

        // Then
        assertThat(results).containsOnly(fakeCount)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "1.5", "true", "false", "\"2\"", "null", "[1]", "{}", "1e100", "1e-400"])
    fun `M fail the test W count() {invalid count}`(fakeCount: String) {
        // Given
        val matcher = json("{\"_count\":$fakeCount}").asJsonObject

        // When + Then
        assertThatThrownBy { CanonicalFixtureMatcher.count(matcher) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Invalid _count")
    }

    @Test
    fun `M fail the test W count() {missing count}`() {
        // When + Then
        assertThatThrownBy { CanonicalFixtureMatcher.count(JsonObject()) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Invalid _count")
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "9007199254740992.0|9007199254740993",
            "0.0|1e-400",
            "{\"a\":[9007199254740992.0]}|{\"a\":[9007199254740993]}",
            "[0.0]|[1e-400]"
        ]
    )
    fun `M not match rounded or underflowed values W jsonEquals()`(actual: String, expected: String) {
        // Given
        val actualValue = Gson().toJsonTree(Gson().fromJson(actual, Any::class.java))

        // When
        val result = CanonicalFixtureMatcher.jsonEquals(actualValue, json(expected))

        // Then
        assertThat(result).isFalse()
    }

    private fun json(text: String) = JsonParser.parseString(text)
}
