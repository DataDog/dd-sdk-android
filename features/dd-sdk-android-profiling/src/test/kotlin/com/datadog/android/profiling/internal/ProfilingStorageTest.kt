/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.content.SharedPreferences
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.never
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

    @StringForgery
    lateinit var fakeInstanceName: String

    private val otherInstanceName: String
        get() = "$fakeInstanceName.suffix"

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
        whenever(mockEditor.putString(any(), any())) doReturn mockEditor
    }

    @Test
    fun `M set flag W setProfilingFlag()`() {
        // When
        ProfilingStorage.setProfilingFlag(mockContext, fakeInstanceName)

        // Then
        verify(mockEditor).putString("dd_profiling_enabled", fakeInstanceName)
        verify(mockEditor).apply()
    }

    @Test
    fun `M return true W isProfilingEnabled() {flag is set}`() {
        // Given
        whenever(mockPrefs.getString("dd_profiling_enabled", null)) doReturn fakeInstanceName

        // When
        val actualInstanceName = ProfilingStorage.getProfilingEnabledInstanceName(mockContext)

        // Then
        assertThat(actualInstanceName).isEqualTo(fakeInstanceName)
    }

    @Test
    fun `M return false W isProfilingEnabled() {flag is not set}`() {
        // Given
        whenever(mockPrefs.getString("dd_profiling_enabled", null)) doReturn null

        // When
        val actualInstanceName = ProfilingStorage.getProfilingEnabledInstanceName(mockContext)

        // Then
        assertThat(actualInstanceName).isNull()
    }

    @Test
    fun `M remove flag W removeProfilingFlag(){ same instance name }`() {
        // Given
        whenever(mockPrefs.getString("dd_profiling_enabled", null)).doReturn(fakeInstanceName)

        // When
        ProfilingStorage.removeProfilingFlag(mockContext, fakeInstanceName)

        // Then
        verify(mockEditor).remove("dd_profiling_enabled")
        verify(mockEditor).apply()
    }

    @Test
    fun `M remove flag W removeProfilingFlag(){ different instance name }`() {
        // Given
        ProfilingStorage.setProfilingFlag(mockContext, fakeInstanceName)

        // When
        ProfilingStorage.removeProfilingFlag(mockContext, otherInstanceName)

        // Then
        verify(mockEditor, never()).remove("dd_profiling_enabled")
    }

    @Test
    fun `M be thread-safe W calling from multiple threads`() {
        // Given
        val latch = CountDownLatch(10)

        // When
        repeat(10) {
            Thread {
                ProfilingStorage.setProfilingFlag(mockContext, fakeInstanceName)
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

    companion object {
        internal const val DATADOG_PREFERENCES_FILE_NAME = "dd_prefs"
    }
}
