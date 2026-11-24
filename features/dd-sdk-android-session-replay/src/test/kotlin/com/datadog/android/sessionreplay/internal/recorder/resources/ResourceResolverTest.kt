/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.internal.utils.PathUtils
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

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
    lateinit var mockAlpha8Copy: Bitmap

    @Mock
    lateinit var mockPendingImageRequests: PendingImageRequests

    @Mock
    lateinit var mockCopiedBitmap: Bitmap

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
        whenever(mockBitmapCachesManager.generateResourceKeyFromDrawable(any())).thenReturn(fakeResourceKey)
        whenever(mockDrawableCopier.copy(eq(mockDrawable), any())).thenReturn(mockDrawable)
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeBitmapWidth = forge.anInt(min = 1, max = 1000)
        fakeBitmapHeight = forge.anInt(min = 1, max = 1000)
        whenever(mockPendingImageRequests.maxPendingBytes).thenReturn(Long.MAX_VALUE)

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
    fun `M not use bitmap from bitmapDrawable W resolveResourceIdFromDrawable() { bitmap was recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

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
        pendingImageRequests: PendingImageRequests = PendingImageRequests(mockLogger),
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
        buildSdkVersionProvider = mockBuildSdkVersionProvider
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

    private fun useAlpha8Copy(source: Bitmap) {
        whenever(source.copy(Bitmap.Config.ALPHA_8, false)).thenReturn(mockAlpha8Copy)
        whenever(mockAlpha8Copy.config).thenReturn(Bitmap.Config.ALPHA_8)
    }

    private fun useFullBudget() {
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
        testedResourceResolver = createResourceResolver(
            pendingImageRequests = PendingImageRequests(mockLogger, mockBuildSdkVersionProvider, Long.MAX_VALUE)
        )
    }

    // Like the copier a mapper creates for each snapshot, each call makes another instance of the same class.
    private fun copierOf(copy: Drawable) = DrawableCopier { _, _ -> copy }

    private fun drawableRequestKey(
        drawable: Drawable = mockDrawable,
        width: Int = 0,
        height: Int = 0,
        isAttached: Boolean = false
    ): CaptureKey = CaptureKey.OfDrawable(
        drawable = drawable,
        copier = mockDrawableCopier,
        resources = mockResources,
        displayMetrics = mockDisplayMetrics,
        width = width,
        height = height,
        customResourceIdCacheKey = null,
        holdsDrawableStrongly = !isAttached
    )

    private fun resolveDrawable(
        callback: ResourceResolverCallback,
        drawable: Drawable = mockDrawable,
        drawableWidth: Int = 0,
        drawableHeight: Int = 0,
        customResourceIdCacheKey: String? = null,
        drawableCopier: DrawableCopier = mockDrawableCopier
    ) {
        testedResourceResolver.resolveResourceIdFromDrawable(
            resources = mockResources,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = drawable,
            drawableCopier = drawableCopier,
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
    fun `M capture the drawable W resolveResourceIdFromDrawable() { detached drawable }`() {
        // Given
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M capture each size W resolveResourceIdFromDrawable() twice { drawable in flight at another size }`(
        @Mock mockOtherCallback: ResourceResolverCallback,
        @IntForgery(min = 1, max = 1000) fakeWidth: Int,
        @IntForgery(min = 1, max = 1000) fakeHeight: Int
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback, drawableWidth = fakeWidth, drawableHeight = fakeHeight)
        resolveDrawable(mockOtherCallback, drawableWidth = fakeWidth + 1, drawableHeight = fakeHeight)

        // Then
        assertThat(tasks).hasSize(2)
    }

    @Test
    fun `M capture with each copier W resolveResourceIdFromDrawable() twice { in flight, another copier }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback)
        resolveDrawable(mockOtherCallback, drawableCopier = copierOf(mockDrawable))

        // Then
        assertThat(tasks).hasSize(2)
    }

    @Test
    fun `M capture with each copier W resolveResourceIdFromDrawable() twice { in flight, same copier class }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback, drawableCopier = copierOf(mockDrawable))
        resolveDrawable(mockOtherCallback, drawableCopier = copierOf(mockDrawable))

        // Then
        assertThat(tasks).hasSize(2)
    }

    @Test
    fun `M resolve once W resolveResourceIdFromDrawable() twice { in flight, default copiers }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()

        // When
        resolveDrawable(mockSerializerCallback, drawableCopier = DefaultDrawableCopier())
        resolveDrawable(mockOtherCallback, drawableCopier = DefaultDrawableCopier())

        // Then
        assertThat(tasks).hasSize(1)
    }

    @Test
    fun `M capture the detached drawable W resolveResourceIdFromDrawable() twice { attached, then detached }`(
        @Mock mockView: View,
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })
        val tasks = captureTasks()
        whenever(mockDrawable.callback).thenReturn(mockView)
        resolveDrawable(mockSerializerCallback)
        whenever(mockDrawable.callback).thenReturn(null)

        // When
        resolveDrawable(mockOtherCallback)
        tasks.toList().forEach { it.run() }

        // Then
        assertThat(tasks).hasSize(2)
        verify(mockSerializerCallback).onFailure()
        verify(mockOtherCallback).onSuccess(fakeResourceId)
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
    fun `M capture the drawable W resolveResourceIdFromDrawable() { child of a detached wrapper }`(
        @Mock mockWrapper: InsetDrawable
    ) {
        // Given
        whenever(mockDrawable.callback).thenReturn(mockWrapper)
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M capture the drawable W resolveResourceIdFromDrawable() { callback other than a view }`(
        @Mock mockDrawableCallback: Drawable.Callback
    ) {
        // Given
        whenever(mockDrawable.callback).thenReturn(mockDrawableCallback)
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockDrawableCopier).copy(mockDrawable, mockResources)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M capture the drawable W resolveResourceIdFromDrawable() { wrapper callbacks in a loop }`(
        @Mock mockWrapper: InsetDrawable
    ) {
        // Given
        whenever(mockDrawable.callback).thenReturn(mockWrapper)
        whenever(mockWrapper.callback).thenReturn(mockWrapper)
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromDrawable() { child of a view's wrapper released }`(
        @Mock mockWrapper: LayerDrawable,
        @Mock mockView: View
    ) {
        // Given
        whenever(mockDrawable.callback).thenReturn(mockWrapper)
        whenever(mockWrapper.callback).thenReturn(mockView)
        testedResourceResolver = createResourceResolver(drawableReferenceFactory = { WeakReference(null) })

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockDrawableCopier)
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromDrawable() { attached drawable released before the task runs }`(
        @Mock mockView: View
    ) {
        // Given
        whenever(mockDrawable.callback).thenReturn(mockView)
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
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule resolveResourceId task on the executor",
            throwable = fakeRejection
        )
    }

    @Test
    fun `M fail the capture and report W resolveResourceIdFromDrawable() { copying throws }`(forge: Forge) {
        // Given
        val fakeError = forge.anElementFrom(IllegalStateException(), NullPointerException(), Exception())
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenAnswer { throw fakeError }

        // When
        assertDoesNotThrow { resolveDrawable(mockSerializerCallback) }

        // Then
        verify(mockSerializerCallback).onFailure()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Unable to copy a drawable for Session Replay",
            throwable = fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M fail the capture and propagate W resolveResourceIdFromDrawable() { copying throws an error }`() {
        // Given
        val fakeError = StackOverflowError()
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenThrow(fakeError)

        // When
        val actualError = assertThrows<StackOverflowError> { resolveDrawable(mockSerializerCallback) }

        // Then
        assertThat(actualError).isSameAs(fakeError)
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M fail the capture and propagate W resolveResourceIdFromDrawable() { drawing throws an error }`() {
        // Given
        val fakeError = StackOverflowError()
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
        ).thenThrow(fakeError)

        // When
        val actualError = assertThrows<StackOverflowError> { resolveDrawable(mockSerializerCallback) }

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
        verify(mockPendingImageRequests).start(eq(drawableRequestKey()), any(), eq(mockSerializerCallback))
        verifyNoInteractions(mockExecutorService)
    }

    @Test
    fun `M hold the drawable weakly W resolveResourceIdFromDrawable() { attached to a view }`(
        @Mock mockView: View
    ) {
        // Given
        whenever(mockDrawable.callback).thenReturn(mockView)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests).start(drawableRequestKey(isAttached = true), 0L, mockSerializerCallback)
    }

    @Test
    fun `M hold the drawable and reserve nothing W resolveResourceIdFromDrawable() { detached }`(
        @IntForgery(min = 1) fakeAllocationByteCount: Int
    ) {
        // Given
        whenever(mockBitmap.allocationByteCount).thenReturn(fakeAllocationByteCount)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveDrawable(mockSerializerCallback, drawable = mockBitmapDrawable)

        // Then
        verify(mockPendingImageRequests).start(
            drawableRequestKey(drawable = mockBitmapDrawable),
            0L,
            mockSerializerCallback
        )
    }

    @Test
    fun `M capture again W resolveResourceIdFromDrawable() twice { drawable changes while the worker reads it }`(
        @Mock mockOtherCallback: ResourceResolverCallback
    ) {
        // Given
        val tasks = captureTasks()
        resolveDrawable(mockSerializerCallback)
        whenever(mockDrawableCopier.copy(mockDrawable, mockResources)).thenAnswer {
            resolveDrawable(mockOtherCallback)
            mockDrawable
        }

        // When
        tasks.single().run()

        // Then
        assertThat(tasks).hasSize(2)
    }

    @Test
    fun `M fail the capture W resolveResourceIdFromDrawable() { too many detached drawables held }`() {
        // Given
        val tasks = captureTasks()
        repeat(PendingImageRequests.MAX_HELD_DRAWABLES) {
            resolveDrawable(mock<ResourceResolverCallback>(), drawable = mock<Drawable>())
        }

        // When
        resolveDrawable(mockSerializerCallback, drawable = mock<Drawable>())

        // Then
        assertThat(tasks).hasSize(PendingImageRequests.MAX_HELD_DRAWABLES)
        verify(mockSerializerCallback).onFailure()
    }

    // region bitmap capture

    @Test
    fun `M recycle the copy before completing W resolveResourceIdFromBitmap()`(
        @BoolForgery fakeSucceeded: Boolean
    ) {
        // Given
        if (!fakeSucceeded) {
            whenever(mockWebPImageCompression.compressBitmap(mockCopiedBitmap)).thenReturn(ByteArray(0))
        }

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        inOrder(mockCopiedBitmap, mockSerializerCallback) {
            verify(mockCopiedBitmap).recycle()
            if (fakeSucceeded) {
                verify(mockSerializerCallback).onSuccess(fakeResourceId)
            } else {
                verify(mockSerializerCallback).onFailure()
            }
        }
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { copy larger than the budget }`(
        @IntForgery(min = 4097, max = 10_000) fakeWidth: Int,
        @IntForgery(min = 4097, max = 10_000) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(fakeHeight)
        useFullBudget()

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
    }

    @Test
    fun `M fail without copying W resolveResourceIdFromBitmap() { ALPHA_8 conversion over the budget }`() {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockBitmap.width).thenReturn(3700)
        whenever(mockBitmap.height).thenReturn(3700)
        useFullBudget()

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        verify(mockBitmap, never()).copy(any(), any())
    }

    @Test
    fun `M copy W resolveResourceIdFromBitmap() { copy as large as the budget }`() {
        // Given
        whenever(mockBitmap.width).thenReturn(4096)
        whenever(mockBitmap.height).thenReturn(4096)
        useFullBudget()

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockBitmap).copy(Bitmap.Config.ARGB_8888, false)
    }

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
    fun `M copy again W resolveResourceIdFromBitmap() twice { alpha flag or color space changed in between }`(
        @Mock mockColorSpace: ColorSpace,
        @Mock mockOtherColorSpace: ColorSpace,
        forge: Forge
    ) {
        // Given
        useFakeResourceCache()
        when (forge.anInt(min = 0, max = 3)) {
            0 -> whenever(mockBitmap.hasAlpha()).thenReturn(true, false)
            1 -> whenever(mockBitmap.isPremultiplied).thenReturn(true, false)
            else -> {
                whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
                whenever(mockBitmap.colorSpace).thenReturn(mockColorSpace, mockOtherColorSpace)
            }
        }

        // When
        resolveBitmap(mockSerializerCallback)
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockBitmap, times(2)).copy(Bitmap.Config.ARGB_8888, false)
    }

    @Test
    fun `M fail without throwing W resolveResourceIdFromBitmap() { recycled while its key is made }`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastO).thenReturn(true)
        whenever(mockBitmap.colorSpace).thenThrow(IllegalStateException())
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenThrow(IllegalStateException())

        // When
        assertDoesNotThrow { resolveBitmap(mockSerializerCallback) }

        // Then
        verify(mockSerializerCallback).onFailure()
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
        @IntForgery(min = 1, max = 10_000) fakeWidth: Int,
        @IntForgery(min = 1, max = 10_000) fakeHeight: Int
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
    fun `M unregister the caches W unregisterCallbacks()`() {
        // When
        testedResourceResolver.unregisterCallbacks()

        // Then
        verify(mockApplicationContext).unregisterComponentCallbacks(mockAlpha8ResourceCache)
        verify(mockBitmapCachesManager).unregisterCallbacks(mockApplicationContext)
    }

    @Test
    fun `M register the caches W registerCallbacks()`() {
        // When
        testedResourceResolver.registerCallbacks()

        // Then
        verify(mockApplicationContext).registerComponentCallbacks(mockAlpha8ResourceCache)
        verify(mockBitmapCachesManager).registerCallbacks(mockApplicationContext)
    }

    @Test
    fun `M fail the capture W resolveResourceIdFromBitmap() { task rejected }`() {
        // Given
        val fakeRejection = RejectedExecutionException()
        whenever(mockExecutorService.execute(any())).thenThrow(fakeRejection)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockSerializerCallback).onFailure()
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule resolveResourceId task on the executor",
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
    fun `M fail the capture and propagate W resolveResourceIdFromBitmap() { copy throws an error }`() {
        // Given
        val fakeError = OutOfMemoryError()
        whenever(mockBitmap.copy(Bitmap.Config.ARGB_8888, false)).thenThrow(fakeError)

        // When
        val actualError = assertThrows<OutOfMemoryError> { resolveBitmap(mockSerializerCallback) }

        // Then
        assertThat(actualError).isSameAs(fakeError)
        verify(mockSerializerCallback).onFailure()
        verifyNoInteractions(mockExecutorService)
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
    fun `M copy as ALPHA_8, convert the copy and release it W resolveResourceIdFromBitmap() { ALPHA_8 bitmap }`() {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        useAlpha8Copy(mockBitmap)
        whenever(mockAlpha8BitmapConverter.convertAlpha8BitmapToArgb8888(mockAlpha8Copy)).thenReturn(mockCopiedBitmap)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockAlpha8BitmapConverter).convertAlpha8BitmapToArgb8888(mockAlpha8Copy)
        verify(mockAlpha8Copy).recycle()
        verify(mockBitmap, never()).recycle()
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M reserve the copy W resolveResourceIdFromBitmap() { ALPHA_8 bitmap }`(
        @IntForgery(min = 1, max = 10_000) fakeWidth: Int,
        @IntForgery(min = 1, max = 10_000) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        whenever(mockBitmap.width).thenReturn(fakeWidth)
        whenever(mockBitmap.height).thenReturn(fakeHeight)
        testedResourceResolver = createResourceResolver(pendingImageRequests = mockPendingImageRequests)

        // When
        resolveBitmap(mockSerializerCallback)

        // Then
        verify(mockPendingImageRequests)
            .start(any(), eq(fakeWidth.toLong() * fakeHeight), eq(mockSerializerCallback))
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
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        useAlpha8Copy(mockAlpha8Bitmap)
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
        @Mock mockConvertedBitmap: Bitmap,
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        useAlpha8Copy(mockAlpha8Bitmap)
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
        @Mock mockCacheKey: Alpha8CacheKey
    ) {
        // Given
        whenever(mockAlpha8Bitmap.config).thenReturn(Bitmap.Config.ALPHA_8)
        useAlpha8Copy(mockAlpha8Bitmap)
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
