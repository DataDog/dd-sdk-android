/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import android.content.ContentResolver
import android.content.Context
import android.os.Handler
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import com.datadog.android.api.InternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultDisplayInfoProviderTest {
    private lateinit var testedProvider: DefaultDisplayInfoProvider

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockHandler: Handler

    @Mock
    lateinit var mockSettingsWrapper: SystemSettingsWrapper

    @Mock
    lateinit var mockContentResolver: ContentResolver

    @BeforeEach
    fun setup() {
        whenever(mockApplicationContext.contentResolver) doReturn mockContentResolver
    }

    @Test
    fun `M return normalized brightness W getBrightnessLevel() { specific brightness values }`() {
        val testCases = mapOf(
            0 to 0.0f,
            1 to 0.0f, // 1/255 = 0.004 rounds to 0.0
            127 to 0.5f, // 127/255 = 0.498 rounds to 0.5
            128 to 0.5f, // 128/255 = 0.502 rounds to 0.5
            254 to 1.0f, // 254/255 = 0.996 rounds to 1.0
            255 to 1.0f
        )

        testCases.forEach { (rawBrightness, expected) ->
            // Given - create fresh provider for each test case to avoid initialization caching
            whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn rawBrightness

            createDisplayInfoProvider()

            // When
            val result = testedProvider.getState().screenBrightness

            // Then
            assertThat(result)
                .`as`("Brightness $rawBrightness should normalize to $expected")
                .isEqualTo(expected)
        }
    }

    @Test
    fun `M verify rounding behavior W getBrightnessLevel() { test decimal precision }`() {
        // Given
        val rawBrightness = 191 // 191/255 = 0.749... should round to 0.7
        whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn rawBrightness

        createDisplayInfoProvider()

        // When
        val result = testedProvider.getState().screenBrightness

        // Then
        assertThat(result).isEqualTo(0.7f)
    }

    // region ContentObserver Tests - Integration Style

    @Test
    fun `M retain initial state W cleanup() then getState() { no re-initialization }`() {
        // Given - create provider
        whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn 128

        createDisplayInfoProvider()

        // When - cleanup (no re-initialization happens)
        testedProvider.cleanup()
        val displayInfo = testedProvider.getState()

        // Then - should retain the initial state from constructor
        assertThat(displayInfo.screenBrightness).isEqualTo(0.5f)
    }

    // endregion

    // region Cleanup Tests

    @Test
    fun `M unregister observer W cleanup() { after initialization }`() {
        // Given - create provider (initialization happens in constructor)
        whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn 128

        createDisplayInfoProvider()

        // When
        testedProvider.cleanup()

        // Then
        verify(mockContentResolver).unregisterContentObserver(any())
    }

    // endregion

    // region Error Handling Tests

    @Test
    fun `M handle system settings error W getState() { initialization error }`() {
        // Given
        whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn Integer.MIN_VALUE

        createDisplayInfoProvider()

        // When
        val displayInfo = testedProvider.getState()

        // Then - should handle gracefully with null brightness
        assertThat(displayInfo.screenBrightness).isNull()
    }

    // endregion

    private fun createDisplayInfoProvider() {
        testedProvider = DefaultDisplayInfoProvider(
            applicationContext = mockApplicationContext,
            internalLogger = mockInternalLogger,
            systemSettingsWrapper = mockSettingsWrapper,
            contentResolver = mockContentResolver,
            handler = mockHandler
        )
    }
}
