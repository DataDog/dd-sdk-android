/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.datadog.android.sessionreplay.utils.GlobalBounds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ComposeSourceImageClipperTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M propagate drawing failures and release unfinished output W clip`(fatal: Boolean) {
        // Given
        val source: Bitmap = mock()
        whenever(source.width).thenReturn(160)
        whenever(source.height).thenReturn(160)
        val output: Bitmap = mock()
        val failure = if (fatal) OutOfMemoryError("simulated") else IllegalStateException("simulated")
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888) }.thenReturn(output)
            mockConstruction(Canvas::class.java) { canvas, _ ->
                doThrow(failure).`when`(canvas).drawBitmap(eq(source), isNull<Rect>(), any<RectF>(), any<Paint>())
            }.use {
                // When + Then
                assertThatThrownBy {
                    DefaultComposeSourceImageClipper().clip(
                        source,
                        GlobalBounds(-80, 0, 320, 160),
                        GlobalBounds(0, 0, 160, 160),
                        32f,
                        1f
                    )
                }.isSameAs(failure)
                verify(output).recycle()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject invalid or oversized output before allocation W clip`(oversized: Boolean) {
        // Given
        val source: Bitmap = mock()
        val bounds = if (oversized) GlobalBounds(0, 0, 50_000, 50_000) else GlobalBounds(0, 0, 160, 160)
        mockStatic(Bitmap::class.java).use { factory ->
            // When
            val result = DefaultComposeSourceImageClipper().clip(source, bounds, bounds, 32f, if (oversized) 1f else 0f)

            // Then
            assertThat(result).isNull()
            factory.verifyNoInteractions()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["scroll", "radius", "scale", "generation"])
    fun `M reuse only equivalent image crops W clip`(change: String) {
        // Given
        val source: Bitmap = mock()
        val output: Bitmap = mock()
        whenever(source.width).thenReturn(320)
        whenever(source.height).thenReturn(160)
        whenever(source.generationId).thenReturn(1)
        var allocations = 0
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888) }.thenAnswer {
                allocations++
                output
            }
            mockConstruction(Canvas::class.java).use {
                val clipper = DefaultComposeSourceImageClipper()
                clipper.clip(source, GlobalBounds(-80, 0, 320, 160), GlobalBounds(0, 0, 160, 160), 32f, 1f)
                if (change == "generation") whenever(source.generationId).thenReturn(2)

                // When - absolute movement is equivalent; crop geometry and pixels are not.
                val result = clipper.clip(
                    source,
                    GlobalBounds(if (change == "scale") -70 else -80, 60, 320, 160),
                    GlobalBounds(0, 60, 160, 160),
                    if (change == "radius") 16f else 32f,
                    1f
                )

                // Then
                assertThat(result).isSameAs(output)
                assertThat(allocations).isEqualTo(if (change == "scroll") 1 else 2)
                verify(source, org.mockito.kotlin.never()).recycle()
                verify(output, org.mockito.kotlin.never()).recycle()
            }
        }
    }
}
