/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ComposeDrawingResolverTest {
    private val testedResolver = DefaultComposeDrawingResolver()

    @Test
    fun `M preserve radio rings and hairlines W resolve() { no host pixels or OCR }`() {
        // Given
        val modifier = Modifier.drawBehind {
            drawCircle(Color.Red, 9f, Offset(10f, 10f), style = Stroke(2f))
            drawCircle(Color.Red, 4f, Offset(10f, 10f))
            drawLine(Color.Blue, Offset.Zero, Offset(20f, 0f), strokeWidth = 1f)
        }
        val coordinates: LayoutCoordinates = mock()
        whenever(coordinates.isAttached).thenReturn(true)
        whenever(coordinates.size).thenReturn(IntSize(20, 0))
        val info: ModifierInfo = mock()
        whenever(info.modifier).thenReturn(modifier)
        whenever(info.coordinates).thenReturn(coordinates)
        val layout: LayoutInfo = mock()
        whenever(layout.density).thenReturn(Density(1f))
        whenever(layout.layoutDirection).thenReturn(LayoutDirection.Ltr)
        val node: SemanticsNode = mock()
        whenever(node.layoutInfo).thenReturn(layout)

        // When
        val result = testedResolver.resolve(node, info)!!

        // Then - the stroked ring expands by half its stroke; a zero-height Canvas still paints its line.
        assertThat(result.before).hasSize(3)
        assertThat(result.before[0].position).isEqualTo(Offset.Zero)
        assertThat(result.before[0].size).isEqualTo(Size(20f, 20f))
        assertThat(result.before[0].border!!.width).isEqualTo(2L)
        assertThat(result.before[0].style.backgroundColor).isNull()
        assertThat(result.before[1].size).isEqualTo(Size(8f, 8f))
        assertThat(result.before[2].size).isEqualTo(Size(20f, 1f))
    }

    @Test
    fun `M identify decoration only leaves W isIndicationOnlyModifierList() { Material navigation }`() {
        // Given - the element list exposed by the current Material navigation indicator.
        val indication = listOf(
            "androidx.compose.ui.layout.LayoutIdElement",
            "androidx.compose.ui.graphics.GraphicsLayerElement",
            "androidx.compose.foundation.IndicationModifierElement"
        )

        // When / Then - skip only this decoration; keep independent drawing and image content.
        assertThat(isIndicationOnlyModifierList(indication)).isTrue()
        assertThat(isIndicationOnlyModifierList(indication + "androidx.compose.foundation.BackgroundElement")).isFalse()
        assertThat(isIndicationOnlyModifierList(indication + "androidx.compose.ui.draw.DrawWithCacheElement")).isFalse()
        assertThat(isIndicationOnlyModifierList(indication + "androidx.compose.ui.draw.PainterElement")).isFalse()
        assertThat(isIndicationOnlyModifierList(indication.dropLast(1))).isFalse()
    }

    @Test
    fun `M retain both cached gradient fills W resolve() { child content stays separate }`() {
        // Given
        val modifier = Modifier.drawWithCache {
            val top = Brush.linearGradient(
                0f to Color.Red,
                0.724f to Color.Transparent,
                start = Offset(size.width / 2, 0f),
                end = Offset(0f, size.height)
            )
            val bottom = Brush.linearGradient(0.2552f to Color.Transparent, 1f to Color.Blue)
            onDrawBehind { drawRect(top); drawRect(bottom) }
        }
        val coordinates: LayoutCoordinates = mock()
        whenever(coordinates.isAttached).thenReturn(true)
        whenever(coordinates.size).thenReturn(IntSize(1080, 2640))
        val info: ModifierInfo = mock()
        whenever(info.modifier).thenReturn(modifier)
        whenever(info.coordinates).thenReturn(coordinates)
        val layout: LayoutInfo = mock()
        whenever(layout.density).thenReturn(Density(3f))
        whenever(layout.layoutDirection).thenReturn(LayoutDirection.Ltr)
        val node: SemanticsNode = mock()
        whenever(node.layoutInfo).thenReturn(layout)

        // When
        val result = testedResolver.resolve(node, info)!!

        // Then - no screenshot or child draw is needed to preserve the two gradient operations.
        assertThat(result.before).hasSize(2)
        assertThat(result.after).isEmpty()
        val first = result.before.first().style.backgroundGradient!!
        assertThat(first.stops.first().color).isEqualTo("#ff0000ff")
        assertThat(first.stops.last().offset).isBetween(0.723, 0.725)
        assertThat(first.startPoint.x).isEqualTo(0.5)
        assertThat(first.endPoint.y).isEqualTo(1.0)
    }

    @Test
    fun `M rebuild cache with current geometry W drawingFor() { inspector information is disabled }`() {
        // Given
        var cachedSize = Size.Zero
        var cachedDensity = 0f
        var drawn = false
        val modifier = Modifier.drawWithCache {
            cachedSize = size
            cachedDensity = density
            onDrawBehind { drawn = true }
        }
        val scope: ContentDrawScope = mock()

        // When
        val drawing = testedResolver.drawingFor(modifier, Density(3f), LayoutDirection.Ltr, Size(1080f, 2640f))
        drawing!!(scope)

        // Then
        assertThat(cachedSize).isEqualTo(Size(1080f, 2640f))
        assertThat(cachedDensity).isEqualTo(3f)
        assertThat(drawn).isTrue()
        verify(scope).drawContent()
    }

    @Test
    fun `M resolve direct drawing callbacks W drawingFor()`() {
        // Given
        var behind = false
        var content = false
        val scope: ContentDrawScope = mock()
        val modifiers = listOf(
            Modifier.drawBehind { behind = true },
            Modifier.drawWithContent { content = true; drawContent() }
        )

        // When
        modifiers.forEach { modifier ->
            testedResolver.drawingFor(modifier, Density(1f), LayoutDirection.Ltr, Size(30f, 20f))!!(scope)
        }

        // Then
        assertThat(behind).isTrue()
        assertThat(content).isTrue()
    }
}
