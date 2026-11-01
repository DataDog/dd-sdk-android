/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class MeasuredComposeTextTest {
    private val mockParagraph: Paragraph = mock()
    private val mockPaint: Paint = mock()
    private val fakeSize = IntSize(120, 40)

    @Test
    fun `M read original paint through the actual Compose getter W readPaint`() {
        // Given - use the real Compose classes and getter, with constructor-free test owners.
        val paragraphType = Class.forName("androidx.compose.ui.text.AndroidParagraph")
        val paragraph = org.mockito.Mockito.mock(paragraphType, org.mockito.Mockito.CALLS_REAL_METHODS) as Paragraph
        val intrinsicsField = paragraphType.getDeclaredField("paragraphIntrinsics").apply { isAccessible = true }
        val intrinsics = org.mockito.Mockito.mock(intrinsicsField.type, org.mockito.Mockito.CALLS_REAL_METHODS)
        val paintField = intrinsicsField.type.getDeclaredField("textPaint").apply { isAccessible = true }
        val paint = org.mockito.Mockito.mock(paintField.type) as Paint
        paintField.set(intrinsics, paint)
        intrinsicsField.set(paragraph, intrinsics)

        // When + Then
        assertThat(MeasuredComposeText.readPaint(paragraph)).isSameAs(paint)
    }

    @Test
    fun `M retain fallback for an unknown paragraph implementation W readPaint`() {
        // Given + When + Then
        assertThat(MeasuredComposeText.readPaint(mockParagraph)).isNull()
    }

    @Test
    fun `M match only current plain text and color W matches`() {
        // Given
        whenever(mockPaint.color).thenReturn(Color.Red.toArgb())
        val text = MeasuredComposeText(mockParagraph, "Current", TextStyle.Default, fakeSize, mockPaint)

        // When + Then
        assertThat(text.matches("Current", Color.Red.value)).isTrue()
        assertThat(text.matches("Previous", Color.Red.value)).isFalse()
        assertThat(text.matches("Current", Color.Blue.value)).isFalse()
        assertThat(text.matches("Current", Color.Unspecified.value)).isFalse()
        verifyNoInteractions(mockParagraph)
    }

    @Test
    fun `M reject mutable brush and shader paint W matches`() {
        // Given
        whenever(mockPaint.color).thenReturn(Color.Red.toArgb())
        val brush = MeasuredComposeText(
            mockParagraph,
            "Current",
            TextStyle(brush = Brush.linearGradient(listOf(Color.Red, Color.Blue))),
            fakeSize,
            mockPaint
        )
        val plain = MeasuredComposeText(mockParagraph, "Current", TextStyle.Default, fakeSize, mockPaint)
        val shader: Shader = mock()

        // When + Then
        assertThat(brush.matches("Current", Color.Red.value)).isFalse()
        whenever(mockPaint.shader).thenReturn(shader)
        assertThat(plain.matches("Current", Color.Red.value)).isFalse()
        verifyNoInteractions(mockParagraph)
    }

    @Test
    fun `M preserve application paint parameters W draw`() {
        // Given
        val text = MeasuredComposeText(mockParagraph, "Current", TextStyle.Default, fakeSize, mockPaint)
        val canvas: Canvas = mock()

        // When
        text.draw(canvas)

        // Then
        verify(mockParagraph).paint(canvas, Color.Unspecified, null, null, null)
        verifyNoInteractions(mockPaint)
    }
}
