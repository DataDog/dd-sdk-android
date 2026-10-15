/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.DefaultViewRasterizer
import com.datadog.android.sessionreplay.internal.composition.mapper.ViewRasterizer
import com.datadog.android.sessionreplay.utils.GlobalBounds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class ViewPixelCaptureTest {
    private val fakePending = mutableListOf<PendingPixelCapture>()
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeContext = CapturedMappingContext(
        fakeFactory,
        fakeFactory.view(fakeFactory.window("window"), "view"),
        1f,
        imagePrivacy = ImagePrivacy.MASK_NONE,
        pendingPixelCaptureSink = PendingPixelCaptureSink(fakePending::add)
    )
    private val fakeTransform = ViewTransform(
        floatArrayOf(2f, 0f, 50f, 0f, 2f, 175f, 0f, 0f, 1f),
        GlobalBounds(50, 175, 200, 100)
    )
    private val mockRasterizer: ViewRasterizer = mock()
    private val testedMapper = ViewPixelCapture(mock(), rasterizer = mockRasterizer)

    @Test
    fun `M register transformed pixels for text masking W capture`() {
        // Given
        val stubView = view()
        val mockBitmap: Bitmap = mock()
        whenever(mockRasterizer.rasterize(any(), any(), any())).thenReturn(mockBitmap)

        // When
        val result = testedMapper.captureTransformed(
            stubView,
            fakeContext,
            fakeTransform,
            true
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.isTerminal).isTrue()
        val fakePixel = result.wireframes.single() as CapturedWireframe.Pixel
        assertThat(fakePixel.bounds.x).isEqualTo(50)
        assertThat(fakePixel.bounds.width).isEqualTo(200)
        assertThat(fakePending.single().wireframeIdentity).isEqualTo(fakePixel.identity)
        assertThat(fakePending.single().bitmap).isSameAs(mockBitmap)
        assertThat(fakePending.single().isTextFree).isFalse()
    }

    @Test
    fun `M mask without drawing W capture under MASK_ALL`() {
        // When
        val result = testedMapper.captureTransformed(
            view(),
            fakeContext.copy(imagePrivacy = ImagePrivacy.MASK_ALL),
            fakeTransform,
            true
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(fakePending).isEmpty()
        verifyNoInteractions(mockRasterizer)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M respect hidden and stronger child privacy W capture subtree`(fakeHidden: Boolean) {
        // Given
        val stubParent: ViewGroup = mock()
        val stubResources = resources()
        whenever(stubParent.resources).thenReturn(stubResources)
        val stubChild = view()
        whenever(stubParent.childCount).thenReturn(1)
        whenever(stubParent.getChildAt(0)).thenReturn(stubChild)
        if (fakeHidden) {
            whenever(stubChild.getTag(R.id.datadog_hidden)).thenReturn(true)
        } else {
            whenever(stubChild.getTag(R.id.datadog_image_privacy)).thenReturn(ImagePrivacy.MASK_ALL.name)
        }

        // When
        val result = testedMapper.captureTransformed(
            stubParent,
            fakeContext,
            fakeTransform,
            true
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        verifyNoInteractions(mockRasterizer)
    }

    @Test
    fun `M retain overflow capture placement W capture without parent clipping`() {
        // Given
        val stubView = view()
        val stubWindow = view()
        whenever(stubWindow.width).thenReturn(1000)
        whenever(stubWindow.height).thenReturn(800)
        whenever(stubView.rootView).thenReturn(stubWindow)
        val mockBitmap: Bitmap = mock()
        whenever(mockRasterizer.rasterize(any(), any(), any())).thenReturn(mockBitmap)

        // When
        val result = testedMapper.captureTransformed(
            stubView,
            fakeContext,
            fakeTransform,
            false
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.single().bounds.width).isEqualTo(1000)
        val fakeRaster = argumentCaptor<ViewTransform>()
        verify(mockRasterizer).rasterize(any(), fakeRaster.capture(), org.mockito.kotlin.eq(false))
        assertThat(fakeRaster.firstValue.pixelBounds).isEqualTo(GlobalBounds(0, 0, 1000, 800))
    }

    @Test
    fun `M fail closed W rasterization fails`() {
        // When
        val result = testedMapper.captureTransformed(
            view(),
            fakeContext,
            fakeTransform,
            true
        ) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(fakePending).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M draw with copied matrix and local clips W rasterize`(fakeClipToBounds: Boolean) {
        // Given
        val stubView = view()
        val fakeClip = Rect(10, 5, 90, 45)
        whenever(stubView.clipBounds).thenReturn(fakeClip)
        whenever(stubView.scrollX).thenReturn(7)
        whenever(stubView.scrollY).thenReturn(9)
        val mockBitmap: Bitmap = mock()
        val testedRasterizer = DefaultViewRasterizer(mock())
        mockStatic(Bitmap::class.java).use { fakeBitmaps ->
            fakeBitmaps.`when`<Bitmap> { Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888) }.thenReturn(mockBitmap)
            mockConstruction(Matrix::class.java).use { fakeMatrices ->
                mockConstruction(Canvas::class.java).use { fakeCanvases ->
                    // When
                    assertThat(
                        testedRasterizer.rasterize(stubView, fakeTransform, fakeClipToBounds)
                    ).isSameAs(mockBitmap)

                    // Then
                    val mockCanvas = fakeCanvases.constructed().single()
                    val mockMatrix = fakeMatrices.constructed().single()
                    verify(mockMatrix).setValues(fakeTransform.matrix)
                    verifyDrawCalls(mockCanvas, mockMatrix, stubView, fakeClip, fakeClipToBounds)
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M share descendant privacy checks W capture ordinary or transformed content`(
        fakeTransformed: Boolean,
        fakeHidden: Boolean
    ) {
        // Given
        val stubParent: ViewGroup = mock()
        whenever(stubParent.width).thenReturn(100)
        whenever(stubParent.height).thenReturn(50)
        val stubResources = resources()
        whenever(stubParent.resources).thenReturn(stubResources)
        val stubChild = view()
        whenever(stubParent.childCount).thenReturn(1)
        whenever(stubParent.getChildAt(0)).thenReturn(stubChild)
        if (fakeHidden) {
            whenever(stubChild.getTag(R.id.datadog_hidden)).thenReturn(true)
        } else {
            whenever(stubChild.getTag(R.id.datadog_image_privacy)).thenReturn(ImagePrivacy.MASK_ALL.name)
        }
        val fakeMappingContext = fakeContext.copy(
            bounds = com.datadog.android.internal.sessionreplay.composition.CapturedBounds(0, 0, 100, 50)
        )

        // When
        val fakeResult = if (fakeTransformed) {
            testedMapper.captureTransformed(stubParent, fakeMappingContext, fakeTransform, true)
        } else {
            testedMapper.capture(stubParent, fakeMappingContext)
        }

        // Then
        if (fakeTransformed) {
            assertThat((fakeResult as CapturedViewMapperResult.Wireframes).wireframes.single())
                .isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        } else {
            assertThat(fakeResult).isNull()
        }
        verifyNoInteractions(mockRasterizer)
        assertThat(fakePending).isEmpty()
    }

    private fun verifyDrawCalls(canvas: Canvas, matrix: Matrix, view: View, clip: Rect, clipToBounds: Boolean) {
        inOrder(canvas, view) {
            verify(canvas).translate(-50f, -175f)
            verify(canvas).concat(matrix)
            if (clipToBounds) verify(canvas).clipRect(0, 0, 100, 50)
            verify(canvas).translate(-7f, -9f)
            verify(canvas).clipRect(clip)
            verify(view).draw(canvas)
        }
        if (!clipToBounds) verify(canvas, never()).clipRect(0, 0, 100, 50)
        verify(view, never()).alpha
    }

    private fun view(): View {
        val stubView: View = mock()
        val stubResources = resources()
        whenever(stubView.resources).thenReturn(stubResources)
        whenever(stubView.width).thenReturn(100)
        whenever(stubView.height).thenReturn(50)
        return stubView
    }

    private fun resources(): Resources {
        val stubResources: Resources = mock()
        whenever(stubResources.displayMetrics).thenReturn(
            DisplayMetrics().apply {
                widthPixels = 1000
                heightPixels = 1000
            }
        )
        return stubResources
    }
}
