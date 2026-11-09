/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.MeasuredComposeText
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResourceKey
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResources
import com.datadog.android.sessionreplay.utils.GlobalBounds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.ref.WeakReference

internal class ComposeTextRasterizerTest {
    private val mockSemanticsUtils: SemanticsUtils = mock()
    private val mockStateReader: ComposeTextCaptureCache = mock()
    private val mockParagraph: Paragraph = mock()
    private val mockPaint: Paint = mock()
    private val mockNode: SemanticsNode = mock()
    private val fakeOffset = ComposeWindowOffset(0, 0, 0, 0)
    private val fakeBounds = GlobalBounds(10, 20, 80, 40)
    private val fakeText = TextLayoutInfo("Current", Color.Red.value, 12, null, sourceText = "Current")
    private val mockReflectionUtils: ReflectionUtils = mock()
    private val testedRasterizer =
        DefaultComposeTextRasterizer(mockSemanticsUtils, mockStateReader, mockReflectionUtils)
    private var layoutRequests = 0

    // Mockito sees the primitive JVM representations of these Compose value classes.
    @Suppress("IgnoredReturnValue") // Registers the primitive argument matcher.
    private fun anyColor(): Color = Color.Unspecified.also { org.mockito.ArgumentMatchers.anyLong() }

    @Suppress("IgnoredReturnValue") // Registers the primitive argument matcher.
    private fun anyBlendMode(): BlendMode = BlendMode.SrcOver.also { org.mockito.ArgumentMatchers.anyInt() }

    private fun measured(size: IntSize = IntSize(80, 40)) {
        val action: (MutableList<TextLayoutResult>) -> Boolean = { layoutRequests++; false }
        val config = SemanticsConfiguration().apply {
            set(SemanticsActions.GetTextLayoutResult, AccessibilityAction(null, action))
        }
        whenever(mockNode.config).thenReturn(config)
        whenever(mockNode.id).thenReturn(1)
        whenever(mockSemanticsUtils.resolveTextBounds(mockNode, fakeOffset)).thenReturn(fakeBounds)
        whenever(mockPaint.color).thenReturn(Color.Red.toArgb())
        val paragraph = MeasuredComposeText(mockParagraph, "Current", TextStyle.Default, size, mockPaint)
        whenever(mockStateReader.state(mockNode, action)).thenReturn(
            ComposeTextCaptureCache.State(mockParagraph, 0, 0, paragraph)
        )
    }

