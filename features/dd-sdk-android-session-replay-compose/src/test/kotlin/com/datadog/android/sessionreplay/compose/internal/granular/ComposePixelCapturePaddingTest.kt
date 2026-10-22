/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ComposePixelCapturePaddingTest {
    @ParameterizedTest
    @CsvSource("2,5,16,40", "10,27,20,54", "14,37,28,74", "1,3,16,48")
    fun `M preserve visible pixels and scale W pad { short capture }`(
        heightDp: Long,
        heightPx: Int,
        paddedHeightDp: Long,
        paddedHeightPx: Int
    ) {
        // Given
        val source: Bitmap = mock()
        val padded: Bitmap = mock()
        whenever(source.width).thenReturn(996)
        whenever(source.height).thenReturn(heightPx)
        whenever(source.density).thenReturn(420)
        val bounds = CapturedBounds(16, 194, 379, heightDp)
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> {
                Bitmap.createBitmap(996, paddedHeightPx, Bitmap.Config.ARGB_8888)
            }.thenReturn(padded)
            mockConstruction(Canvas::class.java).use { canvases ->
                // When
                val result = ComposePixelCapturePadding.pad(source, bounds)

                // Then - original crop remains at (0,0); the clip removes only added transparent rows.
                assertThat(result.bitmap).isSameAs(padded)
                assertThat(result.bounds).isEqualTo(bounds.copy(height = paddedHeightDp))
                assertThat(result.clip).isEqualTo(CapturedClip(bottom = paddedHeightDp - heightDp))
                assertThat(paddedHeightPx.toDouble() / paddedHeightDp).isEqualTo(heightPx.toDouble() / heightDp)
                verify(canvases.constructed().single()).drawBitmap(source, 0f, 0f, null)
                verify(padded).setDensity(420)
                verify(padded).setHasAlpha(true)
                verify(padded, never()).recycle()
                verify(source, never()).recycle()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [16, 24, 100])
    fun `M reuse original pixels W pad { normal capture }`(height: Long) {
        // Given
        val source: Bitmap = mock()
        whenever(source.width).thenReturn(100)
        whenever(source.height).thenReturn(height.toInt())
        val bounds = CapturedBounds(16, 194, 100, height)
        mockStatic(Bitmap::class.java).use { factory ->
            // When
            val result = ComposePixelCapturePadding.pad(source, bounds)

            // Then
            assertThat(result).isEqualTo(PaddedComposePixelCapture(source, bounds))
            factory.verifyNoInteractions()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M release incomplete padding and propagate failure W pad`(fatal: Boolean) {
        // Given
        val source: Bitmap = mock()
        val padded: Bitmap = mock()
        whenever(source.width).thenReturn(996)
        whenever(source.height).thenReturn(5)
        val failure = if (fatal) OutOfMemoryError("simulated") else IllegalStateException("simulated")
        mockStatic(Bitmap::class.java).use { factory ->
            factory.`when`<Bitmap> { Bitmap.createBitmap(996, 40, Bitmap.Config.ARGB_8888) }.thenReturn(padded)
            mockConstruction(Canvas::class.java) { canvas, _ ->
                doThrow(failure).whenever(canvas).drawBitmap(source, 0f, 0f, null)
            }.use {
                // When + Then
                assertThatThrownBy {
                    ComposePixelCapturePadding.pad(source, CapturedBounds(16, 194, 379, 2))
                }.isSameAs(failure)
                verify(padded).recycle()
                verify(source, never()).recycle()
            }
        }
    }
}
