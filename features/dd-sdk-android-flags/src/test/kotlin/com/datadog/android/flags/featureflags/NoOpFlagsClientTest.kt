/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NoOpFlagsClientTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedClient: NoOpFlagsClient

    @StringForgery
    private lateinit var fakeClientName: String

    @StringForgery
    private lateinit var fakeReason: String

    @BeforeEach
    fun `set up`() {
        testedClient = NoOpFlagsClient(
            name = fakeClientName,
            reason = fakeReason,
            logger = mockInternalLogger
        )
    }

    // region setEvaluationContext()

    @Test
    fun `M do nothing W setEvaluationContext()`(forge: Forge) {
        // Given
        val fakeTargetingKey = forge.anAlphabeticalString()
        val fakeAttributes = mapOf(
            "user_id" to forge.anAlphabeticalString(),
            "user_email" to forge.anAlphabeticalString(),
            "plan" to forge.anAlphabeticalString()
        )
        val fakeContext = EvaluationContext(fakeTargetingKey, fakeAttributes)

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        // No exception should be thrown, method should be no-op
    }

    @Test
    fun `M log critical error W setEvaluationContext()`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString(), emptyMap())

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region resolveBooleanValue()

    @Test
    fun `M return default value W resolveBooleanValue() {true default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveBooleanValue() {false default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M log critical error W resolveBooleanValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()

        // When
        testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region resolveStringValue()

    @Test
    fun `M return default value W resolveStringValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveStringValue() {empty string default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = ""

        // When
        val result = testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M log critical error W resolveStringValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region resolveDoubleValue()

    @Test
    fun `M return default value W resolveDoubleValue() {double}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue.toDouble())

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveDoubleValue() {float}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aFloat()

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue.toDouble())

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue.toDouble())
    }

    @Test
    fun `M return default value W resolveDoubleValue() {long}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aLong()

        // When
        val result = testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue.toDouble())

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue.toDouble())
    }

    @Test
    fun `M log critical error W resolveDoubleValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()

        // When
        testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    // endregion

    // region resolveIntValue()

    @Test
    fun `M return default value W resolveIntValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() {zero default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = 0

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return default value W resolveIntValue() {negative default}`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = -forge.anInt(min = 1)

        // When
        val result = testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M log critical error W resolveIntValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()

        // When
        testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
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
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

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
        val result = testedClient.resolveStructureValue(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M log critical error W resolveStructureValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()

        // When
        testedClient.resolveStructureValue(fakeFlagKey, JSONObject())

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    // endregion
}
