/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import com.datadog.android.api.InternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class RasterizerBitmapTest {

    private val mockInternalLogger: InternalLogger = mock()
    private val mockBitmap: Bitmap = mock()
    private val mockSoftwareDrawableResolver: SoftwareDrawableResolver = mock()

    private fun backgroundView(widthPx: Int, heightPx: Int, background: Drawable): View {
        val mockView: View = mock()
        whenever(mockView.width).thenReturn(widthPx)
        whenever(mockView.height).thenReturn(heightPx)
        whenever(mockView.background).thenReturn(background)
        whenever(background.copyBounds()).thenReturn(Rect())
        return mockView
    }

    @Test
    fun `M draw the background at full size W rasterize() { within the budget }`() {
        // Given
        val mockBackground: Drawable = mock()
        whenever(mockSoftwareDrawableResolver.resolve(mockBackground)).thenReturn(mockBackground)
        val testedRasterizer = DefaultViewBackgroundRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)
        val fakeSizes = mutableListOf<Pair<Int, Int>>()

        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(any<Int>(), any<Int>(), any<Bitmap.Config>()) }
                .thenAnswer { fakeSizes += it.getArgument<Int>(0) to it.getArgument<Int>(1); mockBitmap }
            mockConstruction(Canvas::class.java).use { canvases ->
                // When
                val result = testedRasterizer.rasterize(backgroundView(100, 40, mockBackground))

                // Then
                assertThat(result).isSameAs(mockBitmap)
                assertThat(fakeSizes).containsExactly(100 to 40)
                verify(canvases.constructed().single()).scale(1f, 1f)
                verify(mockBackground).draw(canvases.constructed().single())
            }
        }
    }

    @Test
    fun `M draw the background scaled to the budget W rasterize() { view over the budget }`() {
        // Given
        val mockBackground: Drawable = mock()
        whenever(mockSoftwareDrawableResolver.resolve(mockBackground)).thenReturn(mockBackground)
        val testedRasterizer = DefaultViewBackgroundRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)
        val fakeSizes = mutableListOf<Pair<Int, Int>>()

        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(any<Int>(), any<Int>(), any<Bitmap.Config>()) }
                .thenAnswer { fakeSizes += it.getArgument<Int>(0) to it.getArgument<Int>(1); mockBitmap }
            mockConstruction(Canvas::class.java).use { canvases ->
                // When
                testedRasterizer.rasterize(backgroundView(1080, 7200, mockBackground))

                // Then
                val (width, height) = fakeSizes.single()
                assertThat(PixelCaptureEligibility.exceedsRasterBudget(width, height)).isFalse()
                verify(canvases.constructed().single()).scale(width / 1080f, height / 7200f)
            }
        }
    }

    @Test
    fun `M restore the bounds of the live background W rasterize()`() {
        // Given
        val mockBackground: Drawable = mock()
        val fakeOriginalBounds = Rect()
        whenever(mockSoftwareDrawableResolver.resolve(mockBackground)).thenReturn(mockBackground)
        val testedRasterizer = DefaultViewBackgroundRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)
        val mockView = backgroundView(100, 40, mockBackground)
        whenever(mockBackground.copyBounds()).thenReturn(fakeOriginalBounds)

        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888) }.thenReturn(mockBitmap)
            mockConstruction(Canvas::class.java).use {
                // When
                testedRasterizer.rasterize(mockView)

                // Then
                verify(mockBackground).setBounds(fakeOriginalBounds)
            }
        }
    }

    @Test
    fun `M draw the resolved drawable W rasterize() { background needs a software copy }`() {
        // Given
        val mockBackground: Drawable = mock()
        val mockSoftwareCopy: Drawable = mock()
        whenever(mockSoftwareCopy.copyBounds()).thenReturn(Rect())
        whenever(mockSoftwareDrawableResolver.resolve(mockBackground)).thenReturn(mockSoftwareCopy)
        val testedRasterizer = DefaultViewBackgroundRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)

        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888) }.thenReturn(mockBitmap)
            mockConstruction(Canvas::class.java).use { canvases ->
                // When
                val result = testedRasterizer.rasterize(backgroundView(100, 40, mockBackground))

                // Then
                assertThat(result).isSameAs(mockBitmap)
                verify(mockSoftwareCopy).draw(canvases.constructed().single())
                verify(mockBackground, never()).draw(any())
            }
        }
    }

    @Test
    fun `M return null without allocating W rasterize() { background cannot be drawn on a software canvas }`() {
        // Given
        val mockBackground: Drawable = mock()
        whenever(mockSoftwareDrawableResolver.resolve(mockBackground)).thenReturn(null)
        val testedRasterizer = DefaultViewBackgroundRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)

        mockStatic(Bitmap::class.java).use { factory ->
            // When
            val result = testedRasterizer.rasterize(backgroundView(100, 40, mockBackground))

            // Then
            assertThat(result).isNull()
            factory.verifyNoInteractions()
        }
    }

    @Test
    fun `M draw the icon at its bounds origin without changing its bounds W rasterize() { compound drawable }`() {
        // Given
        val mockDrawable: Drawable = mock()
        val fakeBounds = Rect().apply {
            left = 10
            top = 5
            right = 30
            bottom = 25
        }
        whenever(mockDrawable.bounds).thenReturn(fakeBounds)
        whenever(mockSoftwareDrawableResolver.resolve(mockDrawable)).thenReturn(mockDrawable)
        val testedRasterizer = DefaultCompoundDrawableRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)

        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888) }.thenReturn(mockBitmap)
            mockConstruction(Canvas::class.java).use { canvases ->
                // When
                val result = testedRasterizer.rasterize(mockDrawable, 20, 20)

                // Then
                assertThat(result).isSameAs(mockBitmap)
                verify(canvases.constructed().single()).translate(-10f, -5f)
                verify(mockDrawable).draw(canvases.constructed().single())
                verify(mockDrawable, never()).setBounds(any<Rect>())
            }
        }
    }

    @Test
    fun `M draw the icon scaled to the budget W rasterize() { compound drawable over the budget }`() {
        // Given
        val mockDrawable: Drawable = mock()
        whenever(mockDrawable.bounds).thenReturn(Rect())
        whenever(mockSoftwareDrawableResolver.resolve(mockDrawable)).thenReturn(mockDrawable)
        val testedRasterizer = DefaultCompoundDrawableRasterizer(mockInternalLogger, mockSoftwareDrawableResolver)
        val fakeSizes = mutableListOf<Pair<Int, Int>>()

        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(any<Int>(), any<Int>(), any<Bitmap.Config>()) }
                .thenAnswer { fakeSizes += it.getArgument<Int>(0) to it.getArgument<Int>(1); mockBitmap }
            mockConstruction(Canvas::class.java).use { canvases ->
                // When
                testedRasterizer.rasterize(mockDrawable, 4000, 4000)

                // Then
                val (width, height) = fakeSizes.single()
                assertThat(PixelCaptureEligibility.exceedsRasterBudget(width, height)).isFalse()
                verify(canvases.constructed().single()).scale(width / 4000f, height / 4000f)
            }
        }
    }
}
