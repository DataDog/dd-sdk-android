/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.NoOpFlagsClient
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsClientManagerTest {

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

    private lateinit var fakeSdkCoreName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSdkCoreName = forge.anAlphabeticalString()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeSdkCoreName
        whenever(mockSecondSdkCore.internalLogger) doReturn mockInternalLogger

        // Clean state before each test
        FlagsClientManager.clear()
    }

    @AfterEach
    fun `tear down`() {
        FlagsClientManager.clear()
    }

    // region isRegistered()

    @Test
    fun `M return false W isRegistered() {no provider registered}`() {
        // When
        val result = FlagsClientManager.isRegistered(mockSdkCore)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isRegistered() {provider registered}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClientManager.isRegistered(mockSdkCore)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false for different SDK W isRegistered() {provider registered for other SDK}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClientManager.isRegistered(mockSecondSdkCore)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region get()

    @Test
    fun `M return NoOpFlagsClient W get() {no provider registered}`() {
        // When
        val result = FlagsClientManager.get(mockSdkCore)

        // Then
        assertThat(result).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M log warning W get() {no provider registered}`() {
        // When
        val result = FlagsClientManager.get(mockSdkCore)

        // Then
        assertThat(result).isInstanceOf(NoOpFlagsClient::class.java)
        verify(mockInternalLogger, atLeastOnce()).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M return registered provider W get() {provider registered}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClientManager.get(mockSdkCore)

        // Then
        assertThat(result).isEqualTo(mockFlagsClient)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return correct provider W get() {multiple providers registered}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClientManager.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        val firstResult = FlagsClientManager.get(mockSdkCore)
        val secondResult = FlagsClientManager.get(mockSecondSdkCore)

        // Then
        assertThat(firstResult).isEqualTo(mockFlagsClient)
        assertThat(secondResult).isEqualTo(mockSecondFlagsClient)
    }

    // endregion

    // region registerIfAbsent()

    @Test
    fun `M return true W registerIfAbsent() {first registration}`() {
        // When
        val result = FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // Then
        assertThat(result).isTrue()
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isTrue()
        assertThat(FlagsClientManager.get(mockSdkCore)).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M return false W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClientManager.registerIfAbsent(mockSecondFlagsClient, mockSdkCore)

        // Then
        assertThat(result).isFalse()
        assertThat(FlagsClientManager.get(mockSdkCore)).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M log warning W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        FlagsClientManager.registerIfAbsent(mockSecondFlagsClient, mockSdkCore)

        // Then
        verify(mockInternalLogger, atLeastOnce()).log(
            any<InternalLogger.Level>(),
            any<InternalLogger.Target>(),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M register different providers W registerIfAbsent() {different core instances}`() {
        // When
        val firstResult = FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)
        val secondResult = FlagsClientManager.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // Then
        assertThat(firstResult).isTrue()
        assertThat(secondResult).isTrue()
        assertThat(FlagsClientManager.get(mockSdkCore)).isEqualTo(mockFlagsClient)
        assertThat(FlagsClientManager.get(mockSecondSdkCore)).isEqualTo(mockSecondFlagsClient)
    }

    // endregion

    // region unregister()

    @Test
    fun `M remove provider W unregister() {provider registered}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isTrue()

        // When
        FlagsClientManager.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isFalse()
        assertThat(FlagsClientManager.get(mockSdkCore)).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M do nothing W unregister() {no provider registered}`() {
        // When
        FlagsClientManager.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isFalse()
    }

    @Test
    fun `M only remove specific provider W unregister() {multiple providers registered}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClientManager.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        FlagsClientManager.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isFalse()
        assertThat(FlagsClientManager.isRegistered(mockSecondSdkCore)).isTrue()
        assertThat(FlagsClientManager.get(mockSecondSdkCore)).isEqualTo(mockSecondFlagsClient)
    }

    // endregion

    // region clear()

    @Test
    fun `M remove all providers W clear() {multiple providers registered}`() {
        // Given
        FlagsClientManager.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClientManager.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        FlagsClientManager.clear()

        // Then
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isFalse()
        assertThat(FlagsClientManager.isRegistered(mockSecondSdkCore)).isFalse()
    }

    @Test
    fun `M do nothing W clear() {no providers registered}`() {
        // When
        FlagsClientManager.clear()

        // Then
        assertThat(FlagsClientManager.isRegistered(mockSdkCore)).isFalse()
    }

    // endregion
}
