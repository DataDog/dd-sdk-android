/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

internal class FlagsJsonTest {
    @ParameterizedTest
    @MethodSource("invalidMetadata")
    fun `M reject invalid descriptor W readObfuscation() {strict encoding contract}`(json: String) {
        // Given
        val metadata = JsonParser.parseString(json).asJsonObject

        // When
        val exception = catchThrowable { FlagsJson.readObfuscation(metadata) }

        // Then
        assertThat(exception).isInstanceOf(JsonParseException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "{\"obfuscated\":false}", "{\"unrecognized\":true}"])
    fun `M accept plaintext W readObfuscation() {legacy metadata}`(json: String) {
        // Given
        val metadata = JsonParser.parseString(json).asJsonObject

        // When
        val actual = FlagsJson.readObfuscation(metadata)

        // Then
        assertThat(actual).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["plaintext", "", "UPPERCASE"])
    fun `M reject malformed flag key W validateKeys() {encoded assignments}`(key: String) {
        // Given
        val keys = listOf(key).iterator()

        // When
        val exception = catchThrowable { FlagsJson.validateKeys(keys) }

        // Then
        assertThat(exception).isInstanceOf(JsonParseException::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [63, 65])
    fun `M reject incorrect digest length W validateKeys() {encoded assignments}`(length: Int) {
        // Given
        val keys = listOf("a".repeat(length)).iterator()

        // When
        val exception = catchThrowable { FlagsJson.validateKeys(keys) }

        // Then
        assertThat(exception).isInstanceOf(JsonParseException::class.java)
    }

    companion object {
        @JvmStatic
        fun invalidMetadata(): List<String> {
            val valid = """{"obfuscated":true,"obfuscation":""" +
                """{"scheme":"flag-key-sha256-v1","salt":"000102030405060708090a0b0c0d0e0f"}}"""
            return listOf(
                """{"obfuscated":true}""",
                """{"obfuscated":null}""",
                """{"obfuscated":true,"obfuscation":null}""",
                """{"obfuscated":true,"obfuscation":[]}""",
                valid.replace("true", "\"true\""),
                valid.replace("true", "false"),
                valid.replace("\"obfuscated\":true,", ""),
                valid.replace("flag-key-sha256-v1", "unknown"),
                valid.replace("\"flag-key-sha256-v1\"", "1"),
                valid.replace("\"flag-key-sha256-v1\"", "null")
            ) + listOf(
                "\"\"", "\"${"0".repeat(30)}\"", "\"${"0".repeat(34)}\"",
                "\"${"G".repeat(32)}\"", "\"${"A".repeat(32)}\"", "\"${"0".repeat(31)}\\n\"",
                "42", "true", "null", "[]", "{}"
            ).map { valid.replace("\"000102030405060708090a0b0c0d0e0f\"", it) }
        }
    }
}
