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
        whenever(mockEditor.putStringSet(any(), any())) doReturn mockEditor
    }

    @Test
    fun `M add flag W addProfilingFlag()`() {
        // When
        ProfilingStorage.addProfilingFlag(mockContext, fakeInstanceName)

        // Then
        verify(mockEditor).putStringSet("dd_profiling_enabled", setOf(fakeInstanceName))
        verify(mockEditor).apply()
    }

    @Test
    fun `M return true W isProfilingEnabled() {flag is set}`() {
        // Given
        whenever(mockPrefs.getStringSet("dd_profiling_enabled", emptySet())) doReturn setOf(
            fakeInstanceName
        )

        // When
        val actualInstanceName = ProfilingStorage.getProfilingEnabledInstanceNames(mockContext)

        // Then
        assertThat(actualInstanceName).isEqualTo(setOf(fakeInstanceName))
    }

    @Test
    fun `M return false W isProfilingEnabled() {flag is not set}`() {
        // Given
        whenever(mockPrefs.getString("dd_profiling_enabled", null)) doReturn null

        // When
        val actualInstanceName = ProfilingStorage.getProfilingEnabledInstanceNames(mockContext)

        // Then
        assertThat(actualInstanceName).isEqualTo(emptySet<String>())
    }

    @Test
    fun `M remove flag W removeProfilingFlag(){ same instance name }`() {
        // Given
        whenever(
            mockPrefs
                .getStringSet("dd_profiling_enabled", emptySet())
        ).doReturn(setOf(fakeInstanceName))

        // When
        ProfilingStorage.removeProfilingFlag(mockContext, setOf(fakeInstanceName))

        // Then
        verify(mockEditor).putStringSet("dd_profiling_enabled", emptySet<String>())
        verify(mockEditor).apply()
    }

    @Test
    fun `M remove flag W removeProfilingFlag(){ different instance name }`() {
        // Given
        whenever(
            mockPrefs
                .getStringSet("dd_profiling_enabled", null)
        ) doReturn setOf(
            fakeInstanceName
        )

        // When
        ProfilingStorage.removeProfilingFlag(mockContext, setOf(otherInstanceName))

        // Then
        verify(mockEditor, never()).putStringSet("dd_profiling_enabled", emptySet<String>())
    }

    @Test
    fun `M be thread-safe W calling from multiple threads`() {
        // Given
        val latch = CountDownLatch(10)

        // When
        repeat(10) {
            Thread {
                ProfilingStorage.addProfilingFlag(mockContext, fakeInstanceName)
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
    fun `M preserved the existing instance name W adding a new instance name`() {
        // Given
        whenever(
            mockPrefs
                .getStringSet("dd_profiling_enabled", emptySet<String>())
        ) doReturn setOf(
            otherInstanceName
        )

        // When
        ProfilingStorage.addProfilingFlag(mockContext, fakeInstanceName)

        // Then
        verify(mockEditor).putStringSet(
            "dd_profiling_enabled",
            setOf(fakeInstanceName, otherInstanceName)
        )
        verify(mockEditor).apply()
    }

    companion object {
        internal const val DATADOG_PREFERENCES_FILE_NAME = "dd_prefs"
    }
}
