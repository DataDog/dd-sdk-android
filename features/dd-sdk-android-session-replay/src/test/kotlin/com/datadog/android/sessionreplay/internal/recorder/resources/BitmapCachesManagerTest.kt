/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BitmapCachesManagerTest {
    private lateinit var testedCachesManager: BitmapCachesManager

    @Mock
    lateinit var mockBitmapPool: BitmapPool

    @Mock
    lateinit var mockResourcesCache: ResourcesLRUCache

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockBitmap: Bitmap

    @StringForgery
    lateinit var fakeResourceId: String

    @StringForgery
    lateinit var fakeResourceKey: String

    @BeforeEach
    fun `set up`() {
        whenever(mockResourcesCache.generateKeyFromDrawable(any())).thenReturn(fakeResourceId)

        testedCachesManager = createBitmapCachesManager(
            bitmapPool = mockBitmapPool,
            resourcesLRUCache = mockResourcesCache,
            logger = mockLogger
        )
    }

    @Test
    fun `M register callbacks only once W registerCallbacks`() {
        // When
        repeat(times = 5) {
            testedCachesManager.registerCallbacks(mockApplicationContext)
        }

        // Then
        verify(mockApplicationContext).registerComponentCallbacks(mockResourcesCache)
        verify(mockApplicationContext).registerComponentCallbacks(mockBitmapPool)
    }

    @Test
    fun `M log error W registerCallbacks() { cache does not subclass ComponentCallbacks2 }`() {
        // Given
        val fakeBase64CacheInstance = FakeNonComponentsCallbackCache()
        testedCachesManager = createBitmapCachesManager(
            bitmapPool = mockBitmapPool,
            resourcesLRUCache = fakeBase64CacheInstance,
            logger = mockLogger
        )

        // When
        testedCachesManager.registerCallbacks(mockApplicationContext)

        // Then
        mockLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            message = Cache.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
        )
    }

    @Test
    fun `M put in resource cache W putInResourceCache`() {
        // When
        testedCachesManager.putInResourceCache(fakeResourceKey, fakeResourceId)

        // Then
        verify(mockResourcesCache).put(fakeResourceKey, fakeResourceId.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `M get resource from resource cache W getFromResourceCache { resource exists in cache }`() {
        // Given
        val fakeCacheData = fakeResourceId.toByteArray(Charsets.UTF_8)
        whenever(mockResourcesCache.get(fakeResourceKey)).thenReturn(fakeCacheData)

        // When
        val result = testedCachesManager.getFromResourceCache(fakeResourceKey)

        // Then
        assertThat(result).isEqualTo(fakeResourceId)
    }

    @Test
    fun `M get null from resource cache W getFromResourceCache { resource not in cache }`() {
        // When
        val result = testedCachesManager.getFromResourceCache(fakeResourceKey)

        // Then
        verify(mockResourcesCache).get(fakeResourceKey)
        assertThat(result).isNull()
    }

    @Test
    fun `M put in bitmap pool W putInBitmapPool`() {
        // When
        testedCachesManager.putInBitmapPool(mockBitmap)

        // Then
        verify(mockBitmapPool).put(mockBitmap)
    }

    @Test
    fun `M get bitmap by properties W getBitmapByProperties`(
        @IntForgery fakeWidth: Int,
        @IntForgery fakeHeight: Int,
        @Mock mockConfig: Bitmap.Config
    ) {
        // Given
        whenever(
            mockBitmapPool.getBitmapByProperties(
                fakeWidth,
                fakeHeight,
                mockConfig
            )
        ).thenReturn(mockBitmap)

        // When
        val result = testedCachesManager.getBitmapByProperties(fakeWidth, fakeHeight, mockConfig)

        // Then
        assertThat(result).isEqualTo(mockBitmap)
    }

    @Test
    fun `return null W getBitmapByProperties { no bitmap found }`(
        @IntForgery fakeWidth: Int,
        @IntForgery fakeHeight: Int,
        @Mock mockConfig: Bitmap.Config
    ) {
        // Given
        whenever(
            mockBitmapPool.getBitmapByProperties(
                fakeWidth,
                fakeHeight,
                mockConfig
            )
        ).thenReturn(null)

        // When
        val result = testedCachesManager.getBitmapByProperties(fakeWidth, fakeHeight, mockConfig)

        // Then
        assertThat(result).isNull()
    }

    private fun createBitmapCachesManager(
        bitmapPool: BitmapPool,
        resourcesLRUCache: Cache<Drawable, ByteArray>,
        logger: InternalLogger
    ): BitmapCachesManager =
        BitmapCachesManager(
            bitmapPool = bitmapPool,
            resourcesLRUCache = resourcesLRUCache,
            logger = logger
        )

    // this is in order to test having a class that implements
    // Cache, but does NOT implement ComponentCallbacks2
    private class FakeNonComponentsCallbackCache : Cache<Drawable, ByteArray> {

        override fun size(): Int = 0

        override fun clear() {}
    }
}
