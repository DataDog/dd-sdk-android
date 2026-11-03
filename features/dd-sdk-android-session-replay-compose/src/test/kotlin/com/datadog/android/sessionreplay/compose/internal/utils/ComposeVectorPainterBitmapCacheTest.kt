/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ComposeVectorPainterBitmapCacheTest {
    private val painter: VectorPainter = mock()
    private var source = sourceBitmap()
    private var state: VectorPainterState? = rendererState()
    private val tints = mutableMapOf<ColorFilter, VectorPainterTint>()
    private val testedCache = cache()

    @Test
    fun `M share unchanged recorder output W capture`() {
        // Given
        val pixels: Bitmap = mock()
        var rendered = 0
        val render = { rendered++; pixels }

        // When
        val first = capture(render = render)
        val second = capture(render = render)

        // Then
        assertThat(first?.bitmap).isSameAs(pixels)
        assertThat(second?.bitmap).isSameAs(pixels)
        assertThat(first?.isShared).isTrue()
        assertThat(second?.isShared).isTrue()
        assertThat(rendered).isEqualTo(1)
        verify(source, never()).recycle()
        verify(pixels, never()).recycle()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "alpha", "tint", "density", "fontScale", "direction", "mirror", "viewport", "drawSize",
            "intrinsicTint", "cachedTint", "generation", "source"
        ]
    )
    fun `M recapture changed rendering inputs W capture`(changed: String) {
        // Given
        val first: Bitmap = mock()
        val updated: Bitmap = mock()
        capture { first }
        var alpha = 1f
        var filter: ColorFilter? = null
        var density = Density(3f)
        var direction = LayoutDirection.Ltr
        when (changed) {
            "alpha" -> alpha = .5f
            "tint" -> filter = mock<ColorFilter>().also { tints[it] = VectorPainterTint(1L, 2) }
            "density" -> density = Density(4f)
            "fontScale" -> density = Density(3f, 1.2f)
            "direction" -> direction = LayoutDirection.Rtl
            "mirror" -> state = state!!.copy(autoMirror = true)
            "viewport" -> state = state!!.copy(viewport = Size(25f, 24f))
            "drawSize" -> state = state!!.copy(previousDrawSize = 124L)
            "intrinsicTint" -> state = state!!.copy(intrinsicTint = VectorPainterTint(2L, 3))
            "cachedTint" -> state = state!!.copy(cachedTint = VectorPainterTint(3L, 4))
            "generation" -> whenever(source.generationId).thenReturn(2)
            "source" -> source = sourceBitmap()
        }

        // When
        val result = capture(alpha, filter, density, direction) { updated }

        // Then
        assertThat(result?.bitmap).isSameAs(updated)
        verify(first, never()).recycle()
    }

    @Test
    fun `M compare immutable tint values W capture`() {
        // Given
        val firstFilter: ColorFilter = mock()
        val secondFilter: ColorFilter = mock()
        tints[firstFilter] = VectorPainterTint(3L, 4)
        tints[secondFilter] = VectorPainterTint(3L, 4)
        val pixels: Bitmap = mock()
        capture(filter = firstFilter) { pixels }

        // When + Then
        assertThat(capture(filter = secondFilter) { error("Unchanged tint must reuse") }?.bitmap).isSameAs(pixels)
    }

    @ParameterizedTest
    @ValueSource(strings = ["state", "filter", "alpha", "density", "fontScale"])
    fun `M keep unreadable inputs owned and discard older entries W capture`(unsupported: String) {
        // Given
        val old: Bitmap = mock()
        capture { old }
        var alpha = 1f
        var filter: ColorFilter? = null
        var density = Density(3f)
        when (unsupported) {
            "state" -> state = null
            "filter" -> filter = mock()
            "alpha" -> alpha = Float.NaN
            "density" -> density = Density(0f)
            "fontScale" -> density = Density(3f, Float.NaN)
        }
        val uncached: Bitmap = mock()

        // When
        val first = capture(alpha, filter, density) { uncached }
        state = rendererState()
        val next: Bitmap = mock()
        val second = capture { next }

        // Then
        assertThat(first?.bitmap).isSameAs(uncached)
        assertThat(first?.isShared).isFalse()
        assertThat(second?.bitmap).isSameAs(next)
        verify(old, never()).recycle()
    }

    @Test
    fun `M avoid caching a renderer change during capture W capture`() {
        // Given
        val before = state
        val unstable: Bitmap = mock()
        val stable: Bitmap = mock()
        capture {
            state = state!!.copy(autoMirror = true)
            unstable
        }
        state = before

        // When + Then
        assertThat(capture { stable }?.bitmap).isSameAs(stable)
        assertThat(capture { error("Stable output must reuse") }?.bitmap).isSameAs(stable)
    }

    @Test
    fun `M avoid caching a replaced painter source W capture`() {
        // Given
        val before = source
        val unstable: Bitmap = mock()
        capture {
            source = sourceBitmap()
            unstable
        }
        source = before
        val stable: Bitmap = mock()

        // When + Then
        assertThat(capture { stable }?.bitmap).isSameAs(stable)
    }

    @Test
    fun `M recapture recycled output W capture`() {
        // Given
        val first: Bitmap = mock()
        capture { first }
        whenever(first.isRecycled).thenReturn(true)
        val second: Bitmap = mock()

        // When + Then
        assertThat(capture { second }?.bitmap).isSameAs(second)
    }

    @Test
    fun `M bound painter bookkeeping without recycling output W capture`() {
        // Given
        val cache = cache(1)
        val first: Bitmap = mock()
        cache.capture(painter, source, 1f, null, Density(3f), LayoutDirection.Ltr) { first }
        cache.capture(mock(), source, 1f, null, Density(3f), LayoutDirection.Ltr) { mock() }
        val updated: Bitmap = mock()

        // When
        val result = cache.capture(painter, source, 1f, null, Density(3f), LayoutDirection.Ltr) { updated }

        // Then
        assertThat(result?.bitmap).isSameAs(updated)
        verify(first, never()).recycle()
    }

    @Test
    fun `M keep failures out of the cache W capture`() {
        // When + Then
        assertThrows<IllegalStateException> { capture { error("render failed") } }
        assertThat(capture { null }).isNull()
        val pixels: Bitmap = mock()
        assertThat(capture { pixels }?.bitmap).isSameAs(pixels)
    }

    private fun capture(
        alpha: Float = 1f,
        filter: ColorFilter? = null,
        density: Density = Density(3f),
        direction: LayoutDirection = LayoutDirection.Ltr,
        render: () -> Bitmap?
    ): VectorPainterPixels? = testedCache.capture(painter, source, alpha, filter, density, direction, render)

    private fun cache(limit: Int = 64): ComposeVectorPainterBitmapCache = ComposeVectorPainterBitmapCache(
        stateOf = { state },
        sourceOf = { source },
        tintOf = { tints[it] },
        maxEntries = limit
    )

    private fun rendererState(): VectorPainterState = VectorPainterState(false, Size(24f, 24f), 123L, null, null)

    private fun sourceBitmap(): Bitmap = mock<Bitmap>().also {
        whenever(it.width).thenReturn(72)
        whenever(it.height).thenReturn(72)
        whenever(it.generationId).thenReturn(1)
        whenever(it.config).thenReturn(Bitmap.Config.ARGB_8888)
    }
}
