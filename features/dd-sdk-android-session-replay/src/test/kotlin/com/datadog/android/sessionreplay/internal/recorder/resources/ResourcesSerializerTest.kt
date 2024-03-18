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
import com.datadog.android.sessionreplay.internal.recorder.resources.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourcesSerializerTest {
    private lateinit var testedResourcesSerializer: ResourcesSerializer

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
    lateinit var mockSerializerCallback: ResourcesSerializerCallback

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
    lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockStateListDrawable: StateListDrawable

    @Mock
    lateinit var mockBitmapPool: BitmapPool

    @Mock
    lateinit var mockBitmapDrawable: BitmapDrawable

    @Mock
    lateinit var mockResources: Resources

    @IntForgery(min = 1)
    var fakeBitmapWidth: Int = 0

    @IntForgery(min = 1)
    var fakeBitmapHeight: Int = 0

    @Forgery
    lateinit var fakeApplicationid: UUID

    @Forgery
    lateinit var fakeImageWireframe: MobileSegment.Wireframe.ImageWireframe

    private lateinit var fakeImageCompressionByteArray: ByteArray

    @BeforeEach
    fun setup(forge: Forge) {
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeImageWireframe.isEmpty = true

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                resources = any(),
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull(),
                bitmapCreationCallback = any()
            )
        ).then {
            (it.arguments[7] as ResourcesSerializer.BitmapCreationCallback).onReady(mockBitmap)
        }

        whenever(mockExecutorService.execute(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)

        testedResourcesSerializer = createResourcesSerializer()
    }

    @Test
    fun `M get data from cache and update wireframe W handleBitmap() { cache hit with resourceId }`(
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        whenever(mockResourcesLRUCache.get(mockDrawable)).thenReturn(fakeResourceIdByteArray)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableUtils)
        assertThat(fakeImageWireframe.isEmpty).isFalse()
        assertThat(fakeImageWireframe.base64).isEqualTo(null)
        assertThat(fakeImageWireframe.resourceId).isEqualTo(fakeResourceId)
        verify(mockSerializerCallback).onReady()
    }

    @Test
    fun `M register cache only once for callbacks W handleBitmap() { multiple calls }`() {
        // When
        repeat(5) {
            testedResourcesSerializer.handleBitmap(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = mockDrawable.intrinsicWidth,
                drawableHeight = mockDrawable.intrinsicHeight,
                imageWireframe = fakeImageWireframe,
                resourcesSerializerCallback = mockSerializerCallback
            )
        }

        // Then
        verify(mockApplicationContext, times(1)).registerComponentCallbacks(mockResourcesLRUCache)
    }

    @Test
    fun `M retry image creation only once W handleBitmap() { image was recycled while working on it }`() {
        // Given
        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(2)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
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
    fun `M send onReady W handleBitmap { failed to get image data }`() {
        // Given
        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onReady()
    }

    @Test
    fun `M log error W handleBitmap() { cache does not subclass ComponentCallbacks2 }`() {
        // Given
        val fakeBase64CacheInstance = FakeNonComponentsCallbackCache()
        testedResourcesSerializer = ResourcesSerializer.Builder(
            logger = mockLogger,
            threadPoolExecutor = mockExecutorService,
            bitmapPool = mockBitmapPool,
            resourcesLRUCache = fakeBase64CacheInstance,
            drawableUtils = mockDrawableUtils,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString(),
            webPImageCompression = mockWebPImageCompression
        ).build()

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        val captor = argumentCaptor<() -> String>()
        verify(mockLogger).log(
            level = any(),
            target = any(),
            captor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(captor.firstValue.invoke()).isEqualTo(
            DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
        )
    }

    @Test
    fun `M register BitmapPool only once for callbacks W handleBitmap() { multiple calls }`() {
        // When
        repeat(5) {
            testedResourcesSerializer.handleBitmap(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = mockDrawable.intrinsicWidth,
                drawableHeight = mockDrawable.intrinsicHeight,
                imageWireframe = fakeImageWireframe,
                resourcesSerializerCallback = mockSerializerCallback
            )
        }

        // Then
        verify(mockApplicationContext, times(1)).registerComponentCallbacks(mockBitmapPool)
    }

    @Test
    fun `M calculate resourceId W handleBitmap() { cache miss }`() {
        // Given
        whenever(mockResourcesLRUCache.get(mockDrawable)).thenReturn(null)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
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
    fun `M use the same ThreadPoolExecutor W build()`() {
        // When
        val instance1 = ResourcesSerializer.Builder(
            bitmapPool = mockBitmapPool,
            resourcesLRUCache = mockResourcesLRUCache,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString()
        ).build()
        val instance2 = ResourcesSerializer.Builder(
            bitmapPool = mockBitmapPool,
            resourcesLRUCache = mockResourcesLRUCache,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString()
        ).build()

        // Then
        assertThat(instance1.getThreadPoolExecutor()).isEqualTo(
            instance2.getThreadPoolExecutor()
        )
    }

    @Test
    fun `M not try to cache resourceId W handleBitmap() { and did not get resourceId }`() {
        // Given
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(null)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockStateListDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockResourcesLRUCache, times(0)).put(any(), any())
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W handleBitmap() { no bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
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
    fun `M not use bitmap from bitmapDrawable W handleBitmap() { bitmap was recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
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
    fun `M use scaled bitmap from bitmapDrawable W handleBitmap() { has bitmap }`() {
        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createScaledBitmap(
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `M draw bitmap W handleBitmap() { bitmapDrawable where bitmap has no width }`() {
        // Given
        whenever(mockBitmap.width).thenReturn(0)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, never()).createScaledBitmap(
            any(),
            anyOrNull()
        )
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
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
    fun `M draw bitmap W handleBitmap() { bitmapDrawable where bitmap has no height }`() {
        // Given
        whenever(mockBitmap.height).thenReturn(0)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, never()).createScaledBitmap(
            any(),
            anyOrNull()
        )
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
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
    fun `M not cache bitmap W handleBitmap() { BitmapDrawable with bitmap not resized }`() {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockBitmap)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapPool, never()).put(any())
    }

    @Test
    fun `M cache bitmap W handleBitmap() { BitmapDrawable with bitmap was resized }`(
        @Mock mockResizedBitmap: Bitmap
    ) {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockResizedBitmap)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapPool).put(any())
    }

    @Test
    fun `M cache bitmap W handleBitmap() { from BitmapDrawable with null bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapPool, times(1)).put(any())
    }

    @Test
    fun `M cache bitmap W handleBitmap() { not a BitmapDrawable }`() {
        // Given
        val mockLayerDrawable = mock<LayerDrawable>()

        // When
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockLayerDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapPool, times(1)).put(any())
    }

    @Test
    fun `M return correct callback W handleBitmap() { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: ResourcesSerializerCallback,
        @Mock mockSecondCallback: ResourcesSerializerCallback
    ) {
        // Given
        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            testedResourcesSerializer.handleBitmap(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                imageWireframe = fakeImageWireframe,
                resourcesSerializerCallback = mockFirstCallback
            )
            Thread.sleep(1500)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            testedResourcesSerializer.handleBitmap(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                imageWireframe = fakeImageWireframe,
                resourcesSerializerCallback = mockSecondCallback
            )
            Thread.sleep(500)
            countDownLatch.countDown()
        }

        // When
        thread1.start()
        thread2.start()

        // Then
        countDownLatch.await()
        verify(mockFirstCallback).onReady()
        verify(mockSecondCallback).onReady()
    }

    @Test
    fun `M failover to bitmap creation W handleBitmap { bitmapDrawable returned empty bytearray }`(
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
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        val drawableCaptor = argumentCaptor<Drawable>()
        val intCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()
        val configCaptor = argumentCaptor<Bitmap.Config>()
        val bitmapCreationCallbackCaptor = argumentCaptor<ResourcesSerializer.BitmapCreationCallback>()
        val resourcesCaptor = argumentCaptor<Resources>()

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = resourcesCaptor.capture(),
            drawable = drawableCaptor.capture(),
            drawableWidth = intCaptor.capture(),
            drawableHeight = intCaptor.capture(),
            displayMetrics = displayMetricsCaptor.capture(),
            requestedSizeInBytes = intCaptor.capture(),
            config = configCaptor.capture(),
            bitmapCreationCallback = bitmapCreationCallbackCaptor.capture()
        )

        assertThat(drawableCaptor.firstValue).isEqualTo(mockBitmapDrawable)
        assertThat(intCaptor.firstValue).isEqualTo(fakeBitmapWidth)
        assertThat(intCaptor.secondValue).isEqualTo(fakeBitmapHeight)
        assertThat(displayMetricsCaptor.firstValue).isEqualTo(mockDisplayMetrics)
        assertThat(configCaptor.firstValue).isEqualTo(Bitmap.Config.ARGB_8888)
        assertThat(resourcesCaptor.firstValue).isEqualTo(mockResources)
    }

    @Test
    fun `M only send resource once W handleBitmap { call twice on the same image }`(
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
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

        verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
            identifier = eq(fakeResourceId),
            applicationId = eq(fakeApplicationid.toString()),
            resourceData = eq(fakeByteArray)
        )

        // second time
        testedResourcesSerializer.handleBitmap(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            imageWireframe = fakeImageWireframe,
            resourcesSerializerCallback = mockSerializerCallback
        )

            verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
                identifier = eq(fakeResourceId),
                applicationId = eq(fakeApplicationid.toString()),
                resourceData = eq(fakeByteArray)
            )
        }

    private fun createResourcesSerializer(): ResourcesSerializer {
        val builder = ResourcesSerializer.Builder(
            logger = mockLogger,
            threadPoolExecutor = mockExecutorService,
            bitmapPool = mockBitmapPool,
            resourcesLRUCache = mockResourcesLRUCache,
            drawableUtils = mockDrawableUtils,
            webPImageCompression = mockWebPImageCompression,
            md5HashGenerator = mockMD5HashGenerator,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString()
        )
        return builder.build()
    }

    // this is in order to test having a class that implements
    // Cache, but does NOT implement ComponentCallbacks2
    private class FakeNonComponentsCallbackCache : Cache<Drawable, ByteArray> {

        override fun size(): Int = 0

        override fun clear() {}
    }
}
