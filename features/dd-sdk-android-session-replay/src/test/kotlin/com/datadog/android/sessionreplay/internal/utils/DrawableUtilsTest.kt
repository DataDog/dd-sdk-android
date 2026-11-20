/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.util.AndroidRuntimeException
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourceResolver
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils.Companion.DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream
import kotlin.math.sqrt

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
    private lateinit var mockCurrentDrawable: Drawable

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
    private lateinit var mockBitmapCreationCallback: ResourceResolver.BitmapCreationCallback

    @Mock
    lateinit var mockConstantState: ConstantState

    @Mock
    lateinit var mockSecondDrawable: Drawable

    @Mock
    lateinit var mockResources: Resources

    @Mock
    private lateinit var mockLogger: InternalLogger

    @BeforeEach
    fun setup() {
        whenever(mockConstantState.newDrawable(mockResources)).thenReturn(mockSecondDrawable)
        whenever(mockDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockCurrentDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockDrawable.current).thenReturn(mockCurrentDrawable)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(mockBitmap)
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(mockCanvas)
        whenever(mockBitmap.config).thenReturn(mockConfig)
        whenever(mockBitmapCachesManager.getBitmapByProperties(any(), any(), any())).thenReturn(null)

        testedDrawableUtils = createDrawableUtils()
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
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
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
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
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
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
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
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
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
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { failed to create new drawable }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockBitmap.isRecycled).thenReturn(true)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any())).thenReturn(
            null
        )

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
            bitmapWidth = argumentCaptor.capture(),
            bitmapHeight = argumentCaptor.capture(),
            config = any(),
            displayMetrics = displayMetricsCaptor.capture()
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

    @Test
    fun `M draw the source into a scaled ARGB_8888 bitmap W createScaledArgb8888Bitmap()`(
        @Mock mockSourceBitmap: Bitmap,
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int
    ) {
        // Given
        whenever(mockSourceBitmap.width).thenReturn(fakeWidth)
        whenever(mockSourceBitmap.height).thenReturn(fakeHeight)

        // When
        val result = testedDrawableUtils.createScaledArgb8888Bitmap(mockSourceBitmap)

        // Then
        assertThat(result).isSameAs(mockBitmap)
        argumentCaptor<Int> {
            verify(mockBitmapWrapper).createBitmap(capture(), capture(), eq(Bitmap.Config.ARGB_8888), isNull())
            assertThat(firstValue.toLong() * secondValue * 4)
                .isLessThanOrEqualTo(DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT.toLong())
        }
        verify(mockCanvas).drawBitmap(eq(mockSourceBitmap), isNull<Rect>(), any<Rect>(), isNull<Paint>())
    }

    @Test
    fun `M keep the aspect ratio and fill the limit W createScaledArgb8888Bitmap() { elongated bitmap }`(
        @Mock mockSourceBitmap: Bitmap
    ) {
        // Given
        whenever(mockSourceBitmap.width).thenReturn(1440)
        whenever(mockSourceBitmap.height).thenReturn(3200)

        // When
        testedDrawableUtils.createScaledArgb8888Bitmap(mockSourceBitmap)

        // Then
        argumentCaptor<Int> {
            verify(mockBitmapWrapper).createBitmap(capture(), capture(), eq(Bitmap.Config.ARGB_8888), isNull())
            assertThat(firstValue.toDouble() / secondValue).isCloseTo(1440.0 / 3200, within(0.001))
            assertThat(firstValue.toLong() * secondValue * 4)
                .isBetween(
                    DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT * 99L / 100,
                    DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT.toLong()
                )
        }
    }

    @Test
    fun `M keep each edge within WebP's limit W createScaledArgb8888Bitmap() { very long bitmap }`(
        @Mock mockSourceBitmap: Bitmap,
        @IntForgery(min = 2_700_000, max = 10_000_000) fakeLongEdge: Int
    ) {
        // Given
        whenever(mockSourceBitmap.width).thenReturn(fakeLongEdge)
        whenever(mockSourceBitmap.height).thenReturn(1)

        // When
        testedDrawableUtils.createScaledArgb8888Bitmap(mockSourceBitmap)

        // Then
        argumentCaptor<Int> {
            verify(mockBitmapWrapper).createBitmap(capture(), capture(), eq(Bitmap.Config.ARGB_8888), isNull())
            assertThat(firstValue).isBetween(16000, 16383)
            assertThat(secondValue).isEqualTo(1)
        }
    }

    @Test
    fun `M release the bitmap and return null W createScaledArgb8888Bitmap() { drawing throws }`(
        @Mock mockSourceBitmap: Bitmap
    ) {
        // Given
        val fakeError = IllegalStateException()
        doThrow(fakeError).whenever(mockCanvas)
            .drawBitmap(eq(mockSourceBitmap), isNull<Rect>(), any<Rect>(), isNull<Paint>())

        // When
        val result = testedDrawableUtils.createScaledArgb8888Bitmap(mockSourceBitmap)

        // Then
        assertThat(result).isNull()
        verify(mockBitmap).recycle()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Failed to create a scaled bitmap from the drawable",
            throwable = fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M release the bitmap and return null W createScaledArgb8888Bitmap() { no canvas }`(
        @Mock mockSourceBitmap: Bitmap
    ) {
        // Given
        whenever(mockCanvasWrapper.createCanvas(mockBitmap)).thenReturn(null)

        // When
        val result = testedDrawableUtils.createScaledArgb8888Bitmap(mockSourceBitmap)

        // Then
        assertThat(result).isNull()
        verify(mockBitmap).recycle()
    }

    @Test
    fun `M return null W createScaledArgb8888Bitmap() { bitmap creation fails }`(
        @Mock mockSourceBitmap: Bitmap
    ) {
        // Given
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull())).thenReturn(null)

        // When
        val result = testedDrawableUtils.createScaledArgb8888Bitmap(mockSourceBitmap)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockCanvasWrapper)
    }

    @Test
    fun `M cap the long edge and keep the short edge W createScaledBitmap() { very long bitmap }`(
        @IntForgery(min = 1, max = 50) fakeShortEdge: Int,
        @IntForgery(min = 2_700_000, max = 10_000_000) fakeLongEdge: Int,
        @BoolForgery fakeIsWide: Boolean
    ) {
        // Given
        whenever(mockBitmap.width).thenReturn(if (fakeIsWide) fakeLongEdge else fakeShortEdge)
        whenever(mockBitmap.height).thenReturn(if (fakeIsWide) fakeShortEdge else fakeLongEdge)
        val expectedLongEdge = sqrt(DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT / 4.0).toInt()
        val expectedSize = if (fakeIsWide) listOf(expectedLongEdge, 1) else listOf(1, expectedLongEdge)

        // When
        testedDrawableUtils.createScaledBitmap(mockBitmap)

        // Then
        argumentCaptor<Int> {
            verify(mockBitmapWrapper).createScaledBitmap(eq(mockBitmap), capture(), capture(), eq(false))
            assertThat(allValues).isEqualTo(expectedSize)
        }
    }

    @ParameterizedTest
    @MethodSource("exceptionTypes")
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { drawable draw throws runtime exception }`(
        exceptionType: RuntimeException
    ) {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockDrawable.draw(any())).thenThrow(exceptionType)

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

        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { it == "$DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION ${mockDrawable.javaClass.canonicalName}" },
            exceptionType
        )
    }

    @Test
    fun `M call onFailure W createBitmapOfApproxSizeFromDrawable { drawable setBounds throws runtime exception }`() {
        // Given
        val fakeException = AndroidRuntimeException()
        whenever(mockDrawable.intrinsicWidth).thenReturn(1)
        whenever(mockDrawable.intrinsicHeight).thenReturn(1)
        whenever(mockDrawable.setBounds(any(), any(), any(), any())).thenThrow(fakeException)

        // When
        assertDoesNotThrow {
            testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = mockDrawable,
                drawableWidth = mockDrawable.intrinsicWidth,
                drawableHeight = mockDrawable.intrinsicHeight,
                displayMetrics = mockDisplayMetrics,
                config = mockConfig,
                bitmapCreationCallback = mockBitmapCreationCallback
            )
        }

        // Then
        verify(mockBitmapCreationCallback).onFailure()
        verify(mockDrawable, never()).draw(any())
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "$DRAWABLE_DRAW_FINISHED_WITH_RUNTIME_EXCEPTION ${mockDrawable.javaClass.canonicalName}",
            fakeException
        )
    }

    @Test
    fun `M return the raster to the pool before failing W createBitmapOfApproxSizeFromDrawable() { drawing fails }`(
        @BoolForgery fakeCanvasFails: Boolean
    ) {
        // Given
        if (fakeCanvasFails) {
            whenever(mockCanvasWrapper.createCanvas(mockBitmap)).thenReturn(null)
        } else {
            whenever(mockDrawable.draw(mockCanvas)).thenThrow(IllegalStateException())
        }

        // When
        drawBitmap()

        // Then
        inOrder(mockBitmapCachesManager, mockBitmapCreationCallback) {
            verify(mockBitmapCachesManager).putInBitmapPool(mockBitmap)
            verify(mockBitmapCreationCallback).onFailure()
        }
    }

    @Test
    fun `M use CLEAR mode W createBitmapOfApproxSizeFromDrawable() { when drawing on canvas }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(10)
        whenever(mockDrawable.intrinsicHeight).thenReturn(10)

        // When
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            displayMetrics = mockDisplayMetrics,
            bitmapCreationCallback = mockBitmapCreationCallback
        )

        // Then
        verify(mockCanvas).drawColor(eq(Color.TRANSPARENT), eq(PorterDuff.Mode.CLEAR))
    }

    @Test
    fun `M propagate without completing W createBitmapOfApproxSizeFromDrawable() { setBounds throws error }`() {
        // Given
        val fakeError = StackOverflowError()
        doThrow(fakeError).whenever(mockDrawable).setBounds(any(), any(), any(), any())

        // When
        val actualError = assertThrows<StackOverflowError> { drawBitmap() }

        // Then
        assertThat(actualError).isSameAs(fakeError)
        verifyNoInteractions(mockBitmapCreationCallback)
    }

    private fun createDrawableUtils(): DrawableUtils = DrawableUtils(
        bitmapWrapper = mockBitmapWrapper,
        canvasWrapper = mockCanvasWrapper,
        bitmapCachesManager = mockBitmapCachesManager,
        internalLogger = mockLogger
    )

    private fun drawBitmap() {
        testedDrawableUtils.createBitmapOfApproxSizeFromDrawable(
            drawable = mockDrawable,
            drawableWidth = 1,
            drawableHeight = 1,
            displayMetrics = mockDisplayMetrics,
            config = mockConfig,
            bitmapCreationCallback = mockBitmapCreationCallback
        )
    }

    companion object {
        @JvmStatic
        fun exceptionTypes(): Stream<RuntimeException> {
            return Stream.of(
                AndroidRuntimeException(),
                IndexOutOfBoundsException()
            )
        }
    }
}
