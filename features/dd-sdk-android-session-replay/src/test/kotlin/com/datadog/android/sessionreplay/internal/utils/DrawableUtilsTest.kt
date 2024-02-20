/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.DisplayMetrics
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesSerializer
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.internal.recorder.wrappers.CanvasWrapper
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

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
    private lateinit var mockBitmapCachesManager: BitmapCachesManager

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

    @Mock
    private lateinit var mockExecutorService: ExecutorService

    @Mock
    private lateinit var mockBitmapCreationCallback: ResourcesSerializer.BitmapCreationCallback

    @Mock
    private lateinit var mockMainThreadHandler: Handler

    @BeforeEach
    fun setup() {
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(mockBitmap)
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(mockCanvas)
        whenever(mockBitmap.config).thenReturn(mockConfig)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any())).thenReturn(null)

        doAnswer { invocation ->
            val work = invocation.getArgument(0) as Runnable
            work.run()
            null
        }.whenever(mockMainThreadHandler).post(
            any()
        )

        whenever(mockExecutorService.execute(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        testedDrawableUtils = DrawableUtils(
            bitmapWrapper = mockBitmapWrapper,
            canvasWrapper = mockCanvasWrapper,
            bitmapCachesManager = mockBitmapCachesManager,
            mainThreadHandler = mockMainThreadHandler
        )
    }

    // region createBitmap

    @Test
    fun `M set width to drawable intrinsic W createBitmapOfApproxSizeFromDrawable() { no resizing }`() {
        // Given
        val requestedSize = 1000
        val edge = 10
        whenever(mockDrawable.intrinsicWidth).thenReturn(edge)
        whenever(mockDrawable.intrinsicHeight).thenReturn(edge)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            requestedSizeInBytes = requestedSize,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
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
    fun `M set height higher W createBitmapOfApproxSizeFromDrawable() { when resizing }`(
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
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            bitmapCreationCallback = mockBitmapCreationCallback
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
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
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
    }

    // endregion

    @Test
    fun `M use bitmap from pool W createBitmapOfApproxSizeFromDrawable() { exists in pool }`(
        @IntForgery(min = 1, max = 1000) viewWidth: Int,
        @IntForgery(min = 1, max = 1000) viewHeight: Int
    ) {
        // Given
        val mockBitmapFromPool: Bitmap = mock()
        whenever(mockDrawable.intrinsicWidth).thenReturn(viewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(viewHeight)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any()))
            .thenReturn(mockBitmapFromPool)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onReady(mockBitmapFromPool)
    }

    @Test
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { failed to create bitmap }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any()))
            .thenReturn(null)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(null)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onFailure()
    }

    @Test
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { failed to create canvas }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(null)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockBitmapCreationCallback).onFailure()
    }

    @Test
    fun `M resize image that is greater than limit W createBitmapOfApproxSizeFromDrawable { when resizing }`(
        @IntForgery(min = 501, max = 1000) fakeViewWidth: Int,
        @IntForgery(min = 501, max = 1000) fakeViewHeight: Int
    ) {
        // Given
        val requestedSize = 1000
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeViewWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeViewHeight)

        val argumentCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            requestedSizeInBytes = requestedSize,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
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
        assertThat(width).isLessThanOrEqualTo(fakeViewWidth)
        assertThat(height).isLessThanOrEqualTo(fakeViewHeight)
        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)
    }

    @Test
    fun `M return scaled bitmap W createScaledBitmap()`(
        @Mock mockScaledBitmap: Bitmap
    ) {
        // Given
        whenever(
            mockBitmapWrapper.createScaledBitmap(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(mockScaledBitmap)

        // When
        val actualBitmap = testedDrawableUtils.createScaledBitmap(mockBitmap)

        // Then
        assertThat(actualBitmap).isEqualTo(mockScaledBitmap)
    }
}
