/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
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
    }

    @AfterEach
    fun `tear down`() {
        FlagsClient.clear()
    }

    // region Static Flag Resolution Methods

    @Test
    fun `M return no-op default W instance() + resolveBooleanValue() {no client registered for SDK core}`(
        @StringForgery fakeFlagName: String,
        @BoolForgery fakeFlagDefaultValue: Boolean
    ) {
        // When
        val client = FlagsClient.instance(mockSdkCore)
        val result = client.resolveBooleanValue(fakeFlagName, fakeFlagDefaultValue)

        // Then
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(result).isEqualTo(fakeFlagDefaultValue)
    }

    @Test
    fun `M delegate to registered client W instance() + resolveBooleanValue() {client registered for SDK core}`() {
        // Given
        whenever(mockFlagsClient.resolveBooleanValue("test-flag", false)).thenReturn(true)
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val client = FlagsClient.instance(mockSdkCore)
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
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClient.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        val firstClient = FlagsClient.instance(mockSdkCore)
        val secondClient = FlagsClient.instance(mockSecondSdkCore)
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
    fun `M delegate to registered client W setContext() {client registered for SDK core}`() {
        // Given
        val fakeContext = EvaluationContext("user123", mapOf("plan" to "premium"))
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val client = FlagsClient.instance(mockSdkCore)
        client.setContext(fakeContext)

        // Then
        assertThat(client).isEqualTo(mockFlagsClient)
        verify(mockFlagsClient).setContext(fakeContext)
    }

    // endregion

    // region Internal Registration Methods

    @Test
    fun `M return true W registerIfAbsent() {first registration}`() {
        // When
        val result = FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClient.registerIfAbsent(mockSecondFlagsClient, mockSdkCore)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M register different clients W registerIfAbsent() {different SDK core instances}`() {
        // When
        val firstResult = FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)
        val secondResult = FlagsClient.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // Then
        assertThat(firstResult).isTrue()
        assertThat(secondResult).isTrue()
    }

    @Test
    fun `M remove client W unregister() {client registered for SDK core}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        FlagsClient.unregister(mockSdkCore)

        // Then
        // Verify client was removed by checking no-op behavior
        val client = FlagsClient.instance(mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(client.resolveBooleanValue("test", false)).isFalse()
    }

    @Test
    fun `M do nothing W unregister() {no client registered for SDK core}`() {
        // When / Then - should not throw
        assertDoesNotThrow {
            FlagsClient.unregister(mockSdkCore)
        }

        // Verify operation completed without affecting other state
        val client = FlagsClient.instance(mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M only remove specific client W unregister() {multiple clients registered for different SDK cores}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClient.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        FlagsClient.unregister(mockSdkCore)

        // Then
        // First should be no-op, second should still be registered
        val firstClient = FlagsClient.instance(mockSdkCore)
        assertThat(firstClient).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(firstClient.resolveBooleanValue("test", false)).isFalse()
    }

    @Test
    fun `M remove all clients W clear() {multiple clients registered across SDK cores}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClient.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        FlagsClient.clear()

        // Then
        // Both should now be no-op
        val firstClient = FlagsClient.instance(mockSdkCore)
        val secondClient = FlagsClient.instance(mockSecondSdkCore)
        assertThat(firstClient).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(secondClient).isInstanceOf(NoOpFlagsClient::class.java)
        assertThat(firstClient.resolveBooleanValue("test", false)).isFalse()
        assertThat(secondClient.resolveBooleanValue("test", false)).isFalse()
    }

    @Test
    fun `M do nothing W clear() {no clients registered across any SDK cores}`() {
        // When / Then - should not throw
        assertDoesNotThrow {
            FlagsClient.clear()
        }

        // Verify operation completed - should still return no-op clients
        val client = FlagsClient.instance(mockSdkCore)
        assertThat(client).isInstanceOf(NoOpFlagsClient::class.java)
    }

    // endregion
}
