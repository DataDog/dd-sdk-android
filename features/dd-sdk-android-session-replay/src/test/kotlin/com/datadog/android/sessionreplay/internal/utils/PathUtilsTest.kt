/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PathMeasure
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.HashGenerator
import com.datadog.android.sessionreplay.internal.utils.PathUtils.Companion.DEFAULT_MAX_PATH_LENGTH
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
    private lateinit var mockBitmapCachesManager: BitmapCachesManager

    @Mock
    private lateinit var mockBitmap: Bitmap

    @Mock
    private lateinit var mockCanvas: Canvas

    @LongForgery(min = 0xffffffff)
    var fakeCheckmarkColor: Long = 0L

    @Mock lateinit var mockGenerator: HashGenerator

    @Mock lateinit var mockPathMeasure: PathMeasure

    @Mock lateinit var mockPath: Path

    @StringForgery
    lateinit var fakeHash: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockPathMeasure.getPosTan(any(), any(), any()))
            .thenReturn(true)

        val fakeContourLength = forge.aFloat(min = DEFAULT_MAX_PATH_LENGTH.toFloat())
        whenever(mockPathMeasure.length).thenReturn(fakeContourLength)

        testedUtils = PathUtils(
            logger = mockLogger,
            canvasWrapper = mockCanvasWrapper,
            bitmapWrapper = mockBitmapWrapper,
            bitmapCachesManager = mockBitmapCachesManager,
            md5Generator = mockGenerator
        )
    }

    @Test
    fun `M return null W convertPathToBitmap() { failed to create bitmap }`(
        @IntForgery fakeWidth: Int,
        @IntForgery fakeHeight: Int,
        @IntForgery fakeStrokeWidth: Int
    ) {
        // Given
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(null)

        // When
        val result = testedUtils.convertPathToBitmap(
            checkPath = mockPath,
            desiredWidth = fakeWidth,
            desiredHeight = fakeHeight,
            strokeWidth = fakeStrokeWidth,
            checkmarkColor = fakeCheckmarkColor.toInt()
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W convertPathToBitmap() { failed to create canvas }`(
        @IntForgery fakeWidth: Int,
        @IntForgery fakeHeight: Int,
        @IntForgery fakeStrokeWidth: Int
    ) {
        // Given
        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(null)

        // When
        val result = testedUtils.convertPathToBitmap(
            checkPath = mockPath,
            desiredWidth = fakeWidth,
            desiredHeight = fakeHeight,
            strokeWidth = fakeStrokeWidth,
            checkmarkColor = fakeCheckmarkColor.toInt()
        )

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M release pixels before propagating allocation failure W convertPathToBitmap`(
        failDuringDrawing: Boolean,
        @IntForgery fakeWidth: Int,
        @IntForgery fakeHeight: Int,
        @IntForgery fakeStrokeWidth: Int
    ) {
        // Given
        val fakeFailure = OutOfMemoryError()
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull())).thenReturn(mockBitmap)
        if (failDuringDrawing) {
            whenever(mockCanvasWrapper.createCanvas(mockBitmap)).thenReturn(mockCanvas)
            whenever(mockCanvas.drawPath(any(), any())).thenThrow(fakeFailure)
        } else {
            whenever(mockCanvasWrapper.createCanvas(mockBitmap)).thenThrow(fakeFailure)
        }

        // When
        val actualFailure = assertThrows<OutOfMemoryError> {
            testedUtils.convertPathToBitmap(
                mockPath,
                fakeCheckmarkColor.toInt(),
                fakeWidth,
                fakeHeight,
                fakeStrokeWidth
            )
        }

        // Then
        assertThat(actualFailure).isSameAs(fakeFailure)
        if (failDuringDrawing) {
            inOrder(mockCanvas, mockBitmap) {
                verify(mockCanvas).setBitmap(null)
                verify(mockBitmap).recycle()
            }
        } else {
            verify(mockBitmap).recycle()
        }
        verifyNoInteractions(mockLogger)
    }

    @Test
    fun `M return bitmap W convertPathToBitmap() { success }`(
        @IntForgery fakeWidth: Int,
        @IntForgery fakeHeight: Int,
        @IntForgery fakeStrokeWidth: Int
    ) {
        // Given
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull()))
            .thenReturn(mockBitmap)

        whenever(mockCanvasWrapper.createCanvas(any()))
            .thenReturn(mockCanvas)

        // When
        val result = testedUtils.convertPathToBitmap(
            checkPath = mockPath,
            desiredWidth = fakeWidth,
            desiredHeight = fakeHeight,
            strokeWidth = fakeStrokeWidth,
            checkmarkColor = fakeCheckmarkColor.toInt()
        )

        // Then
        assertThat(result).isEqualTo(mockBitmap)
        verify(mockCanvas).setBitmap(null)
    }

    @Test
    fun `M return path W generateKeyForPath`() {
        // Given
        whenever(mockGenerator.generate(any())).thenReturn(fakeHash)
        whenever(mockPathMeasure.nextContour()).thenReturn(true)

        // When
        val result = testedUtils.generateKeyForPath(
            path = mockPath,
            pathMeasure = mockPathMeasure
        )

        // Then
        assertThat(result).isEqualTo(fakeHash)
    }

    @Test
    fun `M return null W generateKeyForPath { empty points }`() {
        // Given
        val emptyPoints = "0.0,0.0;"
        whenever(mockGenerator.generate(emptyPoints.toByteArray())).thenReturn("")
        whenever(mockPathMeasure.nextContour()).thenReturn(false)

        // When
        val result = testedUtils.generateKeyForPath(
            path = mockPath,
            pathMeasure = mockPathMeasure
        )

        // Then
        assertThat(result).isNull()
    }
}
