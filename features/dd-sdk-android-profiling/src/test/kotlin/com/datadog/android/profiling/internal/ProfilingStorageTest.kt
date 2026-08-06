/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.content.SharedPreferences
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ProfilingStorageTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockPrefs: SharedPreferences

    @Mock
    lateinit var mockEditor: SharedPreferences.Editor

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BeforeEach
    fun `set up`() {
        // Reset the singleton
        val storageField = ProfilingStorage::class.java.getDeclaredField("sharedPreferencesStorage")
        storageField.isAccessible = true
        storageField.set(ProfilingStorage, null)
        whenever(mockContext.getSharedPreferences(any(), any())) doReturn mockPrefs
        whenever(mockPrefs.edit()) doReturn mockEditor
        whenever(mockEditor.remove(any())) doReturn mockEditor
        whenever(mockEditor.putBoolean(any(), any())) doReturn mockEditor
        whenever(mockEditor.putFloat(any(), any())) doReturn mockEditor
        whenever(mockEditor.putInt(any(), any())) doReturn mockEditor
        whenever(mockEditor.putString(any(), any())) doReturn mockEditor
    }

    @Test
    fun `M add flag W addProfilingFlag()`() {
        // When
        ProfilingStorage.addProfilingFlag(mockContext)

        // Then
        verify(mockEditor).putBoolean("dd_profiling_enabled", true)
        verify(mockEditor).apply()
    }

    @Test
    fun `M return true W isProfilingEnabled() {flag is set}`() {
        // Given
        whenever(mockPrefs.getBoolean("dd_profiling_enabled", false)) doReturn true

        // When
        val actual = ProfilingStorage.isProfilingEnabled(mockContext)

        // Then
        assertThat(actual).isTrue
    }

    @Test
    fun `M return false W isProfilingEnabled() {flag is not set}`() {
        // Given
        whenever(mockPrefs.getBoolean("dd_profiling_enabled", false)) doReturn false

        // When
        val actual = ProfilingStorage.isProfilingEnabled(mockContext)

        // Then
        assertThat(actual).isFalse
    }

    @Test
    fun `M return false W isProfilingEnabled() {stale StringSet value stored}`() {
        // Given
        // An app upgrading from a version that stored a StringSet under the same key would hit a
        // ClassCastException on getBoolean; SharedPreferencesStorage swallows it and returns default.
        whenever(mockPrefs.getBoolean("dd_profiling_enabled", false)) doThrow ClassCastException()

        // When
        val actual = ProfilingStorage.isProfilingEnabled(mockContext)

        // Then
        assertThat(actual).isFalse
    }

    @Test
    fun `M remove flag W removeProfilingFlag()`() {
        // When
        ProfilingStorage.removeProfilingFlag(mockContext)

        // Then
        verify(mockEditor).remove("dd_profiling_enabled")
        verify(mockEditor).apply()
    }

    @Test
    fun `M be thread-safe W calling from multiple threads`() {
        // Given
        val latch = CountDownLatch(10)

        // When
        repeat(10) {
            Thread {
                ProfilingStorage.addProfilingFlag(mockContext)
                latch.countDown()
            }.start()
        }
        latch.await(5, TimeUnit.SECONDS)

        // Then
        verify(mockContext).getSharedPreferences(
            DATADOG_PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE
        )
    }

    @Test
    fun `M set sample rate W setSampleRate()`() {
        // When
        ProfilingStorage.setSampleRate(mockContext, fakeSampleRate)

        // Then
        verify(mockEditor).putFloat("dd_profiling_sample_rate", fakeSampleRate)
        verify(mockEditor).apply()
    }

    @Test
    fun `M get sample rate W getSampleRate() {rate is set}`() {
        // Given
        whenever(mockPrefs.getFloat("dd_profiling_sample_rate", -1f)) doReturn fakeSampleRate

        // When
        val actualSampleRate = ProfilingStorage.getSampleRate(mockContext)

        // Then
        assertThat(actualSampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M get default sample rate W getSampleRate() {rate is not set}`() {
        // Given
        whenever(mockPrefs.getFloat("dd_profiling_sample_rate", 0f)) doReturn 0f

        // When
        val actualSampleRate = ProfilingStorage.getSampleRate(mockContext)

        // Then
        assertThat(actualSampleRate).isEqualTo(0f)
    }

    companion object {
        internal const val DATADOG_PREFERENCES_FILE_NAME = "dd_prefs"
    }
}
