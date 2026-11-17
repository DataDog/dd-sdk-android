/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.datadog.android.internal.system.BuildSdkVersionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class SoftwareDrawableResolverTest {

    private val mockBuildSdkVersionProvider: BuildSdkVersionProvider = mock()
    private val testedResolver = SoftwareDrawableResolver(mockBuildSdkVersionProvider)

    private fun hardwareDrawable(widthPx: Int, heightPx: Int, softwareCopy: Bitmap? = null): BitmapDrawable {
        val mockBitmap: Bitmap = mock()
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.HARDWARE)
        whenever(mockBitmap.width).thenReturn(widthPx)
        whenever(mockBitmap.height).thenReturn(heightPx)
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(softwareCopy)
        val mockDrawable: BitmapDrawable = mock()
        whenever(mockDrawable.bitmap).thenReturn(mockBitmap)
        whenever(mockDrawable.copyBounds()).thenReturn(Rect())
        return mockDrawable
    }

    @Test
    fun `M return the drawable W resolve() { not a bitmap drawable }`() {
        // Given
        val mockDrawable: Drawable = mock()

        // When
        val result = testedResolver.resolve(mockDrawable)

        // Then
        assertThat(result).isSameAs(mockDrawable)
    }

    @Test
    fun `M return the drawable W resolve() { software bitmap }`() {
        // Given
        val mockBitmap: Bitmap = mock()
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        val mockDrawable: BitmapDrawable = mock()
        whenever(mockDrawable.bitmap).thenReturn(mockBitmap)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)

        // When
        val result = testedResolver.resolve(mockDrawable)

        // Then
        assertThat(result).isSameAs(mockDrawable)
        verify(mockBitmap, never()).copy(any(), any())
    }

    @Test
    fun `M draw a software copy W resolve() { hardware bitmap }`() {
        // Given
        val mockSoftwareCopy: Bitmap = mock()
        val mockDrawable = hardwareDrawable(widthPx = 100, heightPx = 100, softwareCopy = mockSoftwareCopy)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)

        // When
        val result = testedResolver.resolve(mockDrawable)

        // Then
        assertThat(result).isNotNull().isNotSameAs(mockDrawable).isInstanceOf(BitmapDrawable::class.java)
        verify(checkNotNull(mockDrawable.bitmap)).copy(Bitmap.Config.ARGB_8888, false)
    }

    @Test
    fun `M keep the target density W resolve() { hardware bitmap }`() {
        // Given: a 240 px bitmap of 480 dpi that the original shows at 160 px, a target density of 320 dpi.
        val mockSoftwareCopy: Bitmap = mock()
        whenever(mockSoftwareCopy.density).thenReturn(480)
        whenever(mockSoftwareCopy.width).thenReturn(240)
        whenever(mockSoftwareCopy.height).thenReturn(120)
        val mockDrawable = hardwareDrawable(widthPx = 240, heightPx = 120, softwareCopy = mockSoftwareCopy)
        whenever(mockDrawable.intrinsicWidth).thenReturn(160)
        whenever(mockDrawable.intrinsicHeight).thenReturn(80)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)

        // When
        mockConstruction(BitmapDrawable::class.java).use { construction ->
            testedResolver.resolve(mockDrawable)

            // Then
            verify(construction.constructed().single()).setTargetDensity(320)
        }
    }

    @Test
    fun `M keep the default density W resolve() { bitmap without a density }`() {
        // Given
        val mockSoftwareCopy: Bitmap = mock()
        whenever(mockSoftwareCopy.density).thenReturn(Bitmap.DENSITY_NONE)
        whenever(mockSoftwareCopy.width).thenReturn(240)
        whenever(mockSoftwareCopy.height).thenReturn(120)
        val mockDrawable = hardwareDrawable(widthPx = 240, heightPx = 120, softwareCopy = mockSoftwareCopy)
        whenever(mockDrawable.intrinsicWidth).thenReturn(160)
        whenever(mockDrawable.intrinsicHeight).thenReturn(80)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)

        // When
        mockConstruction(BitmapDrawable::class.java).use { construction ->
            testedResolver.resolve(mockDrawable)

            // Then
            verify(construction.constructed().single(), never()).setTargetDensity(any<Int>())
        }
    }

    @Test
    fun `M return null W resolve() { hardware bitmap over the raster budget }`() {
        // Given
        val mockDrawable = hardwareDrawable(widthPx = 4000, heightPx = 4000, softwareCopy = mock())
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)

        // When
        val result = testedResolver.resolve(mockDrawable)

        // Then
        assertThat(result).isNull()
        verify(checkNotNull(mockDrawable.bitmap), never()).copy(any(), any())
    }

    @Test
    fun `M return null W resolve() { hardware bitmap cannot be copied }`() {
        // Given
        val mockDrawable = hardwareDrawable(widthPx = 100, heightPx = 100, softwareCopy = null)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)

        // When
        val result = testedResolver.resolve(mockDrawable)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return the drawable W resolve() { before API 26 }`() {
        // Given
        val mockDrawable = hardwareDrawable(widthPx = 100, heightPx = 100)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(false)

        // When
        val result = testedResolver.resolve(mockDrawable)

        // Then
        assertThat(result).isSameAs(mockDrawable)
    }
}
