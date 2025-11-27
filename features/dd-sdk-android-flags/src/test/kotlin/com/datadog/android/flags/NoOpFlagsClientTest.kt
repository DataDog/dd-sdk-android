/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.LogWithPolicy
import com.datadog.android.flags.internal.NoOpFlagsClient
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NoOpFlagsClientTest {

    @Mock
    lateinit var mockLogWithPolicy: LogWithPolicy

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
            logWithPolicy = mockLogWithPolicy
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
    fun `M log warning W setEvaluationContext()`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(forge.anAlphabeticalString(), emptyMap())

        // When
        testedClient.setEvaluationContext(fakeContext)

        // Then

        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "setEvaluationContext called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
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
    fun `M log warning W resolveBooleanValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aBool()

        // When
        testedClient.resolveBooleanValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "resolveBooleanValue for flag '$fakeFlagKey' called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
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
    fun `M log warning W resolveStringValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        testedClient.resolveStringValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "resolveStringValue for flag '$fakeFlagKey' called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
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
    fun `M log warning W resolveDoubleValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.aDouble()

        // When
        testedClient.resolveDoubleValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "resolveDoubleValue for flag '$fakeFlagKey' called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
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
    fun `M log warning W resolveIntValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anInt()

        // When
        testedClient.resolveIntValue(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "resolveIntValue for flag '$fakeFlagKey' called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
        )
    }

    // endregion

    // region resolve()

    @Test
    fun `M return default value W resolve()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.value).isEqualTo(fakeDefaultValue)
    }

    @Test
    fun `M return PROVIDER_NOT_READY error code W resolve()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        val result = testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(result.errorMessage).isEqualTo("Provider not ready - using fallback client")
    }

    @Test
    fun `M log warning W resolve()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeDefaultValue = forge.anAlphabeticalString()

        // When
        testedClient.resolve(fakeFlagKey, fakeDefaultValue)

        // Then
        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "resolve for flag '$fakeFlagKey' called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
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
    fun `M log warning W resolveStructureValue()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()

        // When
        testedClient.resolveStructureValue(fakeFlagKey, JSONObject())

        // Then
        verify(mockLogWithPolicy).invoke(
            argThat {
                startsWith(
                    "resolveStructureValue for flag '$fakeFlagKey' " +
                        "called on NoOpFlagsClient for client '$fakeClientName'"
                )
            },
            eq(InternalLogger.Level.WARN)
        )
    }

    // endregion

    // region State Management

    @Test
    fun `M return ready state W state_getCurrentState()`() {
        // When
        val state = testedClient.state.getCurrentState()

        // Then
        // NoOp client is always "ready" to return default values
        assertThat(state).isEqualTo(FlagsClientState.Ready)
    }

    @Test
    fun `M do nothing W state_addListener()`() {
        // Given
        val mockListener = mock<FlagsStateListener>()

        // When
        testedClient.state.addListener(mockListener)

        // Then
        // No exception should be thrown, method should be no-op
        verifyNoInteractions(mockListener)
    }

    @Test
    fun `M do nothing W state_removeListener()`() {
        // Given
        val mockListener = mock<FlagsStateListener>()

        // When
        testedClient.state.removeListener(mockListener)

        // Then
        // No exception should be thrown, method should be no-op
        verifyNoInteractions(mockListener)
    }

    // endregion
}
