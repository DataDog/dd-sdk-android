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
internal class FlagsMapClientTest {

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
        FlagsClientMap.clear()
    }

    @AfterEach
    fun `tear down`() {
        FlagsClientMap.clear()
    }

    // region instance()

    @Test
    fun `M return NoOpFlagsClient W instance() {no client registered for SDK core}`() {
        // When
        val result = FlagsClientMap.instance(mockSdkCore)

        // Then
        assertThat(result).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M log warning W instance() {no client registered for SDK core}`() {
        // When
        val result = FlagsClientMap.instance(mockSdkCore)

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
    fun `M return registered client W instance() {client registered}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClientMap.instance(mockSdkCore)

        // Then
        assertThat(result).isEqualTo(mockFlagsClient)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return correct client W instance() {multiple clients registered for different SDK cores}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClientMap.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        val firstResult = FlagsClientMap.instance(mockSdkCore)
        val secondResult = FlagsClientMap.instance(mockSecondSdkCore)

        // Then
        assertThat(firstResult).isEqualTo(mockFlagsClient)
        assertThat(secondResult).isEqualTo(mockSecondFlagsClient)
    }

    // endregion

    // region registerIfAbsent()

    @Test
    fun `M return true W registerIfAbsent() {first registration}`() {
        // When
        val result = FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // Then
        assertThat(result).isTrue()
        assertThat(FlagsClientMap.instance(mockSdkCore)).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M return false W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        val result = FlagsClientMap.registerIfAbsent(mockSecondFlagsClient, mockSdkCore)

        // Then
        assertThat(result).isFalse()
        assertThat(FlagsClientMap.instance(mockSdkCore)).isEqualTo(mockFlagsClient)
    }

    @Test
    fun `M log warning W registerIfAbsent() {duplicate registration}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        FlagsClientMap.registerIfAbsent(mockSecondFlagsClient, mockSdkCore)

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
    fun `M register different clients W registerIfAbsent() {different SDK core instances}`() {
        // When
        val firstResult = FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)
        val secondResult = FlagsClientMap.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // Then
        assertThat(firstResult).isTrue()
        assertThat(secondResult).isTrue()
        assertThat(FlagsClientMap.instance(mockSdkCore)).isEqualTo(mockFlagsClient)
        assertThat(FlagsClientMap.instance(mockSecondSdkCore)).isEqualTo(mockSecondFlagsClient)
    }

    // endregion

    // region unregister()

    @Test
    fun `M remove client W unregister() {client registered for SDK core}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)

        // When
        FlagsClientMap.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClientMap.instance(mockSdkCore)).isInstanceOf(NoOpFlagsClient::class.java)
    }

    @Test
    fun `M do nothing W unregister() {no client registered for SDK core}`() {
        // When
        FlagsClientMap.unregister(mockSdkCore)

        // Then
    }

    @Test
    fun `M only remove specific client W unregister() {multiple clients registered for different SDK cores}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClientMap.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        FlagsClientMap.unregister(mockSdkCore)

        // Then
        assertThat(FlagsClientMap.instance(mockSecondSdkCore)).isEqualTo(mockSecondFlagsClient)
    }

    // endregion

    // region clear()

    @Test
    fun `M remove all clients W clear() {multiple clients registered across SDK cores}`() {
        // Given
        FlagsClientMap.registerIfAbsent(mockFlagsClient, mockSdkCore)
        FlagsClientMap.registerIfAbsent(mockSecondFlagsClient, mockSecondSdkCore)

        // When
        FlagsClientMap.clear()

        // Then
    }

    @Test
    fun `M do nothing W clear() {no clients registered across any SDK cores}`() {
        // When
        FlagsClientMap.clear()

        // Then
    }

    // endregion
}
