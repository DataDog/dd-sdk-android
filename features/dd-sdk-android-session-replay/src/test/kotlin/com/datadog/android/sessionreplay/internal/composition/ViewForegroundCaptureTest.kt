/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class ViewForegroundCaptureTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("scope"))
    private val fakePending = mutableListOf<PendingPixelCapture>()
    private val fakeContext = CapturedMappingContext(
        fakeFactory,
        fakeFactory.view(fakeFactory.window("window"), "view"),
        2f,
        bounds = CapturedBounds(10, 20, 50, 40),
        imagePrivacy = ImagePrivacy.MASK_NONE,
        pendingPixelCaptureSink = PendingPixelCaptureSink(fakePending::add)
    )
    private val mockRasterizer: ViewForegroundRasterizer = mock()
    private val mockColorResolver: ForegroundColorResolver = mock()
    private val testedMapper = ViewForegroundCapture(mock(), mockRasterizer, mockColorResolver)

    @Test
    fun `M capture a translucent foreground without a background W capture under MASK_ALL`() {
        // Given
        val stubDrawable: ColorDrawable = mock()
        val stubView = view(stubDrawable)
        whenever(mockColorResolver.resolve(stubDrawable)).thenReturn(0x80112233.toInt())

        // When
        val fakeForeground =
            checkNotNull(testedMapper.capture(stubView, fakeContext.copy(imagePrivacy = ImagePrivacy.MASK_ALL)))

        // Then
        val fakeShape = fakeForeground.wireframe as CapturedWireframe.Shape
        assertThat(fakeShape.bounds).isEqualTo(fakeContext.bounds)
        assertThat(fakeShape.style?.backgroundColor).isEqualTo("#11223380")
        assertThat(fakeShape.style?.opacity).isNull()
        assertThat(fakeShape.identity).isNotEqualTo(fakeFactory.shapeWireframe(fakeContext.ownerIdentity))
        assertThat(fakeForeground.identity).isEqualTo(fakeFactory.layer(fakeContext.ownerIdentity, "foreground"))
        assertThat(fakePending).isEmpty()
        verifyNoInteractions(mockRasterizer)
    }

    @Test
    fun `M omit fully transparent foregrounds W capture`() {
        // Given
        val stubDrawable: ColorDrawable = mock()
        whenever(mockColorResolver.resolve(stubDrawable)).thenReturn(0x00112233)

        // When
        val fakeForeground = testedMapper.capture(view(stubDrawable), fakeContext)

        // Then
        assertThat(fakeForeground).isNull()
        assertThat(fakePending).isEmpty()
        verifyNoInteractions(mockRasterizer)
    }

    @ParameterizedTest
    @EnumSource(value = ImagePrivacy::class, names = ["MASK_LARGE_ONLY", "MASK_NONE"])
    fun `M register complex foreground pixels for text masking W capture`(fakePrivacy: ImagePrivacy) {
        // Given
        val stubView = view()
        val mockBitmap: Bitmap = mock()
        whenever(mockRasterizer.rasterize(stubView)).thenReturn(mockBitmap)

        // When
        val fakeForeground = checkNotNull(testedMapper.capture(stubView, fakeContext.copy(imagePrivacy = fakePrivacy)))

        // Then
        val fakePixel = fakeForeground.wireframe as CapturedWireframe.Pixel
        assertThat(fakePixel.bounds).isEqualTo(fakeContext.bounds)
        assertThat(fakePixel.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(fakePending).containsExactly(
            PendingPixelCapture(fakePixel.identity, fakeForeground.identity, mockBitmap, isTextFree = false)
        )
        verifyNoInteractions(mockColorResolver)
    }

    @Test
    fun `M omit private foreground chrome without covering children W capture under MASK_ALL`() {
        // When
        val fakeForeground = testedMapper.capture(view(), fakeContext.copy(imagePrivacy = ImagePrivacy.MASK_ALL))

        // Then
        assertThat(fakeForeground).isNull()
        assertThat(fakePending).isEmpty()
        verifyNoInteractions(mockRasterizer)
    }

    @ParameterizedTest
    @CsvSource("0, 80", "100, 0", "10000, 10000")
    fun `M avoid invalid or oversized raster captures W capture`(fakeWidth: Int, fakeHeight: Int) {
        // Given
        val stubView = view()
        whenever(stubView.width).thenReturn(fakeWidth)
        whenever(stubView.height).thenReturn(fakeHeight)

        // When
        val fakeForeground = testedMapper.capture(stubView, fakeContext)

        // Then
        assertThat(fakeForeground).isNull()
        assertThat(fakePending).isEmpty()
        verifyNoInteractions(mockRasterizer)
    }

    @ParameterizedTest
    @ValueSource(strings = ["gravity", "padding", "scroll"])
    fun `M use native placement for non filling solid foregrounds W capture`(fakeConstraint: String) {
        // Given
        val stubView = view(mock<ColorDrawable>())
        when (fakeConstraint) {
            "gravity" -> whenever(stubView.foregroundGravity).thenReturn(Gravity.BOTTOM or Gravity.END)
            "padding" -> whenever(stubView.paddingLeft).thenReturn(10)
            "scroll" -> whenever(stubView.scrollX).thenReturn(10)
        }
        whenever(mockRasterizer.rasterize(stubView)).thenReturn(mock())

        // When
        val fakeForeground = checkNotNull(testedMapper.capture(stubView, fakeContext))

        // Then
        assertThat(fakeForeground.wireframe).isInstanceOf(CapturedWireframe.Pixel::class.java)
        verifyNoInteractions(mockColorResolver)
    }

    @Test
    fun `M omit absent or failed foregrounds W capture`() {
        // Given
        val stubAbsent = view()
        whenever(stubAbsent.foreground).thenReturn(null)
        val stubFailed = view()

        // When
        assertThat(testedMapper.capture(stubAbsent, fakeContext)).isNull()
        assertThat(testedMapper.capture(stubFailed, fakeContext)).isNull()

        // Then
        assertThat(fakePending).isEmpty()
        verify(mockRasterizer, never()).rasterize(stubAbsent)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M draw only native foreground decorations W rasterize`(fakeFails: Boolean) {
        // Given
        val stubView = view()
        whenever(stubView.scrollX).thenReturn(10)
        whenever(stubView.scrollY).thenReturn(20)
        val mockBitmap: Bitmap = mock()
        mockStatic(Bitmap::class.java).use { bitmapFactory ->
            bitmapFactory.`when`<Bitmap> { Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888) }
                .thenReturn(mockBitmap)
            mockConstruction(Canvas::class.java).use { canvases ->
                if (fakeFails) whenever(stubView.onDrawForeground(any())).thenThrow(IllegalStateException("draw"))

                // When
                val fakeResult = DefaultViewForegroundRasterizer(mock()).rasterize(stubView)

                // Then
                assertThat(fakeResult).isSameAs(if (fakeFails) null else mockBitmap)
                val mockCanvas = canvases.constructed().single()
                inOrder(mockCanvas, stubView) {
                    verify(mockCanvas).translate(-10f, -20f)
                    verify(stubView).onDrawForeground(mockCanvas)
                }
                verify(stubView, never()).draw(any())
                if (fakeFails) verify(mockBitmap).recycle() else verify(mockBitmap, never()).recycle()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M restore drawable bounds and release color sample W resolve`(fakeFails: Boolean) {
        // Given
        val stubDrawable: ColorDrawable = mock()
        val fakeOriginalBounds = Rect(5, 10, 20, 30)
        whenever(stubDrawable.copyBounds()).thenReturn(fakeOriginalBounds)
        val mockBitmap: Bitmap = mock()
        whenever(mockBitmap.getPixel(0, 0)).thenReturn(0x80112233.toInt())
        if (fakeFails) whenever(stubDrawable.draw(any())).thenThrow(IllegalStateException("draw"))
        mockStatic(Bitmap::class.java).use { bitmapFactory ->
            bitmapFactory.`when`<Bitmap> { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }.thenReturn(mockBitmap)
            mockConstruction(Canvas::class.java).use {
                // When
                val fakeResult = DefaultForegroundColorResolver(mock()).resolve(stubDrawable)

                // Then
                assertThat(fakeResult).isEqualTo(if (fakeFails) null else 0x80112233.toInt())
                inOrder(stubDrawable) {
                    verify(stubDrawable).setBounds(0, 0, 1, 1)
                    verify(stubDrawable).draw(any())
                    verify(stubDrawable).setBounds(fakeOriginalBounds)
                }
                verify(mockBitmap).recycle()
            }
        }
    }

    private fun view(drawable: Drawable = mock()): View {
        val stubView: View = mock()
        whenever(stubView.width).thenReturn(100)
        whenever(stubView.height).thenReturn(80)
        whenever(stubView.foreground).thenReturn(drawable)
        whenever(stubView.foregroundGravity).thenReturn(Gravity.FILL)
        val stubResources: Resources = mock()
        whenever(stubResources.displayMetrics).thenReturn(
            DisplayMetrics().apply {
                widthPixels = 1000
                heightPixels = 1000
            }
        )
        whenever(stubView.resources).thenReturn(stubResources)
        return stubView
    }
}
