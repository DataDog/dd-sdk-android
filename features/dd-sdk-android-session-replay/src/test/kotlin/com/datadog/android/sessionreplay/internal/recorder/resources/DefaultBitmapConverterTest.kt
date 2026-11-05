/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.graphics.Canvas
import com.datadog.android.api.InternalLogger
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultBitmapConverterTest {

    @Mock
    private lateinit var mockLogger: InternalLogger

    @Mock
    private lateinit var mockBitmap: Bitmap

    private lateinit var testedConverter: Alpha8BitmapConverter

    @IntForgery(min = 1, max = 100)
    var fakeWidth: Int = 0

    @IntForgery(min = 1, max = 100)
    var fakeHeight: Int = 0

    @BeforeEach
    fun setup() {
        testedConverter = Alpha8BitmapConverter(mockLogger)
    }

    @Test
    fun `M release converted pixels before propagating allocation failure W convertAlpha8BitmapToArgb8888`() {
        // Given
        val fakeFailure = OutOfMemoryError()
        val mockConvertedBitmap = mock<Bitmap>()
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(fakeHeight)
        mockStatic(Bitmap::class.java).use { mockPlatform ->
            mockPlatform.`when`<Bitmap> {
                Bitmap.createBitmap(fakeWidth, fakeHeight, Bitmap.Config.ARGB_8888)
            }.thenReturn(mockConvertedBitmap)
            mockConstruction(Canvas::class.java) { canvas, _ ->
                whenever(canvas.drawColor(any())).thenThrow(fakeFailure)
            }.use {
                // When
                val actualFailure = assertThrows<OutOfMemoryError> {
                    testedConverter.convertAlpha8BitmapToArgb8888(mockBitmap)
                }

                // Then
                assertThat(actualFailure).isSameAs(fakeFailure)
                verify(mockConvertedBitmap).recycle()
                verifyNoInteractions(mockLogger)
            }
        }
    }

    @Test
    fun `M return null W convertAlpha8BitmapToArgb8888 { bitmap is recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        val result = testedConverter.convertAlpha8BitmapToArgb8888(mockBitmap)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W convertAlpha8BitmapToArgb8888 { bitmap has zero width }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(0)
        whenever(mockBitmap.height).thenReturn(fakeHeight)

        // When
        val result = testedConverter.convertAlpha8BitmapToArgb8888(mockBitmap)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W convertAlpha8BitmapToArgb8888 { bitmap has zero height }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(0)

        // When
        val result = testedConverter.convertAlpha8BitmapToArgb8888(mockBitmap)

        // Then
        assertThat(result).isNull()
    }
}
