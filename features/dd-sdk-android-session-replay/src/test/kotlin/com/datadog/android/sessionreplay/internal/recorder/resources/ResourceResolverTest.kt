/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceResolverTest {
    private lateinit var testedResourceResolver: ResourceResolver

    @Mock
    lateinit var mockDrawableUtils: DrawableUtils

    @Mock
    lateinit var mockWebPImageCompression: ImageCompression

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockMD5HashGenerator: MD5HashGenerator

    @Mock
    lateinit var mockSerializerCallback: ResourceResolverCallback

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockResourcesLRUCache: ResourcesLRUCache

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockDrawableCopier: DrawableCopier

    @Mock
    lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockStateListDrawable: StateListDrawable

    @Mock
    lateinit var mockBitmapCachesManager: BitmapCachesManager

    @Mock
    lateinit var mockBitmapDrawable: BitmapDrawable

    @Mock
    lateinit var mockPathUtils: PathUtils

    @Mock
    lateinit var mockResources: Resources

    private var fakeBitmapWidth: Int = 1

    private var fakeBitmapHeight: Int = 1

    @StringForgery
    lateinit var fakeResourceKey: String

    @StringForgery
    lateinit var fakeResourceId: String

    private lateinit var fakeImageCompressionByteArray: ByteArray

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(
            mockBitmapDrawable
        )
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockDrawable)).thenReturn(fakeResourceKey)
        whenever(mockDrawableCopier.copy(eq(mockDrawable), any())).thenReturn(mockDrawable)
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeBitmapWidth = forge.anInt(min = 1)
        fakeBitmapHeight = forge.anInt(min = 1)

        whenever(mockMD5HashGenerator.generate(any())).thenReturn(fakeResourceId)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull(),
                bitmapCreationCallback = any()
            )
        ).then {
            (it.getArgument<ResourceResolver.BitmapCreationCallback>(6)).onReady(mockBitmap)
        }

        // executeSafe is an extension so we have to mock the internal execute function
        whenever(
            mockExecutorService.execute(
                any()
            )
        ).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)

        testedResourceResolver = createResourceResolver()
    }

    @Test
    fun `M get data from cache W resolveResourceIdFromDrawable() { cache hit with resourceId }`() {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeResourceKey)).thenReturn(fakeResourceId)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableUtils)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M retry image creation once W resolveResourceIdFromDrawable() { image recycled while working on it }`() {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockBitmap)
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)

        whenever(mockBitmap.isRecycled)
            .thenReturn(false)
            .thenReturn(true)

        val emptyByteArray = ByteArray(0)

        whenever(mockBitmapCachesManager.getFromResourceCache(fakeResourceKey))
            .thenReturn(null)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M send onReady W resolveResourceIdFromDrawable(Drawable) { failed to get image data }`() {
        // Given
        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M call onFailure W copy bitmap return null`() {
        // Given
        whenever(
            mockDrawableCopier.copy(
                originalDrawable = mockDrawable,
                resources = mockResources
            )
        ).doReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M send onReady W resolveResourceIdFromDrawable(Bitmap) { failed to get image data }`() {
        // Given
        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)

        // When
        testedResourceResolver.resolveResourceIdFromBitmap(
            bitmap = mockBitmap,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M calculate resourceId W resolveResourceIdFromDrawable() { cache miss }`() {
        // Given
        whenever(mockResourcesLRUCache.get(fakeResourceKey)).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M return failure W resolveResourceIdFromDrawable { createBitmapOfApproxSizeFromDrawable failed }`() {
        // Given
        whenever(mockResourcesLRUCache.get(fakeResourceKey)).thenReturn(null)
        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull(),
                bitmapCreationCallback = any()
            )
        ).then {
            (it.getArgument<ResourceResolver.BitmapCreationCallback>(6)).onFailure()
        }

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M use the same ThreadPoolExecutor W build()`() {
        // When
        val instance1 = ResourceResolver(
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            webPImageCompression = mockWebPImageCompression,
            drawableUtils = mockDrawableUtils,
            logger = mockLogger,
            pathUtils = mockPathUtils,
            md5HashGenerator = mockMD5HashGenerator,
            bitmapCachesManager = mockBitmapCachesManager
        )
        val instance2 = ResourceResolver(
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            webPImageCompression = mockWebPImageCompression,
            drawableUtils = mockDrawableUtils,
            logger = mockLogger,
            pathUtils = mockPathUtils,
            md5HashGenerator = mockMD5HashGenerator,
            bitmapCachesManager = mockBitmapCachesManager
        )

        // Then
        assertThat(instance1.threadPoolExecutor).isEqualTo(
            instance2.threadPoolExecutor
        )
    }

    @Test
    fun `M not try to cache resourceId W resolveResourceIdFromDrawable() { and did not get resourceId }`() {
        // Given
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockStateListDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockResourcesLRUCache, times(0)).put(any(), any())
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W resolveResourceIdFromDrawable() { no bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W resolveResourceIdFromDrawable() { bitmap was recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M use scaled bitmap from bitmapDrawable W resolveResourceIdFromDrawable() { has bitmap }`() {
        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createScaledBitmap(
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `M draw bitmap W resolveResourceIdFromDrawable() { bitmapDrawable where bitmap has no width }`() {
        // Given
        whenever(mockBitmap.width).thenReturn(0)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, never()).createScaledBitmap(
            any(),
            anyOrNull()
        )
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M draw bitmap W resolveResourceIdFromDrawable() { bitmapDrawable where bitmap has no height }`() {
        // Given
        whenever(mockBitmap.height).thenReturn(0)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, never()).createScaledBitmap(
            any(),
            anyOrNull()
        )
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M not cache bitmap W resolveResourceIdFromDrawable() { BitmapDrawable with bitmap not resized }`() {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockBitmap)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, never()).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceIdFromDrawable() { BitmapDrawable width was resized }`(
        @Mock mockResizedBitmap: Bitmap,
        @StringForgery fakeString: String
    ) {
        // Given
        val fakeByteArray = fakeString.toByteArray()
        assertThat(fakeByteArray).isNotEmpty()

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockResizedBitmap.width).thenReturn(fakeBitmapWidth - 1)
        whenever(mockResizedBitmap.height).thenReturn(fakeBitmapHeight)

        whenever(mockWebPImageCompression.compressBitmap(mockResizedBitmap)).thenReturn(fakeByteArray)
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull())).thenReturn(mockResizedBitmap)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceIdFromDrawable() { BitmapDrawable height was resized }`(
        @Mock mockResizedBitmap: Bitmap,
        @StringForgery fakeString: String
    ) {
        // Given
        val fakeByteArray = fakeString.toByteArray()
        assertThat(fakeByteArray).isNotEmpty()

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockResizedBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockResizedBitmap.height).thenReturn(fakeBitmapHeight - 1)

        whenever(mockWebPImageCompression.compressBitmap(mockResizedBitmap)).thenReturn(fakeByteArray)
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull())).thenReturn(mockResizedBitmap)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceIdFromDrawable() { from BitmapDrawable with null bitmap }`() {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeResourceKey))
            .thenReturn(null)
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, times(1)).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceIdFromDrawable() { not a BitmapDrawable }`() {
        // Given
        val mockLayerDrawable = mock<LayerDrawable>()
        whenever(mockDrawableCopier.copy(any(), any())).thenReturn(mockLayerDrawable)
        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockLayerDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, times(1)).putInBitmapPool(any())
    }

    @Test
    fun `M return all callbacks W resolveResourceIdFromDrawable(Drawable) { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: ResourceResolverCallback,
        @Mock mockSecondCallback: ResourceResolverCallback,
        @Mock mockFirstDrawable: Drawable,
        @Mock mockSecondDrawable: Drawable,
        @StringForgery fakeFirstResourceId: String,
        @StringForgery fakeSecondResourceId: String,
        @StringForgery fakeFirstKey: String,
        @StringForgery fakeSecondKey: String
    ) {
        // Given
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockFirstDrawable)).thenReturn(fakeFirstKey)
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockSecondDrawable)).thenReturn(fakeSecondKey)
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeFirstKey))
            .thenReturn(fakeFirstResourceId)
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeSecondKey))
            .thenReturn(fakeSecondResourceId)

        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            testedResourceResolver.resolveResourceIdFromDrawable(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                originalDrawable = mockFirstDrawable,
                drawableCopier = mockDrawableCopier,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                customResourceIdCacheKey = null,
                resourceResolverCallback = mockFirstCallback
            )
            Thread.sleep(1500)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            testedResourceResolver.resolveResourceIdFromDrawable(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                originalDrawable = mockSecondDrawable,
                drawableCopier = mockDrawableCopier,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                customResourceIdCacheKey = null,
                resourceResolverCallback = mockSecondCallback
            )
            Thread.sleep(500)
            countDownLatch.countDown()
        }

        // When
        thread1.start()
        thread2.start()

        // Then
        countDownLatch.await()
        verify(mockFirstCallback).onSuccess(fakeFirstResourceId)
        verify(mockSecondCallback).onSuccess(fakeSecondResourceId)
    }

    @Test
    fun `M return all callbacks W resolveResourceIdFromDrawable(Bitmap) { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: ResourceResolverCallback,
        @Mock mockSecondCallback: ResourceResolverCallback,
        @Mock mockFirstBitmap: Bitmap,
        @Mock mockSecondBitmap: Bitmap,
        @StringForgery fakeFirstResourceId: String,
        @StringForgery fakeSecondResourceId: String,
        forge: Forge
    ) {
        // Given
        val firstBitmapCompression = forge.aString().toByteArray()
        val secondBitmapCompression = forge.aString().toByteArray()
        whenever(mockWebPImageCompression.compressBitmap(mockFirstBitmap)).thenReturn(firstBitmapCompression)
        whenever(mockWebPImageCompression.compressBitmap(mockSecondBitmap)).thenReturn(secondBitmapCompression)
        whenever(mockMD5HashGenerator.generate(firstBitmapCompression)).thenReturn(fakeFirstResourceId)
        whenever(mockMD5HashGenerator.generate(secondBitmapCompression)).thenReturn(fakeSecondResourceId)
        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            testedResourceResolver.resolveResourceIdFromBitmap(
                bitmap = mockFirstBitmap,
                resourceResolverCallback = mockFirstCallback
            )
            Thread.sleep(1500)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            testedResourceResolver.resolveResourceIdFromBitmap(
                bitmap = mockSecondBitmap,
                resourceResolverCallback = mockSecondCallback
            )
            Thread.sleep(500)
            countDownLatch.countDown()
        }

        // When
        thread1.start()
        thread2.start()

        // Then
        countDownLatch.await()
        verify(mockFirstCallback).onSuccess(fakeFirstResourceId)
        verify(mockSecondCallback).onSuccess(fakeSecondResourceId)
    }

    @Test
    fun `M failover to bitmap creation W resolveResourceIdFromDrawable() { bitmapDrawable returned empty bytearray }`(
        @Mock mockCreatedBitmap: Bitmap
    ) {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)

        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap))
            .thenReturn(emptyByteArray)

        whenever(mockWebPImageCompression.compressBitmap(mockCreatedBitmap))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap))
            .thenReturn(mockBitmap)
            .thenReturn(mockCreatedBitmap)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M only send resource once W resolveResourceIdFromDrawable() { call twice on the same image }`(
        @Mock mockCreatedBitmap: Bitmap,
        @StringForgery fakeResourceId: String,
        @StringForgery fakeResource: String
    ) {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(fakeResourceId)

        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val fakeByteArray = fakeResource.toByteArray()
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap))
            .thenReturn(fakeByteArray)

        whenever(mockWebPImageCompression.compressBitmap(mockCreatedBitmap))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap))
            .thenReturn(mockBitmap)
            .thenReturn(mockCreatedBitmap)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then

        // second time
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
            identifier = eq(fakeResourceId),
            resourceData = eq(fakeByteArray)
        )

        // second time
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
            identifier = eq(fakeResourceId),
            resourceData = eq(fakeByteArray)
        )
    }

    @Test
    fun `M return cache miss W resolveResourceId() { failed to generate resource key }`() {
        // Given
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockDrawable)).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
    }

    @Test
    fun `M use cache key W resolveResourceId() { cache hit, key provided } `(
        @StringForgery fakeCacheKey: String
    ) {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeCacheKey)).thenReturn(fakeResourceId)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = fakeCacheKey,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M use cache key W resolveResourceId() { cache miss, key provided } `(
        @StringForgery fakeCacheKey: String
    ) {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeCacheKey)).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = fakeCacheKey,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager).putInResourceCache(fakeCacheKey, fakeResourceId)
    }

    private fun createResourceResolver(): ResourceResolver = ResourceResolver(
        logger = mockLogger,
        pathUtils = mockPathUtils,
        threadPoolExecutor = mockExecutorService,
        drawableUtils = mockDrawableUtils,
        webPImageCompression = mockWebPImageCompression,
        md5HashGenerator = mockMD5HashGenerator,
        recordedDataQueueHandler = mockRecordedDataQueueHandler,
        bitmapCachesManager = mockBitmapCachesManager
    )

    @Test
    fun `M use original drawable for cache write W resolveResourceId() { cache miss }`(
        @Mock mockCopiedDrawable: Drawable
    ) {
        // Given
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenReturn(mockCopiedDrawable)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, times(2)).generateResourceKeyFromDrawable(mockDrawable)
    }

    @Test
    fun `M use copy of the drawable for creating the bitmap W resolveResourceId() { cache miss }`(
        @Mock mockCopiedDrawable: Drawable
    ) {
        // Given
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenReturn(mockCopiedDrawable)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = eq(mockCopiedDrawable),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = any(),
            config = any(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M only copy the drawable in work thread W resolveResourceIdFromDrawable`() {
        // Given
        whenever(
            mockExecutorService.execute(
                any()
            )
        ).then {
            // do nothing to simulate that work thread doesn't execute the task.
            mock<Future<Boolean>>()
        }

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableCopier)

        // Given
        whenever(
            mockExecutorService.execute(
                any()
            )
        ).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
    }
}
