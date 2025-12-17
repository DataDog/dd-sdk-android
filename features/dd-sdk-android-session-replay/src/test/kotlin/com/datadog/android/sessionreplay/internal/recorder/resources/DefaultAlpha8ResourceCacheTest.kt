/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import androidx.collection.LruCache
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultAlpha8ResourceCacheTest {

    @Mock
    private lateinit var mockSignatureGenerator: BitmapSignatureGenerator

    @Mock
    private lateinit var mockCache: LruCache<Alpha8CacheKey, String>

    @Mock
    private lateinit var mockBitmap: Bitmap

    @LongForgery
    var fakeSignature: Long = 0L

    @StringForgery
    lateinit var fakeResourceId: String

    private lateinit var testedCache: DefaultAlpha8ResourceCache

    @BeforeEach
    fun setup() {
        whenever(mockBitmap.width).thenReturn(100)
        whenever(mockBitmap.height).thenReturn(100)
        whenever(mockSignatureGenerator.generateSignature(mockBitmap)).thenReturn(fakeSignature)

        testedCache = DefaultAlpha8ResourceCache(mockSignatureGenerator, mockCache)
    }

    // region generateKey

    @Test
    fun `M return null W generateKey { signature generation fails }`() {
        // Given
        whenever(mockSignatureGenerator.generateSignature(mockBitmap)).thenReturn(null)

        // When
        val result = testedCache.generateKey(mockBitmap)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return cache key W generateKey { signature generated successfully }`() {
        // Given
        val expectedKey = Alpha8CacheKey(100, 100, fakeSignature)

        // When
        val result = testedCache.generateKey(mockBitmap)

        // Then
        assertThat(result).isEqualTo(expectedKey)
    }

    // endregion

    // region get

    @Test
    fun `M return cached resourceId W get { key found in cache }`() {
        // Given
        val key = Alpha8CacheKey(100, 100, fakeSignature)
        whenever(mockCache[key]).thenReturn(fakeResourceId)

        // When
        val result = testedCache.get(key)

        // Then
        assertThat(result).isEqualTo(fakeResourceId)
    }

    @Test
    fun `M return null W get { key not in cache }`() {
        // Given
        val key = Alpha8CacheKey(100, 100, fakeSignature)
        whenever(mockCache[key]).thenReturn(null)

        // When
        val result = testedCache.get(key)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region put

    @Test
    fun `M cache resourceId W put`() {
        // Given
        val key = Alpha8CacheKey(100, 100, fakeSignature)

        // When
        testedCache.put(key, fakeResourceId)

        // Then
        verify(mockCache).put(key, fakeResourceId)
    }

    // endregion

    // region onTrimMemory

    @Test
    fun `M evict all W onTrimMemory { TRIM_MEMORY_BACKGROUND }`() {
        // When
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

        // Then
        verify(mockCache).evictAll()
    }

    @Test
    fun `M evict all W onTrimMemory { TRIM_MEMORY_COMPLETE }`() {
        // When
        @Suppress("DEPRECATION")
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        // Then
        verify(mockCache).evictAll()
    }

    @Test
    fun `M evict all W onTrimMemory { TRIM_MEMORY_RUNNING_CRITICAL }`() {
        // When
        @Suppress("DEPRECATION")
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        // Then
        verify(mockCache).evictAll()
    }

    @Test
    fun `M trim to 75 percent W onTrimMemory { TRIM_MEMORY_MODERATE }`() {
        // Given
        whenever(mockCache.maxSize()).thenReturn(100)

        // When
        @Suppress("DEPRECATION")
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)

        // Then
        verify(mockCache).trimToSize(75)
    }

    @Test
    fun `M trim to 75 percent W onTrimMemory { TRIM_MEMORY_RUNNING_MODERATE }`() {
        // Given
        whenever(mockCache.maxSize()).thenReturn(100)

        // When
        @Suppress("DEPRECATION")
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        // Then
        verify(mockCache).trimToSize(75)
    }

    @Test
    fun `M trim to 50 percent W onTrimMemory { TRIM_MEMORY_RUNNING_LOW }`() {
        // Given
        whenever(mockCache.maxSize()).thenReturn(100)

        // When
        @Suppress("DEPRECATION")
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        // Then
        verify(mockCache).trimToSize(50)
    }

    @Test
    fun `M do nothing W onTrimMemory { TRIM_MEMORY_UI_HIDDEN }`() {
        // When
        testedCache.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        // Then
        verify(mockCache, never()).evictAll()
        verify(mockCache, never()).trimToSize(any())
    }

    @Test
    fun `M evict all W onTrimMemory { unknown level }`() {
        // When
        testedCache.onTrimMemory(9999)

        // Then
        verify(mockCache).evictAll()
    }

    // endregion

    // region onLowMemory

    @Test
    fun `M evict all W onLowMemory`() {
        // When
        @Suppress("DEPRECATION")
        testedCache.onLowMemory()

        // Then
        verify(mockCache).evictAll()
    }

    // endregion
}
