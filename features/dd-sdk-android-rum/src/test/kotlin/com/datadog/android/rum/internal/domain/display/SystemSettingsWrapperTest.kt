/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SystemSettingsWrapperTest {

    private lateinit var testedWrapper: SystemSettingsWrapper

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockContentResolver: ContentResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun setUp() {
        whenever(mockApplicationContext.contentResolver) doReturn mockContentResolver

        testedWrapper = SystemSettingsWrapper(
            applicationContext = mockApplicationContext,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M return setting value W getInt() { setting exists }`() {
        // Given
        val fakeName = Settings.System.SCREEN_BRIGHTNESS
        val fakeValue = 128

        Mockito.mockStatic(Settings.System::class.java).use { mockedSettings ->
            mockedSettings.`when`<Int> {
                Settings.System.getInt(mockContentResolver, fakeName)
            }.thenReturn(fakeValue)

            // When
            val result = testedWrapper.getInt(fakeName)

            // Then
            assertThat(result).isEqualTo(fakeValue)
        }
    }

    @Test
    fun `M return MIN_VALUE and log W getInt() { setting not found }`() {
        // Given
        val fakeName = Settings.System.SCREEN_BRIGHTNESS

        Mockito.mockStatic(Settings.System::class.java).use { mockedSettings ->
            mockedSettings.`when`<Int> {
                Settings.System.getInt(mockContentResolver, fakeName)
            }.thenThrow(Settings.SettingNotFoundException(fakeName))

            // When
            val result = testedWrapper.getInt(fakeName)

            // Then
            assertThat(result).isEqualTo(Integer.MIN_VALUE)
            mockInternalLogger.verifyLog(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                message = "Problem retrieving system value for $fakeName",
                throwableClass = Settings.SettingNotFoundException::class.java
            )
        }
    }

    @Test
    fun `M return MIN_VALUE and log W getInt() { RuntimeException thrown unparceling MemoryIntArray }`() {
        // Given
        val fakeName = Settings.System.SCREEN_BRIGHTNESS

        Mockito.mockStatic(Settings.System::class.java).use { mockedSettings ->
            mockedSettings.`when`<Int> {
                Settings.System.getInt(mockContentResolver, fakeName)
            }.thenThrow(IllegalArgumentException("Error unparceling MemoryIntArray"))

            // When
            val result = testedWrapper.getInt(fakeName)

            // Then
            assertThat(result).isEqualTo(Integer.MIN_VALUE)
            mockInternalLogger.verifyLog(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                message = "Problem retrieving system value for $fakeName",
                throwableClass = IllegalArgumentException::class.java
            )
        }
    }

    @Test
    fun `M return MIN_VALUE and log W getInt() { SecurityException thrown by ContentProvider }`() {
        // Given
        val fakeName = Settings.System.SCREEN_BRIGHTNESS

        Mockito.mockStatic(Settings.System::class.java).use { mockedSettings ->
            mockedSettings.`when`<Int> {
                Settings.System.getInt(mockContentResolver, fakeName)
            }.thenThrow(SecurityException("permission denied"))

            // When
            val result = testedWrapper.getInt(fakeName)

            // Then
            assertThat(result).isEqualTo(Integer.MIN_VALUE)
            mockInternalLogger.verifyLog(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                message = "Problem retrieving system value for $fakeName",
                throwableClass = SecurityException::class.java
            )
        }
    }
}
