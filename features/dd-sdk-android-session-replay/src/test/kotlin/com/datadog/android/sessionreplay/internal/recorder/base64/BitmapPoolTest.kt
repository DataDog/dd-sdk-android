/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.Bitmap
import android.util.LruCache
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.base64.BitmapPool.MAX_CACHE_MEMORY_SIZE_BYTES
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BitmapPoolTest {
    private lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockConfig: Bitmap.Config

    private val testedCache = BitmapPool

    private var width: Int = 0
    private var height: Int = 0
    private lateinit var fakeKey: String

    private lateinit var internalCache: LruCache<String, Bitmap>

    @BeforeEach
    fun setup(forge: Forge) {
        internalCache = LruCache(MAX_CACHE_MEMORY_SIZE_BYTES)
        testedCache.setBackingCache(internalCache)

        width = forge.anInt(1, 200)
        height = forge.anInt(1, 200)
        fakeKey = "$width-$height-$mockConfig"

        mockBitmap = createMockBitmap(forge)

        testedCache.setUsedBitmaps(HashSet())
        testedCache.setBitmapsBySize(HashMap())
    }

    @Test
    fun `M return null W get() { pool does not have bitmaps of the right size }`() {
        // Given
        val bitmapOtherDimensions: Bitmap = mock()
        whenever(bitmapOtherDimensions.width).thenReturn(width + 10)
        testedCache.put(bitmapOtherDimensions)

        // When
        val bitmap = testedCache.getBitmapByProperties(width, height, mockConfig)

        // Then
        assertThat(bitmap).isNull()
    }

    @Test
    fun `M return a bitmap W get() { pool has free bitmap of the right size }`() {
        // Given
        testedCache.put(mockBitmap)

        // When
        val cacheItem = testedCache.getBitmapByProperties(width, height, mockConfig)

        // Then
        assertThat(cacheItem).isEqualTo(mockBitmap)
    }

    @Test
    fun `M return null W get() { bitmap in pool but it's already in use }`() {
        // Given
        testedCache.put(mockBitmap)
        testedCache.getBitmapByProperties(width, height, mockConfig)

        // When
        val cacheItem = testedCache.getBitmapByProperties(width, height, mockConfig)

        // Then
        assertThat(cacheItem).isNull()
    }

    // region put

    @Test
    fun `M return bitmaps W get() { according to what is in the pool }`(forge: Forge) {
        // Given
        testedCache.put(mockBitmap)

        val bitmapsBySize = HashMap<String, HashSet<Bitmap>>()
        bitmapsBySize[fakeKey] = hashSetOf(mockBitmap)
        testedCache.setBitmapsBySize(bitmapsBySize)

        val secondBitmap = createMockBitmap(forge)
        testedCache.put(secondBitmap)

        val expectedBitmaps = listOf(mockBitmap, secondBitmap)

        // When
        val firstResultFromPool = testedCache.get(fakeKey)
        val secondResultFromPool = testedCache.get(fakeKey)
        val thirdResultFromPool = testedCache.get(fakeKey)

        // Then
        assertThat(firstResultFromPool).isIn(expectedBitmaps)
        assertThat(secondResultFromPool).isIn(expectedBitmaps)
        assertThat(firstResultFromPool).isNotEqualTo(secondResultFromPool)
        assertThat(thirdResultFromPool).isNull()
    }

    @Test
    fun `M mark bitmap as free W put() { if bitmap already in the pool }`() {
        // Given
        val usedCache = hashSetOf(mockBitmap)
        testedCache.setUsedBitmaps(usedCache)

        val bitmapsBySize = HashMap<String, HashSet<Bitmap>>()
        bitmapsBySize[fakeKey] = hashSetOf(mockBitmap)
        testedCache.setBitmapsBySize(bitmapsBySize)

        // When
        testedCache.put(mockBitmap)

        // Then
        assertThat(usedCache).isEmpty()
    }

    @Test
    fun `M add to pool W put() { and bitmap not in pool }`() {
        // Given
        testedCache.setUsedBitmaps(HashSet())

        // When
        testedCache.put(mockBitmap)
        val actual = testedCache.getBitmapByProperties(width, height, mockConfig)

        // Then
        assertThat(actual).isEqualTo(mockBitmap)
    }

    @Test
    fun `M not allow immutable bitmaps W put()`() {
        // Given
        whenever(mockBitmap.isMutable).thenReturn(false)

        // When
        testedCache.put(mockBitmap)

        // Then
        assertThat(internalCache.size()).isEqualTo(0)
    }

    @Test
    fun `M not allow recycled bitmaps W put()`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        testedCache.put(mockBitmap)

        // Then
        assertThat(internalCache.size()).isEqualTo(0)
    }

    // endregion

    // concurrency region

    @Test
    fun `M get free bitmap only once W get() { multiple threads }`() {
        // Given
        val countDownLatch = CountDownLatch(3)
        testedCache.put(mockBitmap)
        var numBmpsRetrievedFromPool = 0

        // When
        repeat(3) {
            Thread {
                val result = testedCache.get(fakeKey)
                if (result != null) numBmpsRetrievedFromPool++
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(numBmpsRetrievedFromPool).isEqualTo(1)
    }

    @Test
    fun `M insert same bitmap only once W put() { multiple threads }`() {
        // Given
        val countDownLatch = CountDownLatch(3)

        // When
        repeat(3) {
            Thread {
                testedCache.put(mockBitmap)
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(internalCache.size()).isEqualTo(1)
        assertThat(internalCache.snapshot().values).contains(mockBitmap)
    }

    @Test
    fun `M insert multiple bitmaps W put() { multiple threads }`(forge: Forge) {
        // Given
        val countDownLatch = CountDownLatch(3)

        // When
        repeat(3) {
            Thread {
                testedCache.put(createMockBitmap(forge))
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(internalCache.size()).isEqualTo(3)
    }

    // endregion

    private fun createMockBitmap(forge: Forge): Bitmap {
        val bitmap: Bitmap = mock()
        val allocSize = forge.anInt(100, 10000)
        whenever(bitmap.config).thenReturn(mockConfig)
        whenever(bitmap.width).thenReturn(width)
        whenever(bitmap.height).thenReturn(height)
        whenever(bitmap.allocationByteCount).thenReturn(allocSize)
        whenever(bitmap.isMutable).thenReturn(true)
        whenever(bitmap.isRecycled).thenReturn(false)
        return bitmap
    }
}
