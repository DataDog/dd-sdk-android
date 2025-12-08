/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import dev.openfeature.kotlin.sdk.exceptions.ErrorCode as OpenFeatureErrorCode

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class ConvertersTest {

    // region toProviderEvaluation

    @Test
    fun `M convert resolution W toProviderEvaluation() {successful boolean resolution}`(
        @BoolForgery fakeValue: Boolean,
        @StringForgery fakeVariant: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = fakeValue,
            variant = fakeVariant,
            reason = ResolutionReason.TARGETING_MATCH
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(fakeValue)
        assertThat(result.variant).isEqualTo(fakeVariant)
        assertThat(result.reason).isEqualTo("TARGETING_MATCH")
        assertThat(result.errorCode).isNull()
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `M convert resolution W toProviderEvaluation() {string resolution}`(
        @StringForgery value: String,
        @StringForgery variant: String
    ) {
        // Given
        val resolution = ResolutionDetails(
            value = value,
            variant = variant,
            reason = ResolutionReason.DEFAULT
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(value)
        assertThat(result.variant).isEqualTo(variant)
        assertThat(result.reason).isEqualTo("DEFAULT")
    }

    @Test
    fun `M convert resolution W toProviderEvaluation() {integer resolution}`(forge: Forge) {
        // Given
        val value = forge.anInt()
        val resolution = ResolutionDetails(
            value = value,
            reason = ResolutionReason.RULE_MATCH
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isEqualTo(value)
        assertThat(result.variant).isNull()
        assertThat(result.reason).isEqualTo("RULE_MATCH")
    }

    @Test
    fun `M convert error resolution W toProviderEvaluation() {with error code}`(@StringForgery errorMessage: String) {
        // Given
        val resolution = ResolutionDetails(
            value = false,
            errorCode = ErrorCode.FLAG_NOT_FOUND,
            errorMessage = errorMessage,
            reason = ResolutionReason.ERROR
        )

        // When
        val result = resolution.toProviderEvaluation()

        // Then
        assertThat(result.value).isFalse()
        assertThat(result.errorCode).isEqualTo(OpenFeatureErrorCode.FLAG_NOT_FOUND)
        assertThat(result.errorMessage).isEqualTo(errorMessage)
        assertThat(result.reason).isEqualTo("ERROR")
    }

    // endregion

    // region toOpenFeatureErrorCode

    @ParameterizedTest
    @MethodSource("errorCodeMappings")
    fun `M map error codes W toOpenFeatureErrorCode() {all error codes}`(
        datadogErrorCode: ErrorCode,
        expectedOpenFeatureErrorCode: OpenFeatureErrorCode
    ) {
        // When
        val result = datadogErrorCode.toOpenFeatureErrorCode()

        // Then
        assertThat(result).isEqualTo(expectedOpenFeatureErrorCode)
    }

    // endregion

    // region toMap

    @Test
    fun `M convert JSONObject W toMap() {simple object}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put("string", "value")
            put("int", 42)
            put("double", 3.14)
            put("boolean", true)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).containsEntry("string", "value")
        assertThat(result).containsEntry("int", 42)
        assertThat(result).containsEntry("double", 3.14)
        assertThat(result).containsEntry("boolean", true)
        assertThat(result).hasSize(4)
    }

    @Test
    fun `M filter null values W toMap() {JSONObject with null}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put("key1", "value1")
            put("key2", JSONObject.NULL)
            put("key3", "value3")
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).containsEntry("key1", "value1")
        assertThat(result).containsEntry("key3", "value3")
        assertThat(result).doesNotContainKey("key2")
        assertThat(result).hasSize(2)
    }

    @Test
    fun `M convert nested objects W toMap() {nested JSONObject}`() {
        // Given
        val nestedObject = JSONObject().apply {
            put("nested", "value")
        }
        val jsonObject = JSONObject().apply {
            put("top", "level")
            put("object", nestedObject)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).containsEntry("top", "level")
        assertThat(result).containsKey("object")
        assertThat(result["object"]).isInstanceOf(JSONObject::class.java)
    }

    @Test
    fun `M convert arrays W toMap() {with JSONArray}`() {
        // Given
        val jsonArray = JSONArray().apply {
            put("item1")
            put("item2")
        }
        val jsonObject = JSONObject().apply {
            put("array", jsonArray)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).containsKey("array")
        assertThat(result["array"]).isInstanceOf(JSONArray::class.java)
    }

    @Test
    fun `M return empty map W toMap() {empty JSONObject}`() {
        // Given
        val jsonObject = JSONObject()

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M handle large objects W toMap() {many keys}`(forge: Forge) {
        // Given
        val jsonObject = JSONObject()
        val entries = (1..100).associate { index ->
            "key$index" to forge.anAlphabeticalString()
        }
        entries.forEach { (key, value) ->
            jsonObject.put(key, value)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).hasSize(100)
        entries.forEach { (key, value) ->
            assertThat(result).containsEntry(key, value)
        }
    }

    @Test
    fun `M skip invalid entries W toMap() {with mixed valid and null entries}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put("valid1", "value1")
            put("null1", JSONObject.NULL)
            put("valid2", 42)
            put("null2", JSONObject.NULL)
            put("valid3", true)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).hasSize(3)
        assertThat(result).containsEntry("valid1", "value1")
        assertThat(result).containsEntry("valid2", 42)
        assertThat(result).containsEntry("valid3", true)
    }

    @Test
    fun `M handle special characters W toMap() {keys with special chars}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put("key-with-dash", "value1")
            put("key_with_underscore", "value2")
            put("key.with.dot", "value3")
            put("key with space", "value4")
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).hasSize(4)
        assertThat(result).containsEntry("key-with-dash", "value1")
        assertThat(result).containsEntry("key_with_underscore", "value2")
        assertThat(result).containsEntry("key.with.dot", "value3")
        assertThat(result).containsEntry("key with space", "value4")
    }

    @Test
    fun `M handle complex nested structure W toMap() {deeply nested}`() {
        // Given
        val deeplyNested = JSONObject().apply {
            put("level3", "deep")
        }
        val midLevel = JSONObject().apply {
            put("level2", deeplyNested)
        }
        val jsonObject = JSONObject().apply {
            put("level1", midLevel)
            put("topLevel", "value")
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).hasSize(2)
        assertThat(result).containsEntry("topLevel", "value")
        assertThat(result["level1"]).isInstanceOf(JSONObject::class.java)
    }

    // endregion

    // region toDatadogEvaluationContext
    // Note: Context conversion is tested indirectly through provider integration tests
    // Direct unit tests removed due to ImmutableContext API complexity
    // endregion

    companion object {
        @JvmStatic
        fun errorCodeMappings() = listOf(
            Arguments.of(ErrorCode.PROVIDER_NOT_READY, OpenFeatureErrorCode.PROVIDER_NOT_READY),
            Arguments.of(ErrorCode.FLAG_NOT_FOUND, OpenFeatureErrorCode.FLAG_NOT_FOUND),
            Arguments.of(ErrorCode.PARSE_ERROR, OpenFeatureErrorCode.PARSE_ERROR),
            Arguments.of(ErrorCode.TYPE_MISMATCH, OpenFeatureErrorCode.TYPE_MISMATCH)
        )
    }
}
