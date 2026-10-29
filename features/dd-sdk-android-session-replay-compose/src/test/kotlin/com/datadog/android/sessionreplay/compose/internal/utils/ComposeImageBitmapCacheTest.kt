/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import com.datadog.android.internal.system.BuildSdkVersionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ComposeImageBitmapCacheTest {
    private val testedCache = ComposeImageBitmapCache<Int>()

    @Test
    fun `M reuse unchanged pixels W getOrCreate`() {
        // Given
        val source = sourceBitmap()
        val pixels: Bitmap = mock()
        var copies = 0
        val copy = { copies++; pixels }

        // When
        val first = testedCache.getOrCreate(source, 1, copy)
        val second = testedCache.getOrCreate(source, 1, copy)

        // Then
        assertThat(first).isSameAs(pixels)
        assertThat(second).isSameAs(pixels)
        assertThat(copies).isEqualTo(1)
        verify(source, never()).recycle()
        verify(pixels, never()).recycle()
    }

    @ParameterizedTest
    @ValueSource(strings = ["generation", "width", "height", "config", "alpha", "premultiplied", "density", "key"])
    fun `M recapture changed inputs W getOrCreate`(changed: String) {
        // Given
        val source = sourceBitmap()
        val first: Bitmap = mock()
        val updated: Bitmap = mock()
        testedCache.getOrCreate(source, 1) { first }
        when (changed) {
            "generation" -> whenever(source.generationId).thenReturn(2)
            "width" -> whenever(source.width).thenReturn(121)
            "height" -> whenever(source.height).thenReturn(81)
            "config" -> whenever(source.config).thenReturn(Bitmap.Config.RGB_565)
            "alpha" -> whenever(source.hasAlpha()).thenReturn(false)
            "premultiplied" -> whenever(source.isPremultiplied).thenReturn(false)
            "density" -> whenever(source.density).thenReturn(480)
        }

        // When
        val result = testedCache.getOrCreate(source, if (changed == "key") 2 else 1) { updated }

        // Then
        assertThat(result).isSameAs(updated)
        verify(first, never()).recycle()
    }

    @Test
    fun `M recapture recycled cached pixels W getOrCreate`() {
        // Given
        val source = sourceBitmap()
        val first: Bitmap = mock()
        val updated: Bitmap = mock()
        testedCache.getOrCreate(source, 1) { first }
        whenever(first.isRecycled).thenReturn(true)

        // When + Then
        assertThat(testedCache.getOrCreate(source, 1) { updated }).isSameAs(updated)
    }

    @Test
    fun `M avoid caching unstable copies W getOrCreate`() {
        // Given
        val source = sourceBitmap()
        val first: Bitmap = mock()
        val updated: Bitmap = mock()

        // When
        testedCache.getOrCreate(source, 1) {
            whenever(source.generationId).thenReturn(2)
            first
        }
        val result = testedCache.getOrCreate(source, 1) { updated }

        // Then
        assertThat(result).isSameAs(updated)
    }

    @Test
    fun `M evict entries without recycling pixels W getOrCreate`() {
        // Given
        val cache = ComposeImageBitmapCache<Int>(1)
        val source = sourceBitmap()
        val other = sourceBitmap()
        val pixels: Bitmap = mock()
        val updated: Bitmap = mock()
        cache.getOrCreate(source, 1) { pixels }
        cache.getOrCreate(other, 1) { mock() }

        // When + Then
        assertThat(cache.getOrCreate(source, 1) { updated }).isSameAs(updated)
        verify(source, never()).recycle()
        verify(pixels, never()).recycle()
    }

    @ParameterizedTest
    @ValueSource(strings = ["recycled", "empty", "failed copy"])
    fun `M reject unavailable pixels W getOrCreate`(unavailable: String) {
        // Given
        val source = sourceBitmap()
        if (unavailable == "recycled") whenever(source.isRecycled).thenReturn(true)
        if (unavailable == "empty") whenever(source.width).thenReturn(0)
        var copies = 0

        // When
        val result = testedCache.getOrCreate(source, 1) { copies++; null }

        // Then
        assertThat(result).isNull()
        assertThat(copies).isEqualTo(if (unavailable == "failed copy") 1 else 0)
    }

    @Test
    fun `M recapture color space changes W getOrCreate`() {
        // Given
        val provider: BuildSdkVersionProvider = mock()
        whenever(provider.isAtLeastO).thenReturn(true)
        val cache = ComposeImageBitmapCache<Int>(buildSdkVersionProvider = provider)
        val source = sourceBitmap()
        val first: Bitmap = mock()
        val updated: Bitmap = mock()
        cache.getOrCreate(source, 1) { first }
        whenever(source.colorSpace).thenReturn(mock())

        // When + Then
        assertThat(cache.getOrCreate(source, 1) { updated }).isSameAs(updated)
    }

    private fun sourceBitmap(): Bitmap = mock<Bitmap>().also {
        whenever(it.width).thenReturn(120)
        whenever(it.height).thenReturn(80)
        whenever(it.config).thenReturn(Bitmap.Config.ARGB_8888)
        whenever(it.generationId).thenReturn(1)
        whenever(it.hasAlpha()).thenReturn(true)
        whenever(it.isPremultiplied).thenReturn(true)
        whenever(it.density).thenReturn(320)
    }
}
