/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock

@ExtendWith(ForgeExtension::class)
internal class NoOpFlagsClientTest {

    private lateinit var testedClient: NoOpFlagsClient

    @BeforeEach
    fun `set up`() {
        testedClient = NoOpFlagsClient()
    }

    // region setEvaluationContext()

    @Test
    fun `M do nothing W setEvaluationContext()`(forge: Forge) {
        // Given
        val fakeEvaluationContext = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf(
                "user_id" to forge.anAlphabeticalString(),
                "user_email" to forge.anAlphabeticalString(),
                "plan" to "premium"
            )
        )

        // When
        testedClient.setEvaluationContext(fakeEvaluationContext)

        // Then
        // No exception should be thrown, method should be no-op
    }

    // endregion

    // region getBooleanValue()

    @Test
    fun `M return default value W getBooleanValue() {true default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = true

        // When
        val result = testedClient.getBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getBooleanValue() {false default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = false

        // When
        val result = testedClient.getBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getStringValue()

    @Test
    fun `M return default value W getStringValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        val result = testedClient.getStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getStringValue() {empty string default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = ""

        // When
        val result = testedClient.getStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getNumberValue()

    @Test
    fun `M return default value W getNumberValue() {double}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()

        // When
        val result = testedClient.getNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getNumberValue() {float}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aFloat()

        // When
        val result = testedClient.getNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getNumberValue() {long}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aLong()

        // When
        val result = testedClient.getNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getIntValue()

    @Test
    fun `M return default value W getIntValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()

        // When
        val result = testedClient.getIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getIntValue() {zero default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = 0

        // When
        val result = testedClient.getIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W getIntValue() {negative default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = -forge.anInt(min = 1)

        // When
        val result = testedClient.getIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getStructureValue()

    @Test
    fun `M return default value W getStructureValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("key1", forge.anAlphabeticalString())
            put("key2", forge.anInt())
            put("key3", forge.aBool())
        }

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        assertThat(result.toString()).isEqualTo(fakeDefaultValue.toString())
    }

    @Test
    fun `M return default value W getStructureValue() {empty JSON default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()

        // When
        val result = testedClient.getStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region getBooleanDetails()

    @Test
    fun `M return default evaluation details W getBooleanDetails()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()

        // When
        val result = testedClient.getBooleanDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getStringDetails()

    @Test
    fun `M return default evaluation details W getStringDetails()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        val result = testedClient.getStringDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getNumberDetails()

    @Test
    fun `M return default evaluation details W getNumberDetails()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()

        // When
        val result = testedClient.getNumberDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getIntDetails()

    @Test
    fun `M return default evaluation details W getIntDetails()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()

        // When
        val result = testedClient.getIntDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion

    // region getStructureDetails()

    @Test
    fun `M return default evaluation details W getStructureDetails()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("key1", forge.anAlphabeticalString())
            put("key2", forge.anInt())
        }

        // When
        val result = testedClient.getStructureDetails(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.flagKey).isEqualTo(fakeFlagKey)
        assertThat(result.value).isEqualTo(fakeDefaultValue.toString())
        assertThat(result.reason).isEqualTo("DEFAULT")
        assertThat(result.variationKey).isEqualTo("default")
    }

    // endregion
}
