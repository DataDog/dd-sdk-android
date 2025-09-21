/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.flags.featureflags.internal.NoOpFlagsProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class NoOpFlagsProviderTest {

    private lateinit var testedProvider: NoOpFlagsProvider

    @BeforeEach
    fun `set up`() {
        testedProvider = NoOpFlagsProvider()
    }

    // region setContext()

    @Test
    fun `M do nothing W setContext()`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "user_id" to forge.anAlphabeticalString(),
            "user_email" to forge.anAlphabeticalString(),
            "plan" to "premium"
        )

        // When
        testedProvider.setContext(fakeTargetingKey, fakeAttributes)

        // Then
        // No exception should be thrown, method should be no-op
    }

    // endregion

    // region resolveBooleanValue()

    @Test
    fun `M return default value W resolveBooleanValue() {true default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = true

        // When
        val result = testedProvider.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() {false default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = false

        // When
        val result = testedProvider.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveStringValue()

    @Test
    fun `M return default value W resolveStringValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        val result = testedProvider.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveStringValue() {empty string default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = ""

        // When
        val result = testedProvider.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveNumberValue()

    @Test
    fun `M return default value W resolveNumberValue() {double}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()

        // When
        val result = testedProvider.resolveNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveNumberValue() {float}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aFloat()

        // When
        val result = testedProvider.resolveNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveNumberValue() {long}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aLong()

        // When
        val result = testedProvider.resolveNumberValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveIntValue()

    @Test
    fun `M return default value W resolveIntValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()

        // When
        val result = testedProvider.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() {zero default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = 0

        // When
        val result = testedProvider.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() {negative default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = -forge.anInt(min = 1)

        // When
        val result = testedProvider.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion

    // region resolveStructureValue()

    @Test
    fun `M return default value W resolveStructureValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject().apply {
            put("key1", forge.anAlphabeticalString())
            put("key2", forge.anInt())
            put("key3", forge.aBool())
        }

        // When
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
        assertThat(result.toString()).isEqualTo(fakeDefaultValue.toString())
    }

    @Test
    fun `M return default value W resolveStructureValue() {empty JSON default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = JSONObject()

        // When
        val result = testedProvider.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    // endregion
}
