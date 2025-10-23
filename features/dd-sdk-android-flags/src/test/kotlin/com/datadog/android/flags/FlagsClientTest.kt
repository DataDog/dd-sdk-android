/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.NoOpFlagsClient
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsClientTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsClient: FlagsClient

    @Mock
    lateinit var mockSecondSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockSecondFlagsClient: FlagsClient

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSecondSdkCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockSdkCore.name).thenReturn("test-sdk")
        whenever(mockSecondSdkCore.name).thenReturn("test-sdk-2")

        // Enable the Flags Feature for first SDK core
        Flags.enable(sdkCore = mockSdkCore)

        // Capture the registered feature and mock getFeature() to return a FeatureScope wrapping it
        argumentCaptor<Feature> {
            verify(mockSdkCore).registerFeature(capture())
            val capturedFeature = lastValue as FlagsFeature
            val mockFeatureScope = mock<FeatureScope>()
            whenever(
                mockFeatureScope.unwrap<FlagsFeature>()
            ).thenReturn(capturedFeature)
            whenever(mockSdkCore.getFeature(FLAGS_FEATURE_NAME))
                .thenReturn(mockFeatureScope)
        }

        // Enable the Flags Feature for second SDK core
        Flags.enable(sdkCore = mockSecondSdkCore)

        // Capture the second registered feature
        argumentCaptor<Feature> {
            verify(mockSecondSdkCore).registerFeature(capture())
            val capturedFeature = lastValue as FlagsFeature
            val mockFeatureScope = mock<FeatureScope>()
            whenever(
                mockFeatureScope.unwrap<FlagsFeature>()
            ).thenReturn(capturedFeature)
            whenever(mockSecondSdkCore.getFeature(FLAGS_FEATURE_NAME))
                .thenReturn(mockFeatureScope)
        }
    }

    @AfterEach
    fun `tear down`() {
        clearRegisteredClients(mockSdkCore)
        clearRegisteredClients(mockSecondSdkCore)
    }

    // Helper methods for test setup
    private fun registerClient(client: FlagsClient, sdkCore: FeatureSdkCore, name: String) {
        val flagsFeature = sdkCore.getFeature(FLAGS_FEATURE_NAME)
            ?.unwrap<FlagsFeature>()
        flagsFeature?.getOrRegisterNewClient(name) { client }
    }

    private fun unregisterClient(name: String, sdkCore: FeatureSdkCore) {
        val flagsFeature = sdkCore.getFeature(FLAGS_FEATURE_NAME)
            ?.unwrap<FlagsFeature>()
        flagsFeature?.unregisterClient(name)
    }

    private fun clearRegisteredClients(sdkCore: FeatureSdkCore) {
        val flagsFeature = sdkCore.getFeature(FLAGS_FEATURE_NAME)
            ?.unwrap<FlagsFeature>()
        flagsFeature?.clearClients()
    }

    // region Static Flag Resolution Methods

    @Test
    fun `M return no-op default W get() + resolveBooleanValue() {no client registered for SDK core}`(
        @StringForgery fakeFlagName: String,
        @BoolForgery fakeFlagDefaultValue: Boolean
    ) {
        // When
        val client = FlagsClient.get(sdkCore = mockSdkCore)
        val result = client.resolveBooleanValue(fakeFlagName, fakeFlagDefaultValue)

        // Then
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(result).isEqualTo(fakeFlagDefaultValue)
    }

    @Test
    fun `M delegate to registered client W get() + resolveBooleanValue() {client registered for SDK core}`() {
        // Given
        whenever(mockFlagsClient.resolveBooleanValue("test-flag", false)).thenReturn(true)
        registerClient(mockFlagsClient, mockSdkCore, "default")

        // When
        val client = FlagsClient.get(sdkCore = mockSdkCore)
        val result = client.resolveBooleanValue("test-flag", false)

        // Then
        assertThat(client).isEqualTo(mockFlagsClient)
        assertThat(result).isTrue()
        verify(mockFlagsClient).resolveBooleanValue("test-flag", false)
    }

    @Test
    fun `M delegate to correct client W resolveBooleanValue() {multiple clients registered for different SDK cores}`() {
        // Given
        whenever(mockFlagsClient.resolveBooleanValue("test-flag", false)).thenReturn(true)
        whenever(mockSecondFlagsClient.resolveBooleanValue("test-flag", false)).thenReturn(false)
        registerClient(mockFlagsClient, mockSdkCore, "default")
        registerClient(mockSecondFlagsClient, mockSecondSdkCore, "default")

        // When
        val firstClient = FlagsClient.get(sdkCore = mockSdkCore)
        val secondClient = FlagsClient.get(sdkCore = mockSecondSdkCore)
        val firstResult = firstClient.resolveBooleanValue("test-flag", false)
        val secondResult = secondClient.resolveBooleanValue("test-flag", false)

        // Then
        assertThat(firstClient).isEqualTo(mockFlagsClient)
        assertThat(secondClient).isEqualTo(mockSecondFlagsClient)
        assertThat(firstResult).isTrue()
        assertThat(secondResult).isFalse()
        verify(mockFlagsClient).resolveBooleanValue("test-flag", false)
        verify(mockSecondFlagsClient).resolveBooleanValue("test-flag", false)
    }

    @Test
    fun `M delegate to registered client W setEvaluationContext() {client registered for SDK core}`() {
        // Given
        val fakeContext = EvaluationContext("user123", mapOf("plan" to "premium"))
        registerClient(mockFlagsClient, mockSdkCore, "default")

        // When
        val client = FlagsClient.get(sdkCore = mockSdkCore)
        client.setEvaluationContext(fakeContext)

        // Then
        assertThat(client).isEqualTo(mockFlagsClient)
        verify(mockFlagsClient).setEvaluationContext(fakeContext)
    }

    // endregion

    // region Internal unregister/clear Methods

    @Test
    fun `M remove client W unregister() {client registered for SDK core}`() {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, "default")

        // When
        unregisterClient("default", mockSdkCore)

        // Then
        // Verify client was removed by checking no-op behavior
        val client = FlagsClient.get(sdkCore = mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(client.resolveBooleanValue("test", false)).isFalse()
    }

    @Test
    fun `M do nothing W unregister() {no client registered for SDK core}`() {
        // When / Then - should not throw
        assertDoesNotThrow {
            unregisterClient("default", mockSdkCore)
        }

        // Verify operation completed without affecting other state
        val client = FlagsClient.get(sdkCore = mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M only remove specific client W unregister() {multiple clients registered, other cores unaffected}`() {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, "default")
        registerClient(mockSecondFlagsClient, mockSecondSdkCore, "default")

        // When
        unregisterClient("default", mockSdkCore)

        // Then
        // First should be no-op, second should still be registered
        val firstClient = FlagsClient.get(sdkCore = mockSdkCore)
        assertThat(firstClient).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(firstClient.resolveBooleanValue("test", false)).isFalse()

        // Second client should still be registered
        val secondClient = FlagsClient.get(sdkCore = mockSecondSdkCore)
        assertThat(secondClient).isEqualTo(mockSecondFlagsClient)
    }

    @Test
    fun `M remove all clients W clear() {multiple clients registered across SDK cores}`() {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, "default")
        registerClient(mockSecondFlagsClient, mockSecondSdkCore, "default")

        // When
        clearRegisteredClients(mockSdkCore)
        clearRegisteredClients(mockSecondSdkCore)

        // Then
        // Both should now be no-op
        val firstClient = FlagsClient.get(sdkCore = mockSdkCore)
        val secondClient = FlagsClient.get(sdkCore = mockSecondSdkCore)
        assertThat(firstClient).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(secondClient).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(firstClient.resolveBooleanValue("test", false)).isFalse()
        assertThat(secondClient.resolveBooleanValue("test", false)).isFalse()
    }

    @Test
    fun `M do nothing W clear() {no clients registered across any SDK cores}`() {
        // When / Then - should not throw
        assertDoesNotThrow {
            clearRegisteredClients(mockSdkCore)
        }

        // Verify operation completed - should still return no-op clients
        val client = FlagsClient.get(sdkCore = mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    // endregion

    // region Builder API Tests

    @Test
    fun `M return existing client W Builder#build() {client already exists with default name}`() {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, "default")

        // When
        val client = FlagsClient.Builder(sdkCore = mockSdkCore).build()

        // Then
        assertThat(client).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M return existing client W Builder#build() {client already exists with custom name}`(
        @StringForgery fakeClientName: String
    ) {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, fakeClientName)

        // When - second build with same custom name
        val client = FlagsClient.Builder(fakeClientName, mockSdkCore).build()

        // Then - should return existing client, NOT NOPClient
        assertThat(client).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M log warning W Builder#build() {client already exists}`() {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, "default")

        // When
        val client = FlagsClient.Builder(sdkCore = mockSdkCore).build()

        // Then - should return existing client and log warning
        assertThat(client).isEqualTo(mockFlagsClient)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M create different clients W Builder#build() {different names}`(
        @StringForgery fakeName1: String,
        @StringForgery fakeName2: String
    ) {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, fakeName1)

        // When - build with different names
        val client1 = FlagsClient.get(name = fakeName1, sdkCore = mockSdkCore)
        val client2 = FlagsClient.get(name = fakeName2, sdkCore = mockSdkCore)

        // Then - first should be registered client, second should be NOPClient (custom name not found)
        assertThat(client1).isEqualTo(mockFlagsClient)
        assertThat(client2).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M return NoOpClient W Builder#build() {flags feature not enabled}`() {
        // When - build when feature is not enabled
        val client = FlagsClient.Builder(sdkCore = mockSdkCore).build()

        // Then - should return NoOpClient
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    // endregion

    // region get() API Tests

    @Test
    fun `M return NoOpClient W get() {default client doesn't exist}`() {
        // When - get default client when it doesn't exist
        val client = FlagsClient.get(sdkCore = mockSdkCore)

        // Then - should return NoOpClient (no auto-creation)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M log critical error W get() {client doesn't exist}`() {
        // When - get default client when it doesn't exist
        val client = FlagsClient.get(sdkCore = mockSdkCore)

        // Then - should log critical error
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.USER),
            any(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return existing client W get() {default client exists}`() {
        // Given
        registerClient(mockFlagsClient, mockSdkCore, "default")

        // When
        val client = FlagsClient.get(sdkCore = mockSdkCore)

        // Then
        assertThat(client).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M return NoOpClient W get(name) {custom name doesn't exist}`(@StringForgery fakeClientName: String) {
        // When - get custom-named client when it doesn't exist
        val client = FlagsClient.get(name = fakeClientName, sdkCore = mockSdkCore)

        // Then - should return NoOpClient (no auto-creation)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    // endregion
}
