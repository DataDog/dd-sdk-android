/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PathUtilsTest {
    private lateinit var testedUtils: PathUtils

    @Mock
    private lateinit var mockLogger: InternalLogger

    @Mock
    private lateinit var mockCanvasWrapper: CanvasWrapper

    @Mock
    private lateinit var mockBitmapWrapper: BitmapWrapper

    @Mock
    private lateinit var mockPath: Path

    @Mock
    private lateinit var mockBounds: Rect

    @Mock
    private lateinit var mockBitmap: Bitmap

    @Mock
    private lateinit var mockCanvas: Canvas

    @LongForgery(min = 0xffffffff)
    var fakeFillColor: Long = 0L

    @LongForgery(min = 0xffffffff)
    var fakeCheckmarkColor: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockPath.getBounds())
            .thenReturn(mockBounds)

        testedUtils = PathUtils(
            logger = mockLogger,
            canvasWrapper = mockCanvasWrapper,
            bitmapWrapper = mockBitmapWrapper
        )
    }

    @Test
    fun `M return input value W convertRgbaToArgb() { length lt 2 }`() {
        // Then
        assertThat(testedUtils.convertRgbaToArgb("#")).isEqualTo("#")
    }

    @Test
    fun `M move alpha value W convertRgbaToArgb()`() {
        // Then
        assertThat(testedUtils.convertRgbaToArgb("#000000FF")).isEqualTo("#FF000000")
    }

    @Test
    fun `M return null W convertPathToBitmap() { failed to create bitmap }`() {
        // Given
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(null)

        // When
        val result = testedUtils.convertPathToBitmap(
            checkPath = mockPath,
            fillColor = fakeFillColor.toInt(),
            checkmarkColor = fakeCheckmarkColor.toInt()
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W convertPathToBitmap() { failed to create canvas }`() {
        // Given
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(null)

        // When
        val result = testedUtils.convertPathToBitmap(
            checkPath = mockPath,
            fillColor = fakeFillColor.toInt(),
            checkmarkColor = fakeCheckmarkColor.toInt()
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return bitmap W convertPathToBitmap() { success }`() {
        // Given
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(mockBitmap)

        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(mockCanvas)

        // When
        val result = testedUtils.convertPathToBitmap(
            checkPath = mockPath,
            fillColor = fakeFillColor.toInt(),
            checkmarkColor = fakeCheckmarkColor.toInt()
        )

        // Then
        assertThat(result).isEqualTo(mockBitmap)
    }
}
