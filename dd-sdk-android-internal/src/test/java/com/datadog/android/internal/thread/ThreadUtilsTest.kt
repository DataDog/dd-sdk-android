/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.thread

import android.os.Looper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ThreadUtilsTest {

    @Test
    fun `M return true W isMainThread() {current looper is main looper}`() {
        // Given
        val mockMainLooper = mock<Looper>()

        Mockito.mockStatic(Looper::class.java).use { mockedLooper ->
            // When
            mockedLooper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mockMainLooper)
            mockedLooper.`when`<Looper> { Looper.myLooper() }.thenReturn(mockMainLooper)

            // Then
            assertThat(isMainThread()).isTrue()
        }
    }

    @Test
    fun `M return false W isMainThread() {current looper is not main looper}`() {
        // Given
        val mockMainLooper = mock<Looper>()
        val mockOtherLooper = mock<Looper>()

        Mockito.mockStatic(Looper::class.java).use { mockedLooper ->
            // When
            mockedLooper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mockMainLooper)
            mockedLooper.`when`<Looper> { Looper.myLooper() }.thenReturn(mockOtherLooper)

            // Then
            assertThat(isMainThread()).isFalse()
        }
    }

    @Test
    fun `M return false W isMainThread() {current looper is null}`() {
        // Given
        val mockMainLooper = mock<Looper>()

        Mockito.mockStatic(Looper::class.java).use { mockedLooper ->
            // When
            mockedLooper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mockMainLooper)
            mockedLooper.`when`<Looper> { Looper.myLooper() }.thenReturn(null)

            // Then
            assertThat(isMainThread()).isFalse()
        }
    }
}
