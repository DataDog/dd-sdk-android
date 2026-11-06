/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.serialization

import com.datadog.android.flags.internal.model.SplitFeature
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
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

    @Test
    fun `M keep each value type W readAssignment() {mixed features}`() {
        // Given
        val json = assignment(
            """[
                {"key":"text","value":"holdout-1","destinations":["HOOK"]},
                {"key":"number","value":3,"destinations":["EXPOSURE","EVALUATION"]},
                {"key":"flag","value":false,"destinations":["HOOK","FUTURE"]}
            ]"""
        )

        // When
        val flag = FlagsJson.readAssignment(json)

        // Then
        assertThat(flag.features).containsExactly(
            SplitFeature("text", "holdout-1", listOf("HOOK")),
            SplitFeature("number", 3.0, listOf("EXPOSURE", "EVALUATION")),
            SplitFeature("flag", false, listOf("HOOK", "FUTURE"))
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "{}", "\"features\"", "42", "[]", "[null, 1, \"text\", []]"])
    fun `M read the assignment without features W readAssignment() {missing or malformed features}`(
        features: String
    ) {
        // Given
        val json = assignment(features)

        // When
        val flag = FlagsJson.readAssignment(json)

        // Then
        assertThat(flag.variationKey).isEqualTo("on")
        assertThat(flag.features).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("invalidFeatures")
    fun `M skip the feature W read() {invalid feature}`(feature: String) {
        // Given
        val json = JsonParser.parseString("[$feature, $VALID_FEATURE]")

        // When
        val features = SplitFeaturesJson.read(json)

        // Then
        assertThat(features).containsExactly(SplitFeature("valid", "value", listOf("HOOK")))
    }

    @Test
    fun `M keep the first valid feature W read() {repeated key}`() {
        // Given
        val json = JsonParser.parseString(
            """[
                {"key":"holdout.key","value":{},"destinations":["HOOK"]},
                {"key":"holdout.key","value":"first","destinations":["EXPOSURE"]},
                {"key":"holdout.key","value":"second","destinations":["HOOK"]}
            ]"""
        )

        // When
        val features = SplitFeaturesJson.read(json)

        // Then
        assertThat(features).containsExactly(SplitFeature("holdout.key", "first", listOf("EXPOSURE")))
    }

    @Test
    fun `M read back the features W writeAssignmentJson() + readAssignment()`() {
        // Given
        val flag = FlagsJson.readAssignment(
            assignment(
                """[{"key":"a","value":1.5,"destinations":["EVALUATION","FUTURE"]},""" +
                    """{"key":"b","value":true,"destinations":["HOOK"]}]"""
            )
        )

        // When
        val json = FlagsJson.writeAssignmentJson(flag)

        // Then
        assertThat(flag.features).hasSize(2)
        assertThat(FlagsJson.readAssignment(json.asJsonObject).features).isEqualTo(flag.features)
    }

    @Test
    fun `M write no features field W writeAssignmentJson() {no features}`() {
        // Given
        val flag = FlagsJson.readAssignment(assignment("[]"))

        // When
        val json = FlagsJson.writeAssignmentJson(flag)

        // Then
        assertThat(json.asJsonObject.has("features")).isFalse()
    }

    private fun assignment(features: String) = JsonParser.parseString(
        """{"variationType":"boolean","variationValue":"true","doLog":true,"allocationKey":"allocation",""" +
            """"variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH","features":$features}"""
    ).asJsonObject

    companion object {
        private const val VALID_FEATURE = """{"key":"valid","value":"value","destinations":["HOOK"]}"""

        @JvmStatic
        fun invalidFeatures(): List<String> = listOf(
            """{"value":"value","destinations":["HOOK"]}""",
            """{"key":"","value":"value","destinations":["HOOK"]}""",
            """{"key":1,"value":"value","destinations":["HOOK"]}""",
            """{"key":"valid","destinations":["HOOK"]}""",
            """{"key":"valid","value":null,"destinations":["HOOK"]}""",
            """{"key":"valid","value":{},"destinations":["HOOK"]}""",
            """{"key":"valid","value":[],"destinations":["HOOK"]}""",
            """{"key":"valid","value":1e400,"destinations":["HOOK"]}""",
            """{"key":"valid","value":"value"}""",
            """{"key":"valid","value":"value","destinations":"HOOK"}""",
            """{"key":"valid","value":"value","destinations":[]}""",
            """{"key":"valid","value":"value","destinations":["",1,null]}""",
            "\"not an object\""
        )

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
