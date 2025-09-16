/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
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
internal class FlagsClientTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsProvider: FlagsProvider

    @Mock
    lateinit var mockSecondSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockSecondFlagsProvider: FlagsProvider

    private lateinit var fakeSdkCoreName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSdkCoreName = forge.anAlphabeticalString()
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.name) doReturn fakeSdkCoreName
        whenever(mockSecondSdkCore.internalLogger) doReturn mockInternalLogger

        // Clean state before each test
        FlagsClient.clear()
    }

    @AfterEach
    fun `tear down`() {
        FlagsClient.clear()
    }

    // region isRegistered()

    @Test
    fun `M return false W isRegistered() {no provider registered}`() {
        // When
        val result = FlagsClient.isRegistered(mockSdkCore)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isRegistered() {provider registered}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)

        // When
        val result = FlagsClient.isRegistered(mockSdkCore)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false for different SDK W isRegistered() {provider registered for other SDK}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)

        // When
        val result = FlagsClient.isRegistered(mockSecondSdkCore)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region get()

    @Test
    fun `M return NoOpFlagsProvider W get() {no provider registered}`() {
        // When
        val result = FlagsClient.get(mockSdkCore)

        // Then
        assertThat(result).isInstanceOf(NoOpFlagsProvider::class.java)
    }

    @Test
    fun `M log warning W get() {no provider registered}`() {
        // When
        val result = FlagsClient.get(mockSdkCore)

        // Then
        assertThat(result).isInstanceOf(NoOpFlagsProvider::class.java)
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
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)

        // When
        val result = FlagsClient.get(mockSdkCore)

        // Then
        assertThat(result).isEqualTo(mockFlagsProvider)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return correct provider W get() {multiple providers registered}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)
        FlagsClient.registerIfAbsent(mockSecondFlagsProvider, mockSecondSdkCore)

        // When
        val firstResult = FlagsClient.get(mockSdkCore)
        val secondResult = FlagsClient.get(mockSecondSdkCore)

        // Then
        assertThat(firstResult).isEqualTo(mockFlagsProvider)
        assertThat(secondResult).isEqualTo(mockSecondFlagsProvider)
    }

    // endregion

    // region registerIfAbsent()

    @Test
    fun `M return true W registerIfAbsent() {first registration}`() {
        // When
        val result = FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)

        // Then
        assertThat(result).isTrue()
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isTrue()
        assertThat(FlagsClient.get(mockSdkCore)).isEqualTo(mockFlagsProvider)
    }

    @Test
    fun `M return false W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)

        // When
        val result = FlagsClient.registerIfAbsent(mockSecondFlagsProvider, mockSdkCore)

        // Then
        assertThat(result).isFalse()
        assertThat(FlagsClient.get(mockSdkCore)).isEqualTo(mockFlagsProvider)
    }

    @Test
    fun `M log warning W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)

        // When
        FlagsClient.registerIfAbsent(mockSecondFlagsProvider, mockSdkCore)

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
        val firstResult = FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)
        val secondResult = FlagsClient.registerIfAbsent(mockSecondFlagsProvider, mockSecondSdkCore)

        // Then
        assertThat(firstResult).isTrue()
        assertThat(secondResult).isTrue()
        assertThat(FlagsClient.get(mockSdkCore)).isEqualTo(mockFlagsProvider)
        assertThat(FlagsClient.get(mockSecondSdkCore)).isEqualTo(mockSecondFlagsProvider)
    }

    // endregion

    // region unregister()

    @Test
    fun `M remove provider W unregister() {provider registered}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isTrue()

        // When
        FlagsClient.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isFalse()
        assertThat(FlagsClient.get(mockSdkCore)).isInstanceOf(NoOpFlagsProvider::class.java)
    }

    @Test
    fun `M do nothing W unregister() {no provider registered}`() {
        // When
        FlagsClient.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isFalse()
    }

    @Test
    fun `M only remove specific provider W unregister() {multiple providers registered}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)
        FlagsClient.registerIfAbsent(mockSecondFlagsProvider, mockSecondSdkCore)

        // When
        FlagsClient.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isFalse()
        assertThat(FlagsClient.isRegistered(mockSecondSdkCore)).isTrue()
        assertThat(FlagsClient.get(mockSecondSdkCore)).isEqualTo(mockSecondFlagsProvider)
    }

    // endregion

    // region clear()

    @Test
    fun `M remove all providers W clear() {multiple providers registered}`() {
        // Given
        FlagsClient.registerIfAbsent(mockFlagsProvider, mockSdkCore)
        FlagsClient.registerIfAbsent(mockSecondFlagsProvider, mockSecondSdkCore)

        // When
        FlagsClient.clear()

        // Then
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isFalse()
        assertThat(FlagsClient.isRegistered(mockSecondSdkCore)).isFalse()
    }

    @Test
    fun `M do nothing W clear() {no providers registered}`() {
        // When
        FlagsClient.clear()

        // Then
        assertThat(FlagsClient.isRegistered(mockSdkCore)).isFalse()
    }

    // endregion
}
