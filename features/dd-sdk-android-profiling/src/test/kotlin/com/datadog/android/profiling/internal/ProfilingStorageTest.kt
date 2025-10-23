/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.content.SharedPreferences
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ProfilingStorageTest {

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockPrefs: SharedPreferences

    @Mock
    lateinit var mockEditor: SharedPreferences.Editor

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSharedPreferences(any(), any())) doReturn mockPrefs
        whenever(mockPrefs.edit()) doReturn mockEditor
        whenever(mockEditor.remove(any())) doReturn mockEditor
        whenever(mockEditor.putBoolean(any(), any())) doReturn mockEditor

        // Reset the singleton
        val storageField = ProfilingStorage::class.java.getDeclaredField("sharedPreferencesStorage")
        storageField.isAccessible = true
        storageField.set(ProfilingStorage, null)
    }

    @Test
    fun `M set flag W setProfilingFlag()`() {
        // When
        ProfilingStorage.setProfilingFlag(mockContext)

        // Then
        verify(mockEditor).putBoolean("dd_profiling_enabled", true)
        verify(mockEditor).apply()
    }

    @Test
    fun `M return true W isProfilingEnabled() {flag is set}`() {
        // Given
        whenever(mockPrefs.getBoolean("dd_profiling_enabled", false)) doReturn true

        // When
        val isEnabled = ProfilingStorage.isProfilingEnabled(mockContext)

        // Then
        Assertions.assertThat(isEnabled).isTrue()
    }

    @Test
    fun `M return false W isProfilingEnabled() {flag is not set}`() {
        // Given
        whenever(mockPrefs.getBoolean("dd_profiling_enabled", false)) doReturn false

        // When
        val isEnabled = ProfilingStorage.isProfilingEnabled(mockContext)

        // Then
        Assertions.assertThat(isEnabled).isFalse()
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
                ProfilingStorage.setProfilingFlag(mockContext)
                latch.countDown()
            }.start()
        }
        latch.await(5, TimeUnit.SECONDS)

        // Then
        verify(mockContext, times(1)).getSharedPreferences(
            DATADOG_PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE
        )
    }

    companion object {
        internal const val DATADOG_PREFERENCES_FILE_NAME = "dd_prefs"
    }
}
