/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultBitmapSignatureGeneratorTest {

    @Mock
    private lateinit var mockBitmap: Bitmap

    private lateinit var testedGenerator: DefaultBitmapSignatureGenerator

    @IntForgery(min = 1, max = 100)
    var fakeWidth: Int = 0

    @IntForgery(min = 1, max = 100)
    var fakeHeight: Int = 0

    @BeforeEach
    fun setup() {
        testedGenerator = DefaultBitmapSignatureGenerator()
    }

    @Test
    fun `M hash complete pixel content W generateSignature { bounded bulk reads }`() {
        val fakeWidth = 19
        val fakeHeight = 37
        val stubBitmap = bitmap(fakeWidth, fakeHeight) { x, y -> x + y * fakeWidth }
        val fakeDigest = MessageDigest.getInstance("SHA-256")
        fakeDigest.update(ByteBuffer.allocate(8).putInt(fakeWidth).putInt(fakeHeight).array())
        val fakePixels = ByteBuffer.allocate(fakeWidth * fakeHeight * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(fakeWidth * fakeHeight) { fakePixels.putInt(it) }
        fakeDigest.update(fakePixels.array())

        val fakeSignature = testedGenerator.generateSignature(stubBitmap)

        assertThat(fakeSignature).isEqualTo(ByteBuffer.wrap(fakeDigest.digest()).long)
        verify(stubBitmap, never()).getPixel(any(), any())
    }

    @Test
    fun `M distinguish different glyphs W only an unsampled pixel changes`() {
        // The old 16-by-16 grid on a 64px bitmap never reads (1, 1). Real list labels such
        // as License 3 and License 9 can differ only between sampled columns in the glyph region.
        val stubFirst = bitmap(64, 64) { _, _ -> 0 }
        val stubSecond = bitmap(64, 64) { x, y -> if (x == 1 && y == 1) -1 else 0 }

        assertThat(testedGenerator.generateSignature(stubFirst))
            .isNotEqualTo(testedGenerator.generateSignature(stubSecond))
    }

    @Test
    fun `M reuse complete fingerprint W bitmap generation and metadata are unchanged`() {
        val stubBitmap = bitmap(64, 64) { x, y -> x + y }

        val fakeFirst = testedGenerator.generateSignature(stubBitmap)
        val fakeSecond = testedGenerator.generateSignature(stubBitmap)

        assertThat(fakeSecond).isEqualTo(fakeFirst)
        verify(stubBitmap, times(1)).getPixels(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `M invalidate fingerprint W the same bitmap pixels change`() {
        var fakeGeneration = 1
        var fakePixel = 0
        val stubBitmap = bitmap(64, 64) { x, y -> if (x == 1 && y == 1) fakePixel else 0 }
        whenever(stubBitmap.generationId).thenAnswer { fakeGeneration }
        val fakeBefore = testedGenerator.generateSignature(stubBitmap)

        fakeGeneration++
        fakePixel = -1
        val fakeAfter = testedGenerator.generateSignature(stubBitmap)

        assertThat(fakeAfter).isNotEqualTo(fakeBefore)
        verify(stubBitmap, times(2)).getPixels(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `M reject unstable fingerprint W drawing changes the bitmap during hashing`() {
        var fakeGeneration = 1
        val stubBitmap = bitmap(64, 64, onRead = { fakeGeneration++ }) { _, _ -> 0 }
        whenever(stubBitmap.generationId).thenAnswer { fakeGeneration }

        assertThat(testedGenerator.generateSignature(stubBitmap)).isNull()
    }

    @Test
    fun `M retain cache reuse W different bitmaps have identical complete contents`() {
        val stubFirst = bitmap(71, 39) { x, y -> x xor y }
        val stubSecond = bitmap(71, 39) { x, y -> x xor y }

        assertThat(testedGenerator.generateSignature(stubFirst))
            .isEqualTo(testedGenerator.generateSignature(stubSecond))
    }

    @Test
    fun `M distinguish dimensions W flattened pixel contents are identical`() {
        val stubFirst = bitmap(64, 32) { _, _ -> 0 }
        val stubSecond = bitmap(32, 64) { _, _ -> 0 }

        assertThat(testedGenerator.generateSignature(stubFirst))
            .isNotEqualTo(testedGenerator.generateSignature(stubSecond))
    }

    @Test
    fun `M bound temporary pixel storage W bitmap is wider than one tile`() {
        var fakeLargestRead = 0
        val stubWideBitmap = bitmap(20_000, 3, onRead = { fakeLargestRead = maxOf(fakeLargestRead, it) }) { x, y ->
            x + y
        }

        assertThat(testedGenerator.generateSignature(stubWideBitmap)).isNotNull()
        assertThat(fakeLargestRead).isLessThanOrEqualTo(16_384)
    }

    private fun bitmap(
        width: Int,
        height: Int,
        onRead: (Int) -> Unit = {},
        pixel: (Int, Int) -> Int
    ): Bitmap {
        val stubBitmap: Bitmap = mock()
        whenever(stubBitmap.width).thenReturn(width)
        whenever(stubBitmap.height).thenReturn(height)
        whenever(stubBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        doAnswer { call ->
            val destination = call.getArgument<IntArray>(0)
            val offset = call.getArgument<Int>(1)
            val stride = call.getArgument<Int>(2)
            val left = call.getArgument<Int>(3)
            val top = call.getArgument<Int>(4)
            val columns = call.getArgument<Int>(5)
            val rows = call.getArgument<Int>(6)
            onRead(destination.size)
            repeat(rows) { row ->
                repeat(columns) { column ->
                    destination[offset + row * stride + column] = pixel(left + column, top + row)
                }
            }
            null
        }.whenever(stubBitmap).getPixels(any(), any(), any(), any(), any(), any(), any())
        return stubBitmap
    }

    @Test
    fun `M return null W generateSignature { bitmap is recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        val result = testedGenerator.generateSignature(mockBitmap)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W generateSignature { bitmap has zero width }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(0)
        whenever(mockBitmap.height).thenReturn(fakeHeight)

        // When
        val result = testedGenerator.generateSignature(mockBitmap)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W generateSignature { bitmap has zero height }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(0)

        // When
        val result = testedGenerator.generateSignature(mockBitmap)

        // Then
        assertThat(result).isNull()
    }
}
