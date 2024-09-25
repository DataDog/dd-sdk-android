/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.os.Build
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.utils.CacheUtils
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BitmapPoolTest {
    private lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockConfig: Bitmap.Config

    @Mock
    lateinit var mockCacheUtils: CacheUtils<String, Bitmap>

    @Spy
    lateinit var spyBitmapPoolHelper: BitmapPoolHelper

    private lateinit var testedCache: BitmapPool
    private var width: Int = 0
    private var height: Int = 0
    private lateinit var fakeKey: String

    @BeforeEach
    fun setup(forge: Forge) {
        width = forge.anInt(1, 200)
        height = forge.anInt(1, 200)
        fakeKey = "$width-$height-$mockConfig"

        mockBitmap = createMockBitmap(forge)

        doAnswer {
            @Suppress("UNCHECKED_CAST")
            (it.arguments[0] as () -> Any).invoke()
        }.`when`(spyBitmapPoolHelper).safeCall<Any>(any())

        testedCache = BitmapPool(
            bitmapPoolHelper = spyBitmapPoolHelper,
            cacheUtils = mockCacheUtils
        )
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
        testedCache.put(mockBitmap)
        testedCache.getBitmapByProperties(mockBitmap.width, mockBitmap.height, mockBitmap.config)

        // When
        testedCache.put(mockBitmap)

        // Then
        val actualBitmap =
            testedCache.getBitmapByProperties(mockBitmap.width, mockBitmap.height, mockBitmap.config)
        assertThat(actualBitmap).isEqualTo(mockBitmap)
    }

    @Test
    fun `M add to pool W put() { and bitmap not in pool }`() {
        // When
        testedCache.put(mockBitmap)
        val actual = testedCache.getBitmapByProperties(width, height, mockConfig)

        // Then
        assertThat(actual).isEqualTo(mockBitmap)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `M add to pool W put() { and bitmap not in pool, api N }`() {
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
        assertThat(testedCache.usedBitmaps.size).isEqualTo(0)
    }

    @Test
    fun `M not allow recycled bitmaps W put()`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        testedCache.put(mockBitmap)

        // Then
        assertThat(testedCache.usedBitmaps.size).isEqualTo(0)
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
        assertThat(testedCache.bitmapsBySize.size).isEqualTo(1)
    }

    @Test
    fun `M insert multiple bitmaps W put() { multiple threads }`(forge: Forge) {
        // Given
        val countDownLatch = CountDownLatch(3)
        val bitmapPoolHelper = BitmapPoolHelper()
        val key = bitmapPoolHelper.generateKey(mockBitmap)

        // When
        repeat(3) {
            Thread {
                testedCache.put(createMockBitmap(forge))
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        assertThat(testedCache.bitmapsBySize[key]?.size).isEqualTo(3)
    }

    @Test
    fun `M return total size of the stored bitmaps W size()`(forge: Forge) {
        // Given
        testedCache.put(mockBitmap)

        val secondBitmap = createMockBitmap(forge)
        testedCache.put(secondBitmap)

        val expectedSize = mockBitmap.allocationByteCount + secondBitmap.allocationByteCount

        // When
        val actualSize = testedCache.size()

        // Then
        assertThat(actualSize).isEqualTo(expectedSize)
    }

    @Test
    fun `M call recycle on bitmaps W clear()`() {
        // Given
        var called = false
        whenever(mockBitmap.recycle()).then {
            called = true
            true
        }
        testedCache.put(mockBitmap)

        // When
        testedCache.clear()

        // Then
        assertThat(called).isTrue()
    }

    @Test
    fun `M remove bitmap from pool W clear()`() {
        // Given
        testedCache.put(mockBitmap)

        // When
        testedCache.clear()

        // Then
        assertThat(testedCache.size()).isEqualTo(0)
    }

    @Test
    fun `M remove bitmap from usedBitmaps W clear()`() {
        // Given
        testedCache.put(mockBitmap)
        testedCache.get(fakeKey)
        assertThat(testedCache.usedBitmaps.size).isEqualTo(1)

        // When
        testedCache.clear()

        // Then
        assertThat(testedCache.usedBitmaps.size).isEqualTo(0)
    }

    @Test
    fun `M remove bitmap from bitmapsBySize W clear()`() {
        // Given
        testedCache.put(mockBitmap)
        assertThat(testedCache.bitmapsBySize[fakeKey]?.size).isEqualTo(1)

        // When
        testedCache.clear()

        // Then
        assertThat(testedCache.bitmapsBySize[fakeKey]?.size).isEqualTo(0)
    }

    @Test
    fun `M clear all items W onLowMemory()`() {
        // Given
        testedCache.put(mockBitmap)

        // When
        testedCache.onLowMemory()

        // Then
        assertThat(testedCache.size()).isEqualTo(0)
    }

    @Test
    fun `M call cacheUtils with correct level W onTrimMemory()`() {
        // When
        testedCache.onTrimMemory(0)

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockCacheUtils).handleTrimMemory(captor.capture(), any())
        assertThat(captor.firstValue).isEqualTo(0)
    }

    @RepeatedTest(30)
    fun `M not receive ConcurrentModificationException W get() { and onLowMemory }`(
        forge: Forge
    ) {
        // when this issue occurs, the frequency is roughly once per 3000 runs,

        // Given
        val numberOfThreads = 3
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val tasks = mutableListOf<Future<*>>()

        val allThreadsStartedLatch = CountDownLatch(numberOfThreads)
        testedCache.put(mockBitmap)
        val bitmapKey = spyBitmapPoolHelper.generateKey(mockBitmap)

        // When
        repeat(numberOfThreads) {
            tasks += executor.submit {
                allThreadsStartedLatch.countDown()
                allThreadsStartedLatch.await()
                if (forge.aBool()) {
                    testedCache.get(bitmapKey)
                } else {
                    testedCache.onLowMemory()
                }
            }
        }

        // Then
        tasks.forEach { assertDoesNotThrow { it.get() } }
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
