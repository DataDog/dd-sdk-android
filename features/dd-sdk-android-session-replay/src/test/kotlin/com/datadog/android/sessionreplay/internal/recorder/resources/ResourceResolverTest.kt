/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.ref.WeakReference
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

    @Mock lateinit var stubTimeProvider: TimeProvider

    private var fakeNowNs = 0L

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

    @Mock
    lateinit var mockAlpha8BitmapConverter: BitmapConverter

    @Mock
    lateinit var mockAlpha8ResourceCache: Alpha8ResourceCache

    @Mock
    lateinit var mockBitmapSnapshot: Bitmap

    private var fakeBitmapWidth: Int = 1

    private var fakeBitmapHeight: Int = 1

    @StringForgery
    lateinit var fakeResourceKey: String

    @StringForgery
    lateinit var fakeResourceId: String

    private lateinit var fakeImageCompressionByteArray: ByteArray

    @BeforeEach
    fun setup(forge: Forge) {
        fakeNowNs = forge.aLong(min = 0, max = Long.MAX_VALUE - PendingResourceRequests.ALLOCATION_BACKOFF_NS)
        whenever(stubTimeProvider.getDeviceElapsedTimeNanos()).thenAnswer { fakeNowNs }
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(
            mockBitmapDrawable
        )
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockDrawable)).thenReturn(fakeResourceKey)
        whenever(mockDrawableCopier.copy(eq(mockDrawable), any())).thenReturn(mockDrawable)
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeBitmapWidth = forge.anInt(min = 1, max = 1024)
        fakeBitmapHeight = forge.anInt(min = 1, max = 1024)
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeBitmapWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeBitmapHeight)

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

        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockBitmapSnapshot)

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
            applicationContext = mockApplicationContext,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            webPImageCompression = mockWebPImageCompression,
            drawableUtils = mockDrawableUtils,
            drawableExecutor = mockExecutorService,
            logger = mockLogger,
            pathUtils = mockPathUtils,
            md5HashGenerator = mockMD5HashGenerator,
            bitmapCachesManager = mockBitmapCachesManager
        )
        val instance2 = ResourceResolver(
            applicationContext = mockApplicationContext,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            webPImageCompression = mockWebPImageCompression,
            drawableUtils = mockDrawableUtils,
            drawableExecutor = mockExecutorService,
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
    fun `M recycle owned pixels W resolveResourceIdFromDrawable { scaled BitmapDrawable }`(
        @Mock mockResizedBitmap: Bitmap
    ) {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap)).thenReturn(mockResizedBitmap)

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
        verify(mockResizedBitmap).recycle()
        verify(mockBitmap, never()).recycle()
        verify(mockBitmapCachesManager, never()).putInBitmapPool(any())
    }

    @Test
    fun `M share byte capacity W resolveResourceIdFromDrawable { bitmap work and wide color images }`(
        @Mock mockBitmapCallback: ResourceResolverCallback
    ) {
        // Given
        val fakeTasks = mutableListOf<Runnable>()
        whenever(mockExecutorService.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }
        whenever(mockBitmap.width).thenReturn(3072)
        whenever(mockBitmap.height).thenReturn(4096)
        val stubWideBitmap = mock<Bitmap> {
            on { width } doReturn 2048
            on { height } doReturn 2048
            on { allocationByteCount } doReturn 2048 * 2048 * 8
        }
        whenever(mockBitmapDrawable.bitmap).thenReturn(stubWideBitmap)
        testedResourceResolver.resolveResourceIdFromBitmap(mockBitmap, mockBitmapCallback)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = 512,
            drawableHeight = 512,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableCopier)
        verify(mockSerializerCallback).onFailure()
        assertThat(fakeTasks).hasSize(1)

        // When
        fakeTasks.removeAt(0).run()
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = 512,
            drawableHeight = 512,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )
        fakeTasks.single().run()

        // Then
        verify(mockDrawableCopier).copy(mockBitmapDrawable, mockResources)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
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
    fun `M return both resources W resolveResourceIdFromBitmap { distinct sources }`(
        @StringForgery fakeFirstResourceId: String,
        @StringForgery fakeSecondResourceId: String,
        forge: Forge
    ) {
        // Given
        val mockFirstCallback = mock<ResourceResolverCallback>()
        val mockSecondCallback = mock<ResourceResolverCallback>()
        val mockFirstCopy = mock<Bitmap>()
        val mockSecondCopy = mock<Bitmap>()
        val stubFirstBitmap = mock<Bitmap> {
            on { width } doReturn fakeBitmapWidth
            on { height } doReturn fakeBitmapHeight
            on { copy(Bitmap.Config.ARGB_8888, false) } doReturn mockFirstCopy
        }
        val stubSecondBitmap = mock<Bitmap> {
            on { width } doReturn fakeBitmapWidth
            on { height } doReturn fakeBitmapHeight
            on { copy(Bitmap.Config.ARGB_8888, false) } doReturn mockSecondCopy
        }
        val fakeFirstCompression = forge.aString().toByteArray()
        val fakeSecondCompression = forge.aString().toByteArray()
        whenever(mockWebPImageCompression.compressBitmap(mockFirstCopy)) doReturn fakeFirstCompression
        whenever(mockWebPImageCompression.compressBitmap(mockSecondCopy)) doReturn fakeSecondCompression
        whenever(mockMD5HashGenerator.generate(fakeFirstCompression)) doReturn fakeFirstResourceId
        whenever(mockMD5HashGenerator.generate(fakeSecondCompression)) doReturn fakeSecondResourceId

        // When
        testedResourceResolver.resolveResourceIdFromBitmap(stubFirstBitmap, mockFirstCallback)
        testedResourceResolver.resolveResourceIdFromBitmap(stubSecondBitmap, mockSecondCallback)

        // Then
        verify(mockFirstCallback).onSuccess(fakeFirstResourceId)
        verify(mockSecondCallback).onSuccess(fakeSecondResourceId)
        verify(mockFirstCopy).recycle()
        verify(mockSecondCopy).recycle()
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
            resourceData = eq(fakeByteArray),
            mimeType = anyOrNull()
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
            resourceData = eq(fakeByteArray),
            mimeType = anyOrNull()
        )
    }

    @Test
    fun `M proceed to copy drawable W resolveResourceId() { cache miss }`() {
        // Given
        // getFromResourceCache returns null by default (cache miss)

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

    @Test
    fun `M limit retained sources W resolveResourceIdFromDrawable { several large sources queued }`() {
        // Given
        val fakeTasks = mutableListOf<Runnable>()
        val fakeSourceBytes = 48 * 1024 * 1024
        whenever(mockExecutorService.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }

        // When
        repeat(6) { index ->
            val stubSource = mock<Bitmap> {
                on { width } doReturn 4096
                on { height } doReturn 3072
                on { allocationByteCount } doReturn fakeSourceBytes
            }
            val stubDrawable = mock<BitmapDrawable> { on { bitmap } doReturn stubSource }
            testedResourceResolver.resolveResourceIdFromDrawable(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                originalDrawable = stubDrawable,
                drawableCopier = mockDrawableCopier,
                drawableWidth = 512,
                drawableHeight = 512,
                customResourceIdCacheKey = fakeResourceKey + index,
                resourceResolverCallback = mockSerializerCallback
            )
        }

        // Then
        assertThat(
            fakeTasks.size.toLong() * fakeSourceBytes
        ).isLessThanOrEqualTo(PendingResourceRequests.MAX_PENDING_BYTES)
    }

    @Test
    fun `M recover image processing W resolveResourceIdFromDrawable { bounds failures and snapshot cancellation }`() {
        // Given
        val stubBitmapWrapper = mock<BitmapWrapper>()
        val stubCanvasWrapper = mock<CanvasWrapper>()
        whenever(stubBitmapWrapper.createBitmap(any(), any(), any(), anyOrNull())).thenReturn(mockBitmap)
        val stubCanvas = mock<Canvas>()
        whenever(stubCanvasWrapper.createCanvas(mockBitmap)).thenReturn(stubCanvas)
        whenever(mockDrawable.setBounds(any(), any(), any(), any())).thenThrow(IllegalStateException(fakeResourceId))
        val testedDrawing = DrawableUtils(
            internalLogger = mockLogger,
            bitmapCachesManager = mockBitmapCachesManager,
            bitmapWrapper = stubBitmapWrapper,
            canvasWrapper = stubCanvasWrapper
        )
        val testedResolverWithDrawing = ResourceResolver(
            applicationContext = mockApplicationContext,
            bitmapCachesManager = mockBitmapCachesManager,
            pathUtils = mockPathUtils,
            threadPoolExecutor = mockExecutorService,
            drawableUtils = testedDrawing,
            drawableExecutor = mockExecutorService,
            webPImageCompression = mockWebPImageCompression,
            logger = mockLogger,
            md5HashGenerator = mockMD5HashGenerator,
            recordedDataQueueHandler = mockRecordedDataQueueHandler
        )
        repeat(PendingResourceRequests.MAX_PENDING_REQUESTS) { index ->
            val fakeCancellation = ResourceCancellation()
            val stubCallback = mock<ResourceResolverCallback> { on { cancellation } doReturn fakeCancellation }
            assertDoesNotThrow {
                testedResolverWithDrawing.resolveResourceIdFromDrawable(
                    resources = mockResources,
                    applicationContext = mockApplicationContext,
                    displayMetrics = mockDisplayMetrics,
                    originalDrawable = mockDrawable,
                    drawableCopier = mockDrawableCopier,
                    drawableWidth = 512,
                    drawableHeight = 512,
                    customResourceIdCacheKey = fakeResourceKey + index,
                    resourceResolverCallback = stubCallback
                )
            }
            fakeCancellation.cancel()
            verify(stubCallback).onFailure()
        }
        val mockHealthyDrawable = mock<Drawable>()
        whenever(mockDrawableCopier.copy(mockHealthyDrawable, mockResources)).thenReturn(mockHealthyDrawable)

        // When
        testedResolverWithDrawing.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockHealthyDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = 512,
            drawableHeight = 512,
            customResourceIdCacheKey = fakeResourceKey + "healthy",
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockHealthyDrawable).draw(stubCanvas)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M release wrapped inputs W resolveResourceIdFromDrawable { source disappears before drawing }`() {
        // Given
        val fakeTasks = mutableListOf<Runnable>()
        val fakeReferences = mutableListOf<WeakReference<Drawable>>()
        whenever(mockExecutorService.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }
        testedResourceResolver = createResourceResolver { drawable ->
            WeakReference(drawable).also { fakeReferences.add(it) }
        }

        // When
        repeat(PendingResourceRequests.MAX_PENDING_REQUESTS) { index ->
            val stubLayer = mock<LayerDrawable>()
            testedResourceResolver.resolveResourceIdFromDrawable(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                originalDrawable = stubLayer,
                drawableCopier = mockDrawableCopier,
                drawableWidth = 512,
                drawableHeight = 512,
                customResourceIdCacheKey = fakeResourceKey + index,
                resourceResolverCallback = mockSerializerCallback
            )
        }
        fakeReferences.forEach { it.clear() }
        fakeTasks.forEach { it.run() }

        // Then
        assertThat(fakeReferences).hasSize(PendingResourceRequests.MAX_PENDING_REQUESTS)
        verifyNoInteractions(mockDrawableCopier, mockDrawableUtils)
        verify(mockSerializerCallback, times(PendingResourceRequests.MAX_PENDING_REQUESTS)).onFailure()
    }

    @ParameterizedTest
    @EnumSource(AllocationFailureStage::class)
    fun `M stop fallback and pause both pipelines W resolveResourceIdFromDrawable { allocation fails }`(
        stage: AllocationFailureStage,
        @Mock mockScaledBitmap: Bitmap,
        @Mock mockBitmapCallback: ResourceResolverCallback
    ) {
        // Given
        val fakeFailure = OutOfMemoryError()
        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap)).thenReturn(mockScaledBitmap)
        val scaledBitmap = if (stage == AllocationFailureStage.ENCODE_BORROWED_BITMAP) mockBitmap else mockScaledBitmap
        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap)).thenReturn(scaledBitmap)
        when (stage) {
            AllocationFailureStage.COPY -> whenever(mockDrawableCopier.copy(mockBitmapDrawable, mockResources))
                .thenThrow(fakeFailure)
            AllocationFailureStage.SCALE -> whenever(mockDrawableUtils.createScaledBitmap(mockBitmap))
                .thenThrow(fakeFailure)
            AllocationFailureStage.ENCODE, AllocationFailureStage.ENCODE_BORROWED_BITMAP ->
                whenever(mockWebPImageCompression.compressBitmap(scaledBitmap))
                    .thenThrow(fakeFailure)
        }
        doAnswer {
            verify(mockSerializerCallback).onFailure()
            if (stage == AllocationFailureStage.ENCODE) verify(mockScaledBitmap).recycle()
            null
        }.whenever(mockLogger).log(any(), any<InternalLogger.Target>(), any(), any(), any(), anyOrNull())

        // When
        captureDrawable()
        testedResourceResolver.resolveResourceIdFromBitmap(mockBitmap, mockBitmapCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmapCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockBitmap, never()).recycle()
        verify(mockDrawableUtils, never()).createBitmapOfApproxSizeFromDrawable(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        // When
        doReturn(mockBitmapDrawable).whenever(mockDrawableCopier).copy(mockBitmapDrawable, mockResources)
        doReturn(scaledBitmap).whenever(mockDrawableUtils).createScaledBitmap(mockBitmap)
        doReturn(fakeImageCompressionByteArray).whenever(mockWebPImageCompression).compressBitmap(scaledBitmap)
        fakeNowNs += PendingResourceRequests.ALLOCATION_BACKOFF_NS
        captureDrawable()

        // Then
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M skip queued drawable allocations W resolveResourceIdFromDrawable { another image fails first }`() {
        // Given
        val fakeTasks = mutableListOf<Runnable>()
        whenever(mockExecutorService.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }
        whenever(mockWebPImageCompression.compressBitmap(mockBitmapSnapshot)).thenThrow(OutOfMemoryError())
        testedResourceResolver.resolveResourceIdFromBitmap(mockBitmap, mockSerializerCallback)
        captureDrawable()

        // When
        fakeTasks.forEach { it.run() }

        // Then
        verify(mockBitmapSnapshot).recycle()
        verifyNoInteractions(mockDrawableCopier)
        verify(mockSerializerCallback, times(2)).onFailure()
    }

    @Test
    fun `M release raster pixels before diagnostics W resolveResourceIdFromDrawable { encoding runs out of memory }`() {
        // Given
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap)).thenThrow(OutOfMemoryError())
        doAnswer {
            verify(mockBitmap).recycle()
            verify(mockSerializerCallback).onFailure()
            null
        }.whenever(mockLogger).log(any(), any<InternalLogger.Target>(), any(), any(), any(), anyOrNull())

        // When
        captureDrawable(mockDrawable)

        // Then
        verify(mockBitmapCachesManager, never()).putInBitmapPool(mockBitmap)
        verify(mockSerializerCallback).onFailure()
    }

    private fun captureDrawable(drawable: Drawable = mockBitmapDrawable) =
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = drawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

    internal enum class AllocationFailureStage { COPY, SCALE, ENCODE, ENCODE_BORROWED_BITMAP }

    private fun createResourceResolver(
        resourceExecutor: ExecutorService = mockExecutorService,
        drawableReferenceFactory: (Drawable) -> WeakReference<Drawable> = { WeakReference(it) }
    ): ResourceResolver = ResourceResolver(
        applicationContext = mockApplicationContext,
        logger = mockLogger,
        pathUtils = mockPathUtils,
        threadPoolExecutor = resourceExecutor,
        drawableUtils = mockDrawableUtils,
        drawableExecutor = mockExecutorService,
        drawableReferenceFactory = drawableReferenceFactory,
        timeProvider = stubTimeProvider,
        webPImageCompression = mockWebPImageCompression,
        md5HashGenerator = mockMD5HashGenerator,
        recordedDataQueueHandler = mockRecordedDataQueueHandler,
        bitmapCachesManager = mockBitmapCachesManager,
        alpha8BitmapConverter = mockAlpha8BitmapConverter,
        alpha8ResourceCache = mockAlpha8ResourceCache
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
        verify(mockBitmapCachesManager).putInResourceCache(fakeResourceKey, fakeResourceId)
        verify(mockBitmapCachesManager, never()).generateResourceKeyFromDrawable(mockCopiedDrawable)
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
        val mockResourceExecutor = mock<ExecutorService>()
        testedResourceResolver = createResourceResolver(resourceExecutor = mockResourceExecutor)
        val fakeTasks = mutableListOf<Runnable>()
        whenever(mockExecutorService.execute(any())).thenAnswer { fakeTasks.add(it.getArgument(0)); null }

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableCopier, mockDrawableUtils, mockResourceExecutor)

        // When
        fakeTasks.single().run()

        // Then
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
    }

    // region Alpha8 bitmap tests

    @Test
    fun `M return cached resourceId W resolveResourceIdFromBitmap { alpha8 bitmap, cache hit }`(
        @StringForgery fakeResourceId: String,
        @Mock mockAlpha8Bitmap: Bitmap,
        @Mock mockAlpha8Snapshot: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockAlpha8Bitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8Bitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Snapshot)
        whenever(mockAlpha8Snapshot.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8ResourceCache.generateKey(mockAlpha8Snapshot)).thenReturn(mockCacheKey)
        whenever(mockAlpha8ResourceCache.get(mockCacheKey)).thenReturn(fakeResourceId)

        // When
        testedResourceResolver.resolveResourceIdFromBitmap(
            bitmap = mockAlpha8Bitmap,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verifyNoInteractions(mockAlpha8BitmapConverter)
        verifyNoInteractions(mockWebPImageCompression)
    }

    @Test
    fun `M convert and process W resolveResourceIdFromBitmap { alpha8 bitmap, cache miss }`(
        @StringForgery fakeResourceId: String,
        @Mock mockAlpha8Bitmap: Bitmap,
        @Mock mockAlpha8Snapshot: Bitmap,
        @Mock mockConvertedBitmap: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockAlpha8Bitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8Bitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Snapshot)
        whenever(mockAlpha8Snapshot.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8ResourceCache.generateKey(mockAlpha8Snapshot)).thenReturn(mockCacheKey)
        whenever(mockAlpha8ResourceCache.get(mockCacheKey)).thenReturn(null)
        whenever(mockAlpha8BitmapConverter.convertAlpha8BitmapToArgb8888(mockAlpha8Snapshot))
            .thenReturn(mockConvertedBitmap)
        whenever(mockWebPImageCompression.compressBitmap(mockConvertedBitmap))
            .thenReturn(fakeImageCompressionByteArray)
        whenever(mockMD5HashGenerator.generate(fakeImageCompressionByteArray))
            .thenReturn(fakeResourceId)

        // When
        testedResourceResolver.resolveResourceIdFromBitmap(
            bitmap = mockAlpha8Bitmap,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockAlpha8BitmapConverter).convertAlpha8BitmapToArgb8888(mockAlpha8Snapshot)
        verify(mockWebPImageCompression).compressBitmap(mockConvertedBitmap)
        verify(mockConvertedBitmap).recycle()
        verify(mockAlpha8ResourceCache).put(mockCacheKey, fakeResourceId)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M fail W resolveResourceIdFromBitmap { alpha8 conversion fails }`(
        @Mock mockAlpha8Bitmap: Bitmap,
        @Mock mockAlpha8Snapshot: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockAlpha8Bitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8Bitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Snapshot)
        whenever(mockAlpha8Snapshot.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8ResourceCache.generateKey(mockAlpha8Snapshot)).thenReturn(mockCacheKey)
        whenever(mockAlpha8ResourceCache.get(mockCacheKey)).thenReturn(null)
        whenever(mockAlpha8BitmapConverter.convertAlpha8BitmapToArgb8888(mockAlpha8Snapshot))
            .thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromBitmap(
            bitmap = mockAlpha8Bitmap,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockWebPImageCompression)
    }

    @Test
    fun `M not use alpha8 cache W resolveResourceIdFromBitmap { non-alpha8 bitmap }`(
        @StringForgery fakeResourceId: String
    ) {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap))
            .thenReturn(fakeImageCompressionByteArray)
        whenever(mockMD5HashGenerator.generate(fakeImageCompressionByteArray))
            .thenReturn(fakeResourceId)

        // When
        testedResourceResolver.resolveResourceIdFromBitmap(
            bitmap = mockBitmap,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockAlpha8ResourceCache)
        verifyNoInteractions(mockAlpha8BitmapConverter)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    // endregion
}