    @Test
    fun `M paint measured glyphs without another text layout W rasterize`() {
        // Given
        measured()
        val pixels: Bitmap = mock()
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888) }.thenReturn(pixels)
            mockConstruction(Canvas::class.java).use {
                // When
                val result = testedRasterizer.rasterize(mockNode, fakeText, emptyList(), fakeOffset)

                // Then
                assertThat(result).isEqualTo(RasterizedComposeText(pixels, fakeBounds))
                assertThat(layoutRequests).isZero()
                verify(mockParagraph).paint(
                    any(),
                    anyColor(),
                    org.mockito.kotlin.isNull(),
                    org.mockito.kotlin.isNull(),
                    org.mockito.kotlin.isNull(),
                    anyBlendMode()
                )
                verify(pixels, never()).recycle()
            }
        }
    }

    @Test
    fun `M fall back when current color differs from drawn paint W rasterize`() {
        // Given
        measured()
        whenever(mockPaint.color).thenReturn(Color.Blue.toArgb())

        // When
        val result = testedRasterizer.rasterize(mockNode, fakeText, emptyList(), fakeOffset)

        // Then
        assertThat(result).isNull()
        assertThat(layoutRequests).isEqualTo(1)
    }

    @Test
    fun `M reject changed source text W rasterize`() {
        // Given
        measured()

        // When
        val result = testedRasterizer.rasterize(
            mockNode,
            fakeText.copy(sourceText = "Changed"),
            emptyList(),
            fakeOffset
        )

        // Then
        assertThat(result).isNull()
        assertThat(layoutRequests).isEqualTo(1)
    }

    @Test
    fun `M reject oversized measured output before allocation W rasterize`() {
        // Given
        measured(IntSize(50_000, 50_000))
        mockStatic(Bitmap::class.java).use { factory ->
            // When
            val result = testedRasterizer.rasterize(mockNode, fakeText, emptyList(), fakeOffset)

            // Then
            assertThat(result).isNull()
            assertThat(layoutRequests).isZero()
            factory.verifyNoInteractions()
        }
    }

    @Test
    fun `M recycle unfinished measured output W rasterize`() {
        // Given
        measured()
        val pixels: Bitmap = mock()
        val failure = IllegalStateException("simulated")
        doThrow(failure).`when`(mockParagraph).paint(
            any(),
            anyColor(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            anyBlendMode()
        )
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888) }.thenReturn(pixels)
            mockConstruction(Canvas::class.java).use {
                // When + Then
                assertThatThrownBy {
                    testedRasterizer.rasterize(mockNode, fakeText, emptyList(), fakeOffset)
                }.isSameAs(failure)
                verify(pixels).recycle()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reuse resolved glyphs or repaint on resource miss W rasterize`(resourcePresent: Boolean) {
        // Given
        measured()
        val stubModifier: Modifier = mock()
        val stubModifierInfo: ModifierInfo = mock()
        whenever(stubModifierInfo.modifier).thenReturn(stubModifier)
        whenever(mockReflectionUtils.isTextStringSimpleElement(stubModifier)).thenReturn(true)
        val stubResources: CompositionTextResources = mock()
        val stubPixels: Bitmap = mock()
        val movedBounds = GlobalBounds(30, 40, 80, 40)
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888) }.thenReturn(stubPixels)
            mockConstruction(Canvas::class.java).use {
                testedRasterizer.rasterize(mockNode, fakeText, listOf(stubModifierInfo), fakeOffset, stubResources)
                val key = argumentCaptor<CompositionTextResourceKey>()
                verify(stubResources).registerCapture(key.capture(), eq(stubPixels))
                // A live bitmap is preferred and does not consult the native coordinator.
                testedRasterizer.rasterize(mockNode, fakeText, listOf(stubModifierInfo), fakeOffset, stubResources)
                verify(stubResources, never()).findResourceId(any())
                clearCachedBitmap()
                whenever(stubResources.findResourceId(key.firstValue))
                    .thenReturn(if (resourcePresent) "resolved-glyph" else null)
                whenever(mockSemanticsUtils.resolveTextBounds(mockNode, fakeOffset)).thenReturn(movedBounds)

                // When
                val result = testedRasterizer.rasterize(
                    mockNode,
                    fakeText,
                    listOf(stubModifierInfo),
                    fakeOffset,
                    stubResources
                )!!

                // Then
                assertThat(result.bounds).isEqualTo(movedBounds)
                assertThat(result.resourceId).isEqualTo(if (resourcePresent) "resolved-glyph" else null)
                assertThat(result.bitmap).isSameAs(if (resourcePresent) null else stubPixels)
                factory.verify({
                    Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
                }, times(if (resourcePresent) 1 else 2))
                verify(stubResources, times(if (resourcePresent) 1 else 2)).registerCapture(any(), eq(stubPixels))
                assertThat(layoutRequests).isZero()
            }
        }
    }

    private fun clearCachedBitmap() {
        val cache = testedRasterizer.javaClass.getDeclaredField("bitmapCache").apply { isAccessible = true }
            .get(testedRasterizer)
        val entries = cache.javaClass.getDeclaredField("entries").apply { isAccessible = true }.get(cache) as Map<*, *>
        val entry = entries[1]!!
        val bitmap = entry.javaClass.getDeclaredField("bitmap").apply { isAccessible = true }
            .get(entry) as WeakReference<*>
        bitmap.clear()
    }
}
