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
import android.widget.ImageView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.base64.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.mock
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
    private lateinit var mockBitmapPool: BitmapPool

    @Mock
    private lateinit var mockDrawable: Drawable

    @Mock
    private lateinit var mockBitmapWrapper: BitmapWrapper

    @Mock
    private lateinit var mockCanvasWrapper: CanvasWrapper

    @Mock
    private lateinit var mockCanvas: Canvas

    @Mock
    private lateinit var mockBitmap: Bitmap

    @Mock
    private lateinit var mockConfig: Bitmap.Config

    @BeforeEach
    fun setup() {
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(mockBitmap)
        whenever(mockCanvasWrapper.createCanvas(mockBitmap))
            .thenReturn(mockCanvas)
        whenever(mockBitmap.config).thenReturn(mockConfig)
        whenever(mockBitmapPool.getBitmapByProperties(any(), any(), any())).thenReturn(null)

        testedDrawableUtils = DrawableUtils(
            bitmapWrapper = mockBitmapWrapper,
            canvasWrapper = mockCanvasWrapper,
            bitmapPool = mockBitmapPool
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
            requestedSize,
            mockConfig
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
    fun `M set height higher W createBitmapFromDrawableOfApproxSize() { when resizing }`(
        @IntForgery(min = 0, max = 500) viewWidth: Int,
        @IntForgery(min = 501, max = 1000) viewHeight: Int
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            mockDrawable,
            mockDisplayMetrics
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
        assertThat(height).isGreaterThanOrEqualTo(width)
    }

    @Test
    fun `M set width higher W createBitmapFromDrawableOfApproxSize() { when resizing }`(
        @IntForgery(min = 501, max = 1000) viewWidth: Int,
        @IntForgery(min = 0, max = 500) viewHeight: Int
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        val result = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            mockDrawable,
            mockDisplayMetrics,
            config = mockConfig
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
        val result = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            mockDrawable,
            mockDisplayMetrics,
            config = mockConfig
        )

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
        val result = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            mockDrawable,
            mockDisplayMetrics,
            config = mockConfig
        )

        // Then
        assertThat(result).isNull()
    }

    // endregion

    fun `M use bitmap from pool W createBitmapFromDrawable() { exists in pool }`(
        @IntForgery(min = 1, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 1000) viewHeight: Int
    ) {
        // Given
        val mockBitmapFromPool: Bitmap = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)
        whenever(mockBitmapPool.getBitmapByProperties(any(), any(), any()))
            .thenReturn(mockBitmapFromPool)

        // When
        val actualBitmap = testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            mockDrawable,
            mockDisplayMetrics,
            config = mockConfig
        )

        // Then
        assertThat(actualBitmap).isEqualTo(mockBitmapFromPool)
    }

    @Test
    fun `M return drawable width and height W getDrawableScaledDimensions() { no scaleType }`(
        @Mock mockImageView: ImageView,
        @Mock mockDrawable: Drawable,
        @IntForgery(min = 1, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 1000) viewHeight: Int,
        @IntForgery(min = 1, max = 1000) drawableWidth: Int,
        @IntForgery(min = 1, max = 1000) drawableHeight: Int,
        @FloatForgery(0.1f, 3f) fakeDensity: Float
    ) {
        // Given
        whenever(mockImageView.scaleType).thenReturn(null)
        whenever(mockImageView.width).thenReturn(viewWidth)
        whenever(mockImageView.height).thenReturn(viewHeight)
        whenever(mockDrawable.intrinsicWidth).thenReturn(drawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(drawableHeight)

        val expectedWidth = drawableWidth.densityNormalized(fakeDensity).toLong()
        val expectedHeight = drawableHeight.densityNormalized(fakeDensity).toLong()

        // When
        val result = testedDrawableUtils.getDrawableScaledDimensions(
            mockImageView,
            mockDrawable,
            fakeDensity
        )

        // Then
        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
    }

    @Test
    fun `M return drawable width and height W getDrawableScaledDimensions() { unsupported scaleType }`(
        @Mock mockImageView: ImageView,
        @Mock mockDrawable: Drawable,
        @IntForgery(min = 1, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 1000) viewHeight: Int,
        @IntForgery(min = 1, max = 1000) drawableWidth: Int,
        @IntForgery(min = 1, max = 1000) drawableHeight: Int,
        @FloatForgery(0.1f, 3f) fakeDensity: Float
    ) {
        // Given
        whenever(mockImageView.scaleType).thenReturn(ImageView.ScaleType.FIT_START)
        whenever(mockImageView.width).thenReturn(viewWidth)
        whenever(mockImageView.height).thenReturn(viewHeight)
        whenever(mockDrawable.intrinsicWidth).thenReturn(drawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(drawableHeight)

        val expectedWidth = drawableWidth.densityNormalized(fakeDensity).toLong()
        val expectedHeight = drawableHeight.densityNormalized(fakeDensity).toLong()

        // When
        val result = testedDrawableUtils.getDrawableScaledDimensions(
            mockImageView,
            mockDrawable,
            fakeDensity
        )

        // Then
        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
    }

    @Test
    fun `M return view width and height W getDrawableScaledDimensions() { FitXY }`(
        @Mock mockImageView: ImageView,
        @Mock mockDrawable: Drawable,
        @IntForgery(min = 1, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 1000) viewHeight: Int,
        @IntForgery(min = 1, max = 1000) drawableWidth: Int,
        @IntForgery(min = 1, max = 1000) drawableHeight: Int,
        @FloatForgery(0.1f, 3f) density: Float
    ) {
        // Given
        whenever(mockImageView.scaleType).thenReturn(ImageView.ScaleType.FIT_XY)
        whenever(mockImageView.width).thenReturn(viewWidth)
        whenever(mockImageView.height).thenReturn(viewHeight)
        whenever(mockDrawable.intrinsicWidth).thenReturn(drawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(drawableHeight)

        val expectedWidth = viewWidth.densityNormalized(density).toLong()
        val expectedHeight = viewHeight.densityNormalized(density).toLong()

        // When
        val result = testedDrawableUtils.getDrawableScaledDimensions(
            mockImageView,
            mockDrawable,
            density
        )

        // Then
        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
    }

    @Test
    fun `M return correct dimensions W getDrawableScaledDimensions() { CenterCrop, width gt height }`(
        @Mock mockImageView: ImageView,
        @Mock mockDrawable: Drawable,
        @IntForgery(min = 501, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 500) viewHeight: Int,
        @IntForgery(min = 1, max = 500) drawableWidth: Int,
        @IntForgery(min = 501, max = 1000) drawableHeight: Int,
        @FloatForgery(0.1f, 3f) fakeDensity: Float
    ) {
        // Given
        whenever(mockImageView.scaleType).thenReturn(ImageView.ScaleType.CENTER_CROP)
        whenever(mockImageView.width).thenReturn(viewWidth)
        whenever(mockImageView.height).thenReturn(viewHeight)
        whenever(mockDrawable.intrinsicWidth).thenReturn(drawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(drawableHeight)

        val viewHeightNormalized = viewHeight.densityNormalized(fakeDensity).toLong()
        val drawableWidthNormalized = drawableWidth.densityNormalized(fakeDensity).toLong()
        val drawableHeightNormalized = drawableHeight.densityNormalized(fakeDensity).toLong()

        val expectedWidth = (viewHeightNormalized * drawableWidthNormalized) / drawableHeightNormalized
        val expectedHeight = viewHeightNormalized

        // When
        val result = testedDrawableUtils.getDrawableScaledDimensions(
            mockImageView,
            mockDrawable,
            fakeDensity
        )

        // Then
        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
    }

    @Test
    fun `M return correct dimensions W getDrawableScaledDimensions() { CenterCrop, width lt height }`(
        @Mock mockImageView: ImageView,
        @Mock mockDrawable: Drawable,
        @IntForgery(min = 1, max = 500) viewWidth: Int,
        @IntForgery(min = 501, max = 1000) viewHeight: Int,
        @IntForgery(min = 501, max = 1000) drawableWidth: Int,
        @IntForgery(min = 1, max = 500) drawableHeight: Int,
        @FloatForgery(0.1f, 3f) fakeDensity: Float
    ) {
        // Given
        whenever(mockImageView.scaleType).thenReturn(ImageView.ScaleType.CENTER_CROP)
        whenever(mockImageView.width).thenReturn(viewWidth)
        whenever(mockImageView.height).thenReturn(viewHeight)
        whenever(mockDrawable.intrinsicWidth).thenReturn(drawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(drawableHeight)

        val viewWidthNormalized = viewWidth.densityNormalized(fakeDensity).toLong()
        val drawableWidthNormalized = drawableWidth.densityNormalized(fakeDensity).toLong()
        val drawableHeightNormalized = drawableHeight.densityNormalized(fakeDensity).toLong()

        val expectedHeight = (viewWidthNormalized * drawableHeightNormalized) / drawableWidthNormalized
        val expectedWidth = viewWidthNormalized

        // When
        val result = testedDrawableUtils.getDrawableScaledDimensions(
            mockImageView,
            mockDrawable,
            fakeDensity
        )

        // Then
        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
    }

    @Test
    fun `M return correct dimensions W getDrawableScaledDimensions() { CenterCrop, width eq height }`(
        @Mock mockImageView: ImageView,
        @Mock mockDrawable: Drawable,
        @IntForgery(min = 1, max = 1000) fakeDimension: Int,
        @FloatForgery(0.1f, 3f) fakeDensity: Float
    ) {
        // Given
        whenever(mockImageView.scaleType).thenReturn(ImageView.ScaleType.CENTER_CROP)
        whenever(mockImageView.width).thenReturn(fakeDimension)
        whenever(mockImageView.height).thenReturn(fakeDimension)
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDimension)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDimension)

        val fakeDimensionNormalized = fakeDimension.densityNormalized(fakeDensity).toLong()

        val expectedWidth = fakeDimensionNormalized
        val expectedHeight = fakeDimensionNormalized

        // When
        val result = testedDrawableUtils.getDrawableScaledDimensions(
            mockImageView,
            mockDrawable,
            fakeDensity
        )

        // Then
        assertThat(result.width).isEqualTo(expectedWidth)
        assertThat(result.height).isEqualTo(expectedHeight)
    }
}
