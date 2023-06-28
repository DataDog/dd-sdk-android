/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.wrappers.BitmapWrapper
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DrawableUtilsTest {
    private lateinit var testedDrawableUtils: DrawableUtils

    @Mock
    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    private lateinit var mockDrawable: Drawable

    @Mock
    private lateinit var mockBitmapWrapper: BitmapWrapper

    @Mock
    private lateinit var fakeBitmap: Bitmap

    @BeforeEach
    fun setup() {
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(fakeBitmap)
        testedDrawableUtils = DrawableUtils(mockBitmapWrapper)
    }

    @Test
    fun `M set width to 1 W createBitmapFromDrawable() { with width 0 }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(0)
        whenever(mockDrawable.intrinsicHeight).thenReturn(0)

        val boundsCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        val result = testedDrawableUtils.createBitmapFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        verify(mockBitmapWrapper).createBitmap(
            displayMetrics = displayMetricsCaptor.capture(),
            bitmapWidth = boundsCaptor.capture(),
            bitmapHeight = boundsCaptor.capture(),
            config = any()
        )

        boundsCaptor.allValues.forEach {
            assertThat(it).isEqualTo(1)
        }

        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)

        assertThat(result).isEqualTo(fakeBitmap)
    }

    @Test
    fun `M set width to drawable intrinsic W createBitmapFromDrawable()`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(200)
        whenever(mockDrawable.intrinsicHeight).thenReturn(200)

        val boundsCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()

        // When
        val result = testedDrawableUtils.createBitmapFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        verify(mockBitmapWrapper).createBitmap(
            displayMetrics = displayMetricsCaptor.capture(),
            bitmapWidth = boundsCaptor.capture(),
            bitmapHeight = boundsCaptor.capture(),
            config = any()
        )

        boundsCaptor.allValues.forEach {
            assertThat(it).isEqualTo(200)
        }

        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)

        assertThat(result).isEqualTo(fakeBitmap)
    }

    @Test
    fun `M return null W createBitmapFromDrawable() { failed to create bmp }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(200)
        whenever(mockDrawable.intrinsicHeight).thenReturn(200)
        whenever(mockBitmapWrapper.createBitmap(any(), any(), any(), any()))
            .thenReturn(null)

        // When
        val result = testedDrawableUtils.createBitmapFromDrawable(mockDrawable, mockDisplayMetrics)

        // Then
        assertThat(result).isNull()
    }
}
