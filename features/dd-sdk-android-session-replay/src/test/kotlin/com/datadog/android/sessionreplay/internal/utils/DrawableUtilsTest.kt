/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils.Companion.MAX_BITMAP_SIZE_IN_BYTES
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DrawableUtilsTest {
    private lateinit var testedDrawableUtils: DrawableUtils

    @Mock
    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    private lateinit var mockDrawable: Drawable

    @Mock
    private lateinit var mockBitmapWrapper: BitmapWrapper

    @Mock
    private lateinit var mockCanvasWrapper: CanvasWrapper

    @Mock
    private lateinit var mockBitmap: Bitmap

    @Mock
    private lateinit var mockCanvas: Canvas

    @BeforeEach
    fun setup() {
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(mockBitmap)
        whenever(mockBitmap.byteCount).thenReturn(MAX_BITMAP_SIZE_IN_BYTES + 1)
        whenever(mockCanvasWrapper.createCanvas(mockBitmap))
            .thenReturn(mockCanvas)
        testedDrawableUtils = DrawableUtils(
            bitmapWrapper = mockBitmapWrapper,
            canvasWrapper = mockCanvasWrapper
        )
    }

    // region createBitmap

    @Test
    fun `M set width to drawable intrinsic W createBitmapFromDrawableOfApproxSize() { no resizing }`() {
        // Given
        val requestedSize = 1000
        val edge = 10
        whenever(mockDrawable.intrinsicWidth).thenReturn(edge)
        whenever(mockDrawable.intrinsicHeight).thenReturn(edge)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            mockDrawable,
            mockDisplayMetrics,
            requestedSize
        )

        // Then
        verify(mockBitmapWrapper).createBitmap(
            displayMetrics = displayMetricsCaptor.capture(),
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(width).isEqualTo(edge)
        assertThat(height).isEqualTo(edge)
    }

    @Test
    fun `M set height higher W createBitmapFromDrawableOfApproxSize() { when resizing }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(900)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1000)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils
            .createBitmapOfApproxSizeFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        verify(mockBitmapWrapper).createBitmap(
            displayMetrics = displayMetricsCaptor.capture(),
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(height).isGreaterThanOrEqualTo(width)
    }

    @Test
    fun `M set width higher W createBitmapFromDrawableOfApproxSize() { when resizing }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1000)
        whenever(mockDrawable.intrinsicHeight).thenReturn(900)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        val result = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        verify(mockBitmapWrapper).createBitmap(
            displayMetrics = displayMetricsCaptor.capture(),
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any()
        )

        val width = argumentCaptor.firstValue
        val height = argumentCaptor.secondValue
        assertThat(width).isGreaterThanOrEqualTo(height)

        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)

        assertThat(result).isEqualTo(mockBitmap)
    }

    @Test
    fun `M return null W createBitmapFromDrawableOfApproxSize() { failed to create bmp }`() {
        // Given
        val edge = 200
        whenever(mockDrawable.intrinsicWidth).thenReturn(edge)
        whenever(mockDrawable.intrinsicHeight).thenReturn(edge)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(null)

        // When
        val result = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W createBitmapFromDrawableOfApproxSize() { failed to create canvas }`() {
        // Given
        val edge = 200
        whenever(mockDrawable.intrinsicWidth).thenReturn(edge)
        whenever(mockDrawable.intrinsicHeight).thenReturn(edge)
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(null)

        // When
        val result = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
