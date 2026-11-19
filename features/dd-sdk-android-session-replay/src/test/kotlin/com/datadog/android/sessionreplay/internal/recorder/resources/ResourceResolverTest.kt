/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
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
import java.util.concurrent.RejectedExecutionException

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

    @Mock
    lateinit var mockAlpha8BitmapConverter: BitmapConverter

    @Mock
    lateinit var mockAlpha8ResourceCache: Alpha8ResourceCache

    @Mock
    lateinit var mockPendingImageRequests: PendingImageRequests

    @Mock
    lateinit var mockCopiedBitmap: Bitmap

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockBitmapWrapper: BitmapWrapper

    @Mock
    lateinit var mockWrappedBitmap: Bitmap

    private var fakeBitmapWidth: Int = 1

    private var fakeBitmapHeight: Int = 1

    @StringForgery
    lateinit var fakeResourceKey: String

    @StringForgery
    lateinit var fakeResourceId: String

    private lateinit var fakeImageCompressionByteArray: ByteArray

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockPendingImageRequests.maxPendingBytes).thenReturn(PendingImageRequests.MAX_PENDING_BYTES)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(
            mockBitmapDrawable
        )
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(any())).thenReturn(fakeResourceKey)
        whenever(mockDrawableCopier.copy(eq(mockDrawable), any())).thenReturn(mockDrawable)
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeBitmapWidth = forge.anInt(min = 1, max = 1000)
        fakeBitmapHeight = forge.anInt(min = 1, max = 1000)

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
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockCopiedBitmap)

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
            .thenReturn(false, false, false)
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
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            customResourceIdCacheKey = null,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        inOrder(mockWebPImageCompression, mockDrawableUtils) {
            verify(mockWebPImageCompression).compressBitmap(mockBitmap)
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
    fun `M fail the capture W resolveResourceIdFromBitmap() { failed to get image data }`() {
        // Given
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
    fun `M return the raster to the pool before failing W resolveResourceIdFromDrawable() { compression fails }`() {
        // Given
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap)).thenReturn(ByteArray(0))

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        inOrder(mockBitmapCachesManager, mockSerializerCallback) {
            verify(mockBitmapCachesManager).putInBitmapPool(mockBitmap)
            verify(mockSerializerCallback).onFailure()
        }
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W resolveResourceIdFromDrawable() { no bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
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
    fun `M fail without drawing W resolveResourceIdFromDrawable() { bitmap was recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockDrawableUtils, never()).createScaledBitmap(any(), any())
        verifyNoDrawing()
    }

    @Test
    fun `M use scaled bitmap from bitmapDrawable W resolveResourceIdFromDrawable() { has bitmap }`() {
        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
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
    fun `M release a copy made by scaling W resolveResourceIdFromDrawable() { copy not pooled }`(
        @Mock mockScaledCopy: Bitmap
    ) {
        // Given
        whenever(mockScaledCopy.width).thenReturn(fakeBitmapWidth)
        whenever(mockScaledCopy.height).thenReturn(fakeBitmapHeight)
        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap)).thenReturn(mockScaledCopy)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        inOrder(mockWebPImageCompression, mockScaledCopy, mockSerializerCallback) {
            verify(mockWebPImageCompression).compressBitmap(mockScaledCopy)
            verify(mockScaledCopy).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockBitmapCachesManager, never()).putInBitmapPool(mockScaledCopy)
        verify(mockBitmap, never()).recycle()
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
        verify(mockResizedBitmap, never()).recycle()
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
        @Mock mockFirstCopy: Bitmap,
        @Mock mockSecondCopy: Bitmap,
        @StringForgery fakeFirstResourceId: String,
        @StringForgery fakeSecondResourceId: String,
        forge: Forge
    ) {
        // Given
        val firstBitmapCompression = forge.aString().toByteArray()
        val secondBitmapCompression = forge.aString().toByteArray()
        whenever(mockFirstBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockFirstCopy)
        whenever(mockSecondBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockSecondCopy)
        whenever(mockWebPImageCompression.compressBitmap(mockFirstCopy)).thenReturn(firstBitmapCompression)
        whenever(mockWebPImageCompression.compressBitmap(mockSecondCopy)).thenReturn(secondBitmapCompression)
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

    private fun createResourceResolver(
        pendingImageRequests: PendingImageRequests = PendingImageRequests(mockLogger, maxHeapBytes = Long.MAX_VALUE),
        drawableReferenceFactory: (Drawable) -> WeakReference<Drawable> = { WeakReference(it) }
    ): ResourceResolver = ResourceResolver(
        applicationContext = mockApplicationContext,
        logger = mockLogger,
        pathUtils = mockPathUtils,
        threadPoolExecutor = mockExecutorService,
        drawableUtils = mockDrawableUtils,
        webPImageCompression = mockWebPImageCompression,
        md5HashGenerator = mockMD5HashGenerator,
        recordedDataQueueHandler = mockRecordedDataQueueHandler,
        bitmapCachesManager = mockBitmapCachesManager,
        alpha8BitmapConverter = mockAlpha8BitmapConverter,
        alpha8ResourceCache = mockAlpha8ResourceCache,
        pendingImageRequests = pendingImageRequests,
        drawableReferenceFactory = drawableReferenceFactory,
        buildSdkVersionProvider = mockBuildSdkVersionProvider,
        bitmapWrapper = mockBitmapWrapper
    )

    private fun resolveBitmap(callback: ResourceResolverCallback) {
        testedResourceResolver.resolveResourceIdFromBitmap(bitmap = mockBitmap, resourceResolverCallback = callback)
    }

    private fun useFakeResourceCache() {
        val fakeResourceCache = mutableMapOf<String, String>()
        doAnswer {
            fakeResourceCache[it.getArgument(0)] = it.getArgument(1)
        }.whenever(mockBitmapCachesManager).putInResourceCache(any(), any())
        whenever(mockBitmapCachesManager.getFromResourceCache(any())).thenAnswer {
            fakeResourceCache[it.getArgument<String>(0)]
        }
    }

    private fun captureTasks(): MutableList<Runnable> {
        val tasks = mutableListOf<Runnable>()
        whenever(mockExecutorService.execute(any())).then {
            tasks += it.getArgument<Runnable>(0)
            null
        }
        return tasks
    }

    // At least 2000x2000, so its ARGB_8888 pixels exceed the copy limit.
    private fun useLargeBitmap(forge: Forge) {
        whenever(mockBitmap.width).thenReturn(forge.anInt(min = 2000, max = 100_000))
        whenever(mockBitmap.height).thenReturn(forge.anInt(min = 2000, max = 100_000))
    }

    // Above the copy limit but within the budget, so it is read back in full and then scaled.
    private fun useLargeHardwareBitmap(forge: Forge) {
        useHardwareBitmap(width = forge.anInt(min = 2000, max = 4096), height = forge.anInt(min = 2000, max = 4096))
    }

    private fun useHardwareBitmap(width: Int, height: Int) {
        whenever(mockBitmap.width).thenReturn(width)
        whenever(mockBitmap.height).thenReturn(height)
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.HARDWARE)
    }

    // Another capture holds all of the budget but what a capture of the default bitmap drawable reserves.
    private fun useBusyBudget(otherCallback: ResourceResolverCallback) {
        val pendingImageRequests = PendingImageRequests(mockLogger, maxHeapBytes = Long.MAX_VALUE)
        val reservedBytes = fakeBitmapWidth.toLong() * fakeBitmapHeight * 4
        pendingImageRequests.start(
            key = "$fakeResourceKey-busy",
            byteCount = PendingImageRequests.MAX_PENDING_BYTES - reservedBytes,
            callback = otherCallback
        )
        testedResourceResolver = createResourceResolver(pendingImageRequests = pendingImageRequests)
    }

    private fun verifyNoDrawing() {
        verify(mockDrawableUtils, never()).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    private fun useUnpremultipliedBitmap(bitmap: Bitmap = mockBitmap, width: Int, height: Int) {
        whenever(bitmap.width).thenReturn(width)
        whenever(bitmap.height).thenReturn(height)
        whenever(bitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
        whenever(bitmap.isPremultiplied).thenReturn(false)
        whenever(bitmap.hasAlpha()).thenReturn(true)
    }

    // From API 31 the hardware buffer is wrapped and read back on the worker.
    private fun useWrappedHardwareBitmap(width: Int, height: Int) {
        useHardwareBitmap(width = width, height = height)
        whenever(mockBuildSdkVersionProvider.isAtLeastS).thenReturn(true)
        whenever(mockBitmapWrapper.wrapHardwareBuffer(mockBitmap)).thenReturn(mockWrappedBitmap)
        whenever(mockWrappedBitmap.width).thenReturn(width)
        whenever(mockWrappedBitmap.height).thenReturn(height)
    }

    private fun resolveDrawable(
        callback: ResourceResolverCallback,
        drawable: Drawable = mockDrawable,
        drawableWidth: Int = 0,
        drawableHeight: Int = 0,
        customResourceIdCacheKey: String? = null
    ) {
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = drawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = drawableWidth,
            drawableHeight = drawableHeight,
            customResourceIdCacheKey = customResourceIdCacheKey,
            resourceResolverCallback = callback
        )
    }

    @Test
    fun `M use original drawable for cache write W resolveResourceId() { cache miss }`(
        @Mock mockCopiedDrawable: Drawable
    ) {
        // Given
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenReturn(mockCopiedDrawable)

        // When
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
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
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback)
        val copiedBeforeTheTaskRan = mockingDetails(mockDrawableCopier).invocations.isNotEmpty()
        tasks.single().run()

        // Then
        assertThat(copiedBeforeTheTaskRan).isFalse()
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromDrawable() { drawable released before the task runs }`() {
        // Given
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockDrawableCopier)
    }

    @Test
    fun `M resolve the image once for both captures W resolveResourceIdFromDrawable() { image in flight }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback)
        resolveDrawable(mockOtherCallback)
        tasks.toList().forEach { it.run() }

        // Then
        assertThat(tasks).hasSize(1)
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M use the published resource and release the capture W resolveResourceIdFromDrawable() { resolved meanwhile }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(fakeResourceKey)).thenReturn(null, fakeResourceId, null)
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback)
        resolveDrawable(mockOtherCallback)

        // Then
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        assertThat(tasks).hasSize(1)
    }

    @Test
    fun `M capture each drawable W resolveResourceIdFromDrawable() twice { custom key of two drawables }`(
        @StringForgery fakeCacheKey: String,
        @Mock mockOtherDrawable: Drawable,
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        whenever(mockDrawableCopier.copy(eq(mockOtherDrawable), any())).thenReturn(mockOtherDrawable)
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback, customResourceIdCacheKey = fakeCacheKey)
        resolveDrawable(mockOtherCallback, drawable = mockOtherDrawable, customResourceIdCacheKey = fakeCacheKey)
        tasks.toList().forEach { it.run() }

        // Then
        assertThat(tasks).hasSize(2)
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
        verify(mockDrawableCopier).copy(mockOtherDrawable, mockResources)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M resolve the image once W resolveResourceIdFromDrawable() twice { custom key of one drawable }`(
        @StringForgery fakeCacheKey: String,
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback, customResourceIdCacheKey = fakeCacheKey)
        resolveDrawable(mockOtherCallback, customResourceIdCacheKey = fakeCacheKey)
        tasks.toList().forEach { it.run() }

        // Then
        assertThat(tasks).hasSize(1)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M copy the copier's bitmap W resolveResourceIdFromDrawable() { another bitmap within the reservation }`(
        @Mock mockCopiedDrawable: BitmapDrawable,
        @Mock mockOtherBitmap: Bitmap,
        @Mock mockOtherCopy: Bitmap
    ) {
        // Given
        useUnpremultipliedBitmap(mockOtherBitmap, width = fakeBitmapWidth, height = fakeBitmapHeight)
        whenever(mockOtherBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockOtherCopy)
        whenever(mockCopiedDrawable.bitmap).thenReturn(mockOtherBitmap)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockCopiedDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        inOrder(mockWebPImageCompression, mockOtherCopy, mockSerializerCallback) {
            verify(mockWebPImageCompression).compressBitmap(mockOtherCopy)
            verify(mockOtherCopy).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
    }

    @Test
    fun `M copy the copier's bitmap W resolveResourceIdFromDrawable() { another bitmap beyond the reservation }`(
        @Mock mockCopiedDrawable: BitmapDrawable,
        @Mock mockOtherBitmap: Bitmap,
        @Mock mockOtherCopy: Bitmap
    ) {
        // Given
        useUnpremultipliedBitmap(mockOtherBitmap, width = fakeBitmapWidth + 1, height = fakeBitmapHeight)
        whenever(mockOtherBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockOtherCopy)
        whenever(mockCopiedDrawable.bitmap).thenReturn(mockOtherBitmap)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockCopiedDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockWebPImageCompression).compressBitmap(mockOtherCopy)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M draw into the reserved raster W resolveResourceIdFromDrawable() { another bitmap the budget cannot cover }`(
        @Mock mockCopiedDrawable: BitmapDrawable,
        @Mock mockOtherBitmap: Bitmap,
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        useBusyBudget(mockOtherCallback)
        whenever(mockOtherBitmap.width).thenReturn(fakeBitmapWidth + 1)
        whenever(mockOtherBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockCopiedDrawable.bitmap).thenReturn(mockOtherBitmap)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockCopiedDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = eq(mockCopiedDrawable),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
        verify(mockDrawableUtils, never()).createScaledBitmap(eq(mockOtherBitmap), any())
    }

    @Test
    fun `M fail without drawing W resolveResourceIdFromDrawable() { unpremultiplied bitmap the budget cannot cover }`(
        @Mock mockCopiedDrawable: BitmapDrawable,
        @Mock mockOtherBitmap: Bitmap,
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        useBusyBudget(mockOtherCallback)
        useUnpremultipliedBitmap(mockOtherBitmap, width = fakeBitmapWidth + 1, height = fakeBitmapHeight)
        whenever(mockCopiedDrawable.bitmap).thenReturn(mockOtherBitmap)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockCopiedDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockOtherBitmap, never()).copy(any(), any())
        verifyNoDrawing()
    }

    @Test
    fun `M draw into the reserved raster W resolveResourceIdFromDrawable() { tiled unpremultiplied bitmap uncovered }`(
        @Mock mockCopiedDrawable: BitmapDrawable,
        @Mock mockOtherBitmap: Bitmap,
        @Mock mockOtherCallback: ResourceResolverCallback,
        forge: Forge
    ) {
        // Given
        useBusyBudget(mockOtherCallback)
        useUnpremultipliedBitmap(mockOtherBitmap, width = fakeBitmapWidth + 1, height = fakeBitmapHeight)
        whenever(mockCopiedDrawable.bitmap).thenReturn(mockOtherBitmap)
        whenever(mockCopiedDrawable.tileModeX).thenReturn(forge.getForgery<Shader.TileMode>())
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockCopiedDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = eq(mockCopiedDrawable),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
        verify(mockOtherBitmap, never()).copy(any(), any())
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromDrawable() { copier's bitmap too large to copy in full }`(
        @Mock mockCopiedDrawable: BitmapDrawable,
        @Mock mockOtherBitmap: Bitmap,
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int,
        forge: Forge
    ) {
        // Given
        useLargeHardwareBitmap(forge)
        useUnpremultipliedBitmap(mockOtherBitmap, width = fakeWidth, height = fakeHeight)
        whenever(mockCopiedDrawable.bitmap).thenReturn(mockOtherBitmap)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockCopiedDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockOtherBitmap, never()).copy(any(), any())
        verifyNoDrawing()
    }

    @Test
    fun `M fail without drawing W resolveResourceIdFromDrawable() { hardware bitmap read back fails }`() {
        // Given
        useHardwareBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(null)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoDrawing()
    }

    @Test
    fun `M copy at full size and release W resolveResourceIdFromDrawable() { unpremultiplied bitmap }`() {
        // Given
        useUnpremultipliedBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        inOrder(mockBitmap, mockWebPImageCompression, mockCopiedBitmap, mockSerializerCallback) {
            verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
            verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockDrawableUtils, never()).createScaledBitmap(any(), any())
        verify(mockBitmap, never()).recycle()
    }

    @Test
    fun `M reserve the full copy W resolveResourceIdFromDrawable() { large unpremultiplied bitmap }`(
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int
    ) {
        // Given
        useUnpremultipliedBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockBitmapDrawable))
            .thenReturn(fakeResourceKey)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockPendingImageRequests)
            .start(fakeResourceKey, fakeWidth.toLong() * fakeHeight * 4, mockSerializerCallback)
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromDrawable() { unpremultiplied bitmap too large }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int
    ) {
        // Given
        useUnpremultipliedBitmap(width = fakeWidth, height = fakeHeight)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoDrawing()
    }

    @Test
    fun `M unregister the bitmap caches W unregisterCallbacks()`() {
        // When
        testedResourceResolver.unregisterCallbacks()

        // Then
        verify(mockBitmapCachesManager).unregisterCallbacks(mockApplicationContext)
    }

    @Test
    fun `M fail the capture W resolveResourceIdFromDrawable() { task rejected }`() {
        // Given
        val fakeRejection = RejectedExecutionException()
        whenever(mockExecutorService.execute(any())).thenThrow(fakeRejection)

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Unable to schedule Session Replay image processing",
            throwable = fakeRejection
        )
    }

    @Test
    fun `M fail the capture and propagate W resolveResourceIdFromDrawable() { copying throws }`() {
        // Given
        val fakeError = IllegalStateException()
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenThrow(fakeError)

        // When
        val actualError = assertThrows<IllegalStateException> { resolveDrawable(mockSerializerCallback) }

        // Then
        assertThat(actualError).isSameAs(fakeError)
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M not schedule work W resolveResourceIdFromDrawable() { capture joined or refused }`() {
        // Given
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(eq(fakeResourceKey), any(), eq(mockSerializerCallback))
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M reserve the source as ARGB_8888 W resolveResourceIdFromDrawable() { bitmap drawable }`(
        @IntForgery(min = 1) fakeAllocationByteCount: Int
    ) {
        // Given
        whenever(mockBitmap.allocationByteCount).thenReturn(fakeAllocationByteCount)
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockBitmapDrawable))
            .thenReturn(fakeResourceKey)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockPendingImageRequests)
            .start(fakeResourceKey, fakeBitmapWidth.toLong() * fakeBitmapHeight * 4, mockSerializerCallback)
    }

    @Test
    fun `M reserve the fallback raster W resolveResourceIdFromDrawable() { bitmap drawable drawn larger }`(
        @IntForgery(min = 1, max = 99) fakeBitmapSide: Int,
        @IntForgery(min = 100, max = 1000) fakeWidth: Int,
        @IntForgery(min = 100, max = 1000) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmap.width).thenReturn(fakeBitmapSide)
        whenever(mockBitmap.height).thenReturn(fakeBitmapSide)
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockBitmapDrawable))
            .thenReturn(fakeResourceKey)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(
            mockSerializerCallback,
            drawable = mockBitmapDrawable,
            drawableWidth = fakeWidth,
            drawableHeight = fakeHeight
        )

        // Then
        verify(mockPendingImageRequests)
            .start(fakeResourceKey, fakeWidth.toLong() * fakeHeight * 4, mockSerializerCallback)
    }

    @Test
    fun `M reserve at most the drawing limit W resolveResourceIdFromDrawable() { large bitmap drawable }`(
        forge: Forge
    ) {
        // Given
        useLargeBitmap(forge)
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockBitmapDrawable))
            .thenReturn(fakeResourceKey)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockPendingImageRequests).start(
            fakeResourceKey,
            DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT.toLong(),
            mockSerializerCallback
        )
    }

    @Test
    fun `M draw into ARGB_8888 and release the copy W resolveResourceIdFromDrawable() { RGBA_F16 bitmap }`(
        @Mock mockScaledBitmap: Bitmap
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.RGBA_F16)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        inOrder(mockWebPImageCompression, mockScaledBitmap, mockSerializerCallback) {
            verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
            verify(mockScaledBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockDrawableUtils, never()).createScaledBitmap(any(), any())
        verify(mockBitmapCachesManager, never()).putInBitmapPool(mockScaledBitmap)
        verify(mockBitmap, never()).recycle()
    }

    @Test
    fun `M reserve the readback and the scaled copy W resolveResourceIdFromDrawable() { large hardware bitmap }`(
        forge: Forge
    ) {
        // Given
        useLargeHardwareBitmap(forge)
        val expectedByteCount = mockBitmap.width.toLong() * mockBitmap.height * 4 +
            DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockBitmapDrawable))
            .thenReturn(fakeResourceKey)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockPendingImageRequests).start(fakeResourceKey, expectedByteCount, mockSerializerCallback)
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromDrawable() { hardware bitmap larger than the budget }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoDrawing()
    }

    @Test
    fun `M capture the copier's drawable W resolveResourceIdFromDrawable() { bitmap too large replaced by the copier }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(mockDrawable)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M reserve only the raster W resolveResourceIdFromDrawable() { bitmap too large to copy in full }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int,
        @IntForgery(min = 1, max = 1000) fakeDrawableWidth: Int,
        @IntForgery(min = 1, max = 1000) fakeDrawableHeight: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(mockBitmapDrawable))
            .thenReturn(fakeResourceKey)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(
            mockSerializerCallback,
            drawable = mockBitmapDrawable,
            drawableWidth = fakeDrawableWidth,
            drawableHeight = fakeDrawableHeight
        )

        // Then
        verify(mockPendingImageRequests)
            .start(fakeResourceKey, fakeDrawableWidth.toLong() * fakeDrawableHeight * 4, mockSerializerCallback)
    }

    @Test
    fun `M read back, compress and release a copy W resolveResourceIdFromDrawable() { hardware bitmap }`() {
        // Given
        useHardwareBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        inOrder(mockWebPImageCompression, mockCopiedBitmap, mockSerializerCallback) {
            verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockDrawableUtils, never()).createScaledBitmap(any(), any())
        verify(mockBitmapCachesManager, never()).putInBitmapPool(mockCopiedBitmap)
        verify(mockBitmap, never()).recycle()
    }

    @Test
    fun `M reserve the drawn pixels W resolveResourceIdFromDrawable() { other drawable }`(
        @IntForgery(min = 1, max = 1000) fakeWidth: Int,
        @IntForgery(min = 1, max = 1000) fakeHeight: Int
    ) {
        // Given
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawableWidth = fakeWidth, drawableHeight = fakeHeight)

        // Then
        verify(mockPendingImageRequests)
            .start(fakeResourceKey, fakeWidth.toLong() * fakeHeight * 4, mockSerializerCallback)
    }

    @Test
    fun `M reserve at most the drawing limit W resolveResourceIdFromDrawable() { large drawable }`(
        @IntForgery(min = 2000, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 2000, max = 100_000) fakeHeight: Int
    ) {
        // Given
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawableWidth = fakeWidth, drawableHeight = fakeHeight)

        // Then
        verify(mockPendingImageRequests).start(
            fakeResourceKey,
            DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT.toLong(),
            mockSerializerCallback
        )
    }

    // region bitmap capture

    @Test
    fun `M compress a copy and release it W resolveResourceIdFromBitmap()`() {
        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockWebPImageCompression, mockCopiedBitmap) {
            verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
            verify(mockCopiedBitmap).recycle()
        }
        verify(mockBitmap, never()).recycle()
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M release the copy before completing W resolveResourceIdFromBitmap()`() {
        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockCopiedBitmap, mockBitmapCachesManager, mockSerializerCallback) {
            verify(mockCopiedBitmap).recycle()
            verify(mockBitmapCachesManager).putInResourceCache(any(), eq(fakeResourceId))
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
    }

    @Test
    fun `M release the copy before failing W resolveResourceIdFromBitmap() { compression fails }`() {
        // Given
        whenever(mockWebPImageCompression.compressBitmap(mockCopiedBitmap)).thenReturn(ByteArray(0))

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockCopiedBitmap, mockSerializerCallback) {
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onFailure()
        }
    }

    @Test
    fun `M copy at full size W resolveResourceIdFromBitmap() { large unpremultiplied bitmap }`(
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int
    ) {
        // Given
        useUnpremultipliedBitmap(width = fakeWidth, height = fakeHeight)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
        verify(mockDrawableUtils, never()).createScaledArgb8888Bitmap(any())
        verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M reserve the full copy W resolveResourceIdFromBitmap() { large unpremultiplied bitmap }`(
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int
    ) {
        // Given
        useUnpremultipliedBitmap(width = fakeWidth, height = fakeHeight)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests)
            .start(any(), eq(fakeWidth.toLong() * fakeHeight * 4), eq(mockSerializerCallback))
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { unpremultiplied bitmap larger than the budget }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int
    ) {
        // Given
        useUnpremultipliedBitmap(width = fakeWidth, height = fakeHeight)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M copy and compress once W resolveResourceIdFromBitmap() twice { unchanged bitmap }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        useFakeResourceCache()

        // When
        resolveBitmap(mockSerializerCallback)
        resolveBitmap(mockOtherCallback)

        // Then
        verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
        verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M copy again W resolveResourceIdFromBitmap() twice { bitmap changed in between }`(
        @IntForgery(min = 0, max = 1_000_000) fakeGenerationId: Int
    ) {
        // Given
        useFakeResourceCache()
        whenever(mockBitmap.generationId).thenReturn(fakeGenerationId, fakeGenerationId + 1)

        // When
        resolveBitmap(mockSerializerCallback)
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockBitmap, times(2)).copy(Bitmap.Config.ARGB_8888, false)
    }

    @Test
    fun `M copy the bitmap once for both captures W resolveResourceIdFromBitmap() { bitmap in flight }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveBitmap(mockSerializerCallback)
        resolveBitmap(mockOtherCallback)
        tasks.toList().forEach { it.run() }

        // Then
        assertThat(tasks).hasSize(1)
        verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verify(mockOtherCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M use the published resource and release the capture W resolveResourceIdFromBitmap() { resolved meanwhile }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(any())).thenReturn(null, fakeResourceId, null)
        val tasks = captureTasks()

        // When
        resolveBitmap(mockSerializerCallback)
        resolveBitmap(mockOtherCallback)

        // Then
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
        verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
        assertThat(tasks).hasSize(1)
    }

    @Test
    fun `M not copy W resolveResourceIdFromBitmap() { capture joined or refused }`() {
        // Given
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(any(), any(), eq(mockSerializerCallback))
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M reserve the copied pixels W resolveResourceIdFromBitmap()`(
        @IntForgery(min = 1, max = 1000) fakeWidth: Int,
        @IntForgery(min = 1, max = 1000) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(fakeHeight)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(
            mockPendingImageRequests
        ).start(any(), eq(fakeWidth.toLong() * fakeHeight * 4), eq(mockSerializerCallback))
    }

    @Test
    fun `M reserve at most the copy limit W resolveResourceIdFromBitmap() { large bitmap }`(forge: Forge) {
        // Given
        useLargeBitmap(forge)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(
            any(),
            eq(DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT.toLong()),
            eq(mockSerializerCallback)
        )
    }

    @Test
    fun `M reserve the readback and the scaled copy W resolveResourceIdFromBitmap() { large hardware bitmap }`(
        forge: Forge
    ) {
        // Given
        useLargeHardwareBitmap(forge)
        val expectedByteCount = mockBitmap.width.toLong() * mockBitmap.height * 4 +
            DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(any(), eq(expectedByteCount), eq(mockSerializerCallback))
    }

    @Test
    fun `M reserve the buffer and its readback W resolveResourceIdFromBitmap() { hardware bitmap, API 31+ }`(
        @IntForgery(min = 1, max = 8_000_000) fakeAllocationByteCount: Int
    ) {
        // Given
        useWrappedHardwareBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)
        whenever(mockBitmap.allocationByteCount).thenReturn(fakeAllocationByteCount)
        val expectedByteCount = 2L * fakeAllocationByteCount + fakeBitmapWidth.toLong() * fakeBitmapHeight * 4
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(any(), eq(expectedByteCount), eq(mockSerializerCallback))
    }

    @Test
    fun `M reserve the buffer, readback and scaled copy W resolveResourceIdFromBitmap() { large, API 31+ }`(
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int,
        @IntForgery(min = 1, max = PendingImageRequests.MAX_PENDING_BYTES.toInt()) fakeAllocationByteCount: Int
    ) {
        // Given
        useWrappedHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockBitmap.allocationByteCount).thenReturn(fakeAllocationByteCount)
        val expectedByteCount = fakeAllocationByteCount + fakeWidth.toLong() * fakeHeight * 4 +
            maxOf(fakeAllocationByteCount, DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(any(), eq(expectedByteCount), eq(mockSerializerCallback))
    }

    @Test
    fun `M reserve the readback temporary W resolveResourceIdFromBitmap() { large hardware bitmap }`(
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int,
        @IntForgery(min = 1, max = PendingImageRequests.MAX_PENDING_BYTES.toInt()) fakeAllocationByteCount: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockBitmap.allocationByteCount).thenReturn(fakeAllocationByteCount)
        val expectedByteCount = fakeWidth.toLong() * fakeHeight * 4 +
            maxOf(fakeAllocationByteCount, DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(any(), eq(expectedByteCount), eq(mockSerializerCallback))
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { hardware readback larger than the budget }`(
        @IntForgery(min = 1, max = 4096) fakeWidth: Int,
        @IntForgery(min = 1, max = 4096) fakeHeight: Int,
        @IntForgery(min = PendingImageRequests.MAX_PENDING_BYTES.toInt() + 1) fakeAllocationByteCount: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockBitmap.allocationByteCount).thenReturn(fakeAllocationByteCount)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M scale on the worker W resolveResourceIdFromBitmap() { long hardware bitmap within the copy limit }`(
        @Mock mockScaledBitmap: Bitmap,
        @IntForgery(min = DrawableUtils.MAX_WEBP_DIMENSION + 1, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 1, max = 26) fakeHeight: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockCopiedBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M reserve the readback and the scaled copy W resolveResourceIdFromBitmap() { long hardware bitmap }`(
        @IntForgery(min = DrawableUtils.MAX_WEBP_DIMENSION + 1, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 1, max = 26) fakeHeight: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests)
            .start(any(), eq(2L * fakeWidth * fakeHeight * 4), eq(mockSerializerCallback))
    }

    @Test
    fun `M scale to the WebP edge limit W resolveResourceIdFromBitmap() { long bitmap within the copy limit }`(
        @Mock mockScaledBitmap: Bitmap,
        @IntForgery(min = DrawableUtils.MAX_WEBP_DIMENSION + 1, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 1, max = 26) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(fakeHeight)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { hardware bitmap larger than the budget }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int
    ) {
        // Given
        useHardwareBitmap(width = fakeWidth, height = fakeHeight)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockPendingImageRequests, never()).start(any(), any(), any())
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M compress a scaled copy and release it W resolveResourceIdFromBitmap() { large bitmap }`(
        @Mock mockScaledBitmap: Bitmap,
        forge: Forge
    ) {
        // Given
        useLargeBitmap(forge)
        whenever(mockBitmap.config).thenReturn(
            forge.anElementFrom(Bitmap.Config.ARGB_8888, Bitmap.Config.RGB_565, Bitmap.Config.RGBA_F16)
        )
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockWebPImageCompression, mockScaledBitmap) {
            verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
            verify(mockScaledBitmap).recycle()
        }
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockBitmap, never()).recycle()
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M scale a full copy and release both W resolveResourceIdFromBitmap() { large hardware bitmap }`(
        @Mock mockScaledBitmap: Bitmap,
        forge: Forge
    ) {
        // Given
        useLargeHardwareBitmap(forge)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockCopiedBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(
            mockBitmap,
            mockExecutorService,
            mockDrawableUtils,
            mockCopiedBitmap,
            mockWebPImageCompression,
            mockScaledBitmap,
            mockSerializerCallback
        ) {
            verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
            verify(mockExecutorService).execute(any())
            verify(mockDrawableUtils).createScaledArgb8888Bitmap(mockCopiedBitmap)
            verify(mockCopiedBitmap).recycle()
            verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
            verify(mockScaledBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockDrawableUtils, never()).createScaledArgb8888Bitmap(mockBitmap)
        verify(mockBitmap, never()).recycle()
    }

    @Test
    fun `M read back on the worker W resolveResourceIdFromBitmap() { hardware bitmap, API 31+ }`() {
        // Given
        useWrappedHardwareBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)
        whenever(mockWrappedBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockCopiedBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(
            mockExecutorService,
            mockWrappedBitmap,
            mockWebPImageCompression,
            mockCopiedBitmap,
            mockSerializerCallback
        ) {
            verify(mockExecutorService).execute(any())
            verify(mockWrappedBitmap).copy(Bitmap.Config.ARGB_8888, false)
            verify(mockWrappedBitmap).recycle()
            verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockBitmap, never()).recycle()
    }

    @Test
    fun `M read back and scale on the worker W resolveResourceIdFromBitmap() { large hardware bitmap, API 31+ }`(
        @Mock mockScaledBitmap: Bitmap,
        forge: Forge
    ) {
        // Given
        val fakeWidth = forge.anInt(min = 2000, max = 4096)
        val fakeHeight = forge.anInt(min = 2000, max = 4096)
        useWrappedHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockWrappedBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(mockCopiedBitmap)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockCopiedBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(
            mockExecutorService,
            mockWrappedBitmap,
            mockCopiedBitmap,
            mockWebPImageCompression,
            mockScaledBitmap,
            mockSerializerCallback
        ) {
            verify(mockExecutorService).execute(any())
            verify(mockWrappedBitmap).copy(Bitmap.Config.ARGB_8888, false)
            verify(mockCopiedBitmap).recycle()
            verify(mockWrappedBitmap).recycle()
            verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
            verify(mockScaledBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
        verify(mockBitmap, never()).copy(any(), any())
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M read back here and scale on the worker W resolveResourceIdFromBitmap() { large, API 31+, wrap fails }`(
        @Mock mockScaledBitmap: Bitmap,
        @IntForgery(min = 2000, max = 4096) fakeWidth: Int,
        @IntForgery(min = 2000, max = 4096) fakeHeight: Int
    ) {
        // Given
        useWrappedHardwareBitmap(width = fakeWidth, height = fakeHeight)
        whenever(mockBitmapWrapper.wrapHardwareBuffer(mockBitmap)).thenReturn(null)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockCopiedBitmap)).thenReturn(mockScaledBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockBitmap, mockExecutorService, mockDrawableUtils, mockWebPImageCompression) {
            verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
            verify(mockExecutorService).execute(any())
            verify(mockDrawableUtils).createScaledArgb8888Bitmap(mockCopiedBitmap)
            verify(mockWebPImageCompression).compressBitmap(mockScaledBitmap)
        }
        verify(mockDrawableUtils, never()).createScaledArgb8888Bitmap(mockBitmap)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M read back instead W resolveResourceIdFromBitmap() { hardware bitmap, API 31+, wrap fails }`() {
        // Given
        useWrappedHardwareBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)
        whenever(mockBitmapWrapper.wrapHardwareBuffer(mockBitmap)).thenReturn(null)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockBitmap, mockExecutorService, mockWebPImageCompression, mockCopiedBitmap, mockSerializerCallback) {
            verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
            verify(mockExecutorService).execute(any())
            verify(mockWebPImageCompression).compressBitmap(mockCopiedBitmap)
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onSuccess(fakeResourceId)
        }
    }

    @Test
    fun `M release the wrapper and fail W resolveResourceIdFromBitmap() { API 31+ hardware, readback fails }`() {
        // Given
        useWrappedHardwareBitmap(width = fakeBitmapWidth, height = fakeBitmapHeight)
        whenever(mockWrappedBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(null)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockWrappedBitmap).recycle()
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockWebPImageCompression)
    }

    @Test
    fun `M release the full copy and fail W resolveResourceIdFromBitmap() { large hardware bitmap, scaling fails }`(
        forge: Forge
    ) {
        // Given
        useLargeHardwareBitmap(forge)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockCopiedBitmap)).thenReturn(null)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockCopiedBitmap, mockSerializerCallback) {
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onFailure()
        }
        verifyNoInteractions(mockWebPImageCompression)
    }

    @Test
    fun `M fail the capture W resolveResourceIdFromBitmap() { large bitmap, scaling fails }`(forge: Forge) {
        // Given
        useLargeBitmap(forge)
        whenever(mockDrawableUtils.createScaledArgb8888Bitmap(mockBitmap)).thenReturn(null)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M fail the capture W resolveResourceIdFromBitmap() { copy fails }`() {
        // Given
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenReturn(null)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M register the bitmap caches W registerCallbacks()`() {
        // When
        testedResourceResolver.registerCallbacks()

        // Then
        verify(mockBitmapCachesManager).registerCallbacks(mockApplicationContext)
    }

    @Test
    fun `M not register the bitmap caches W resolveResourceIdFromBitmap() + resolveResourceIdFromDrawable()`() {
        // When
        resolveBitmap(mockSerializerCallback)
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockBitmapCachesManager, never()).registerCallbacks(any())
    }

    @Test
    fun `M fail the capture W resolveResourceIdFromBitmap() { task rejected }`() {
        // Given
        val fakeRejection = RejectedExecutionException()
        whenever(mockExecutorService.execute(any())).thenThrow(fakeRejection)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockCopiedBitmap, mockSerializerCallback) {
            verify(mockCopiedBitmap).recycle()
            verify(mockSerializerCallback).onFailure()
        }
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Unable to schedule Session Replay image processing",
            throwable = fakeRejection
        )
    }

    @Test
    fun `M fail the capture and report W resolveResourceIdFromBitmap() { copy throws }`(forge: Forge) {
        // Given
        val fakeError = forge.anElementFrom(IllegalArgumentException(), IllegalStateException())
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenThrow(fakeError)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockExecutorService)
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Unable to copy a bitmap for Session Replay",
            throwable = fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { bitmap recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
    }

    @Test
    fun `M copy, convert and release W resolveResourceIdFromBitmap() { ALPHA_8 bitmap }`(
        @Mock mockAlpha8Copy: Bitmap
    ) {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockBitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Copy)
        whenever(mockAlpha8Copy.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8BitmapConverter.convertAlpha8BitmapToArgb8888(mockAlpha8Copy)).thenReturn(mockCopiedBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockBitmap, mockExecutorService, mockAlpha8BitmapConverter, mockCopiedBitmap, mockAlpha8Copy) {
            verify(mockBitmap).copy(Bitmap.Config.ALPHA_8, false)
            verify(mockExecutorService).execute(any())
            verify(mockAlpha8BitmapConverter).convertAlpha8BitmapToArgb8888(mockAlpha8Copy)
            verify(mockCopiedBitmap).recycle()
            verify(mockAlpha8Copy).recycle()
        }
        verify(mockAlpha8BitmapConverter, never()).convertAlpha8BitmapToArgb8888(mockBitmap)
        verify(mockBitmap, never()).recycle()
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M reserve the copy and its conversion W resolveResourceIdFromBitmap() { ALPHA_8 bitmap }`() {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        val expectedByteCount = fakeBitmapWidth.toLong() * fakeBitmapHeight * 5
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(any(), eq(expectedByteCount), eq(mockSerializerCallback))
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { ALPHA_8 bitmap larger than the budget }`(
        @IntForgery(min = 4097, max = 100_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 100_000) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(fakeHeight)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M fail without scheduling work W resolveResourceIdFromBitmap() { ALPHA_8 copy fails }`() {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockBitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(null)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockExecutorService, mockAlpha8BitmapConverter)
    }

    @Test
    fun `M release the copy and fail W resolveResourceIdFromBitmap() { compression throws }`() {
        // Given
        val fakeError = IllegalStateException()
        whenever(mockWebPImageCompression.compressBitmap(mockCopiedBitmap)).thenThrow(fakeError)

        // When
        val actualError = assertThrows<IllegalStateException> { resolveBitmap(mockSerializerCallback) }

        // Then
        assertThat(actualError).isSameAs(fakeError)
        verify(mockCopiedBitmap).recycle()
        verify(mockSerializerCallback).onFailure()
    }

    // endregion

    // region Alpha8 bitmap tests

    @Test
    fun `M return cached resourceId W resolveResourceIdFromBitmap { alpha8 bitmap, cache hit }`(
        @StringForgery fakeResourceId: String,
        @Mock mockAlpha8Bitmap: Bitmap,
        @Mock mockAlpha8Copy: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8Bitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Copy)
        whenever(mockAlpha8Copy.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8ResourceCache.generateKey(mockAlpha8Copy)).thenReturn(mockCacheKey)
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
        @Mock mockAlpha8Copy: Bitmap,
        @Mock mockConvertedBitmap: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8Bitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Copy)
        whenever(mockAlpha8Copy.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8ResourceCache.generateKey(mockAlpha8Copy)).thenReturn(mockCacheKey)
        whenever(mockAlpha8ResourceCache.get(mockCacheKey)).thenReturn(null)
        whenever(mockAlpha8BitmapConverter.convertAlpha8BitmapToArgb8888(mockAlpha8Copy))
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
        verify(mockAlpha8BitmapConverter).convertAlpha8BitmapToArgb8888(mockAlpha8Copy)
        verify(mockWebPImageCompression).compressBitmap(mockConvertedBitmap)
        verify(mockConvertedBitmap).recycle()
        verify(mockAlpha8ResourceCache).put(mockCacheKey, fakeResourceId)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M fail W resolveResourceIdFromBitmap { alpha8 conversion fails }`(
        @Mock mockAlpha8Bitmap: Bitmap,
        @Mock mockAlpha8Copy: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8Bitmap.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Copy)
        whenever(mockAlpha8Copy.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockAlpha8ResourceCache.generateKey(mockAlpha8Copy)).thenReturn(mockCacheKey)
        whenever(mockAlpha8ResourceCache.get(mockCacheKey)).thenReturn(null)
        whenever(mockAlpha8BitmapConverter.convertAlpha8BitmapToArgb8888(mockAlpha8Copy))
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
        whenever(mockWebPImageCompression.compressBitmap(mockCopiedBitmap))
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
