/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import android.content.Context
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultDisplayInfoProviderTest {
    private lateinit var testedProvider: DefaultDisplayInfoProvider

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockSettingsWrapper: SystemSettingsWrapper

    @BeforeEach
    fun setUp() {
        testedProvider = DefaultDisplayInfoProvider(
            applicationContext = mockApplicationContext,
            systemSettingsWrapper = mockSettingsWrapper
        )
    }

    @Test
    fun `M return brightness level W getBrightnessLevel() { valid brightness value }`() {
        // Given
        val rawBrightness = 128
        val expectedBrightness = 0.5f // 128/255 = 0.502 -> rounded to 0.5
        whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn rawBrightness

        // When
        val result = testedProvider.getBrightnessLevel()

        // Then
        assertThat(result).isEqualTo(expectedBrightness)
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
            // Given
            whenever(mockSettingsWrapper.getInt(SCREEN_BRIGHTNESS)) doReturn rawBrightness

            // When
            val result = testedProvider.getBrightnessLevel()

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

        // When
        val result = testedProvider.getBrightnessLevel()

        // Then
        assertThat(result).isEqualTo(0.7f)
    }
}
