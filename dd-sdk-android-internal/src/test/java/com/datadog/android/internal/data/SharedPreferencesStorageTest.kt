/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.data

import android.content.Context
import android.content.SharedPreferences
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SharedPreferencesStorageTest {

    lateinit var testedStorage: SharedPreferencesStorage

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
        whenever(mockEditor.putInt(any(), any())) doReturn mockEditor
        whenever(mockEditor.putString(any(), any())) doReturn mockEditor
        whenever(mockEditor.putStringSet(any(), any())) doReturn mockEditor
        whenever(mockEditor.clear()) doReturn mockEditor

        testedStorage = SharedPreferencesStorage(mockContext)
    }

    @Test
    fun `M use proper file name W instantiating`() {
        // Then
        verify(mockContext).getSharedPreferences(
            SharedPreferencesStorage.DATADOG_PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE
        )
    }

    @Test
    fun `M persist data W putInt`(@StringForgery fakeKey: String, @IntForgery fakeValue: Int) {
        // When
        testedStorage.putInt(fakeKey, fakeValue)

        // Then
        verify(mockEditor).putInt(fakeKey, fakeValue)
        verify(mockEditor).apply()
    }

    @Test
    fun `M read data W getInt`(
        @StringForgery fakeKey: String,
        @IntForgery fakeValue: Int,
        @IntForgery fakeDefaultValue: Int
    ) {
        // Given
        whenever(mockPrefs.getInt(fakeKey, fakeDefaultValue)) doReturn fakeValue

        // When
        val result = testedStorage.getInt(fakeKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeValue)
    }

    @Test
    fun `M persist data W putString`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String
    ) {
        // When
        testedStorage.putString(fakeKey, fakeValue)

        // Then
        verify(mockEditor).putString(fakeKey, fakeValue)
        verify(mockEditor).apply()
    }

    @Test
    fun `M read data W getString`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String
    ) {
        // Given
        whenever(mockPrefs.getString(fakeKey, null)) doReturn fakeValue

        // When
        val result = testedStorage.getString(fakeKey)

        // Then
        assertThat(result).isEqualTo(fakeValue)
    }

    @Test
    fun `M persist data W putBoolean`(
        @StringForgery fakeKey: String,
        @BoolForgery fakeValue: Boolean
    ) {
        // When
        testedStorage.putBoolean(fakeKey, fakeValue)

        // Then
        verify(mockEditor).putBoolean(fakeKey, fakeValue)
        verify(mockEditor).apply()
    }

    @Test
    fun `M read data W getBoolean`(
        @StringForgery fakeKey: String,
        @BoolForgery fakeValue: Boolean,
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        whenever(mockPrefs.getBoolean(fakeKey, fakeDefaultValue)) doReturn fakeValue

        // When
        val result = testedStorage.getBoolean(fakeKey, fakeDefaultValue)

        // Then
        assertThat(result).isEqualTo(fakeValue)
    }

    @Test
    fun `M persist data W putStringSet`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String
    ) {
        // When
        testedStorage.putStringSet(fakeKey, setOf(fakeValue))

        // Then
        verify(mockEditor).putStringSet(fakeKey, setOf(fakeValue))
        verify(mockEditor).apply()
    }

    @Test
    fun `M read data W getStringSet`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String
    ) {
        // Given
        whenever(mockPrefs.getStringSet(fakeKey, emptySet())) doReturn setOf(fakeValue)

        // When
        val result = testedStorage.getStringSet(fakeKey, emptySet())

        // Then
        assertThat(result).isEqualTo(setOf(fakeValue))
    }

    @Test
    fun `M remove data W remove`() {
        // Given
        val key = "key"

        // When
        testedStorage.remove(key)

        // Then
        verify(mockEditor).remove(key)
        verify(mockEditor).apply()
    }

    @Test
    fun `M clear all data W clear`() {
        // When
        testedStorage.clear()

        // Then
        verify(mockEditor).clear()
        verify(mockEditor).apply()
    }

    @Test
    fun `M not throw ClassCastException W getString {data type mismatch}`(
        @StringForgery fakeKey: String,
        @StringForgery fakeDefaultValue: String
    ) {
        // Given
        whenever(
            mockPrefs.getString(fakeKey, fakeDefaultValue)
        ) doThrow ClassCastException("Class cast exception")

        assertDoesNotThrow {
            // When
            val value = testedStorage.getString(fakeKey, fakeDefaultValue)

            // Then
            assertThat(value).isEqualTo(fakeDefaultValue)
        }
    }

    @Test
    fun `M not throw ClassCastException W getBool {data type mismatch}`(
        @StringForgery fakeKey: String,
        @BoolForgery fakeDefaultValue: Boolean
    ) {
        // Given
        whenever(
            mockPrefs.getBoolean(fakeKey, fakeDefaultValue)
        ) doThrow ClassCastException("Class cast exception")

        assertDoesNotThrow {
            // When
            val value = testedStorage.getBoolean(fakeKey, fakeDefaultValue)

            // Then
            assertThat(value).isEqualTo(fakeDefaultValue)
        }
    }

    @Test
    fun `M not throw ClassCastException W getInt {data type mismatch}`(
        @StringForgery fakeKey: String,
        @IntForgery fakeDefaultValue: Int
    ) {
        // Given
        whenever(
            mockPrefs.getInt(fakeKey, fakeDefaultValue)
        ) doThrow ClassCastException("Class cast exception")

        assertDoesNotThrow {
            // When
            val value = testedStorage.getInt(fakeKey, fakeDefaultValue)

            // Then
            assertThat(value).isEqualTo(fakeDefaultValue)
        }
    }

    @Test
    fun `M not throw ClassCastException W getStringSet {data type mismatch}`(
        forge: Forge
    ) {
        val fakeKey = forge.aString()
        val fakeDefaultValue = forge.aList { forge.aString() }.toSet()
        // Given
        whenever(
            mockPrefs.getStringSet(fakeKey, fakeDefaultValue)
        ) doThrow ClassCastException("Class cast exception")

        assertDoesNotThrow {
            // When
            val value = testedStorage.getStringSet(fakeKey, fakeDefaultValue)

            // Then
            assertThat(value).isEqualTo(fakeDefaultValue)
        }
    }
}
