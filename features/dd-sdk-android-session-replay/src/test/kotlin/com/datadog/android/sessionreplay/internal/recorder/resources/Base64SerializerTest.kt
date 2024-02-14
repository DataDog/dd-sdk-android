/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.RESOURCE_ENDPOINT_FEATURE_FLAG
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.resources.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
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
internal class Base64SerializerTest {
    private lateinit var testedBase64Serializer: Base64Serializer

    @Mock
    lateinit var mockDrawableUtils: DrawableUtils

    @Mock
    lateinit var mockWebPImageCompression: ImageCompression

    @Mock
    lateinit var mockBase64Utils: Base64Utils

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockMD5HashGenerator: MD5HashGenerator

    @Mock
    lateinit var mockSerializerCallback: Base64SerializerCallback

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockBase64LRUCache: Base64LRUCache

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

    @IntForgery(min = 1)
    var fakeBitmapWidth: Int = 0

    @IntForgery(min = 1)
    var fakeBitmapHeight: Int = 0

    @Forgery
    lateinit var fakeApplicationid: UUID

    @Forgery
    lateinit var fakeImageWireframe: MobileSegment.Wireframe.ImageWireframe

    private lateinit var fakeBase64Encoding: ByteArray

    private lateinit var fakeCacheData: CacheData

    private lateinit var fakeImageCompressionByteArray: ByteArray

    @BeforeEach
    fun setup(forge: Forge) {
        val fakeResourceId = forge.aNullable { aString() }
        val fakeResourceIdByteArray = fakeResourceId?.toByteArray(Charsets.UTF_8)
        fakeBase64Encoding = forge.aString().toByteArray(Charsets.UTF_8)
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeCacheData = CacheData(fakeBase64Encoding, fakeResourceIdByteArray)

        fakeImageWireframe.base64 = ""
        fakeImageWireframe.isEmpty = true

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)
        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn(
            String(fakeBase64Encoding, Charsets.UTF_8)
        )

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
            (it.arguments[6] as Base64Serializer.BitmapCreationCallback).onReady(mockBitmap)
        }

        whenever(mockExecutorService.execute(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)

        testedBase64Serializer = createBase64Serializer()
    }

    @Test
    fun `M get data from cache and update wireframe W handleBitmap() { cache hit with resourceId }`(forge: Forge) {
        // Given
        fakeCacheData.resourceId = forge.aString().toByteArray(Charsets.UTF_8)
        whenever(mockBase64LRUCache.get(mockDrawable)).thenReturn(fakeCacheData)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableUtils)
        assertThat(fakeImageWireframe.isEmpty).isFalse()
        assertThat(fakeImageWireframe.base64).isEqualTo(
            String(
                fakeBase64Encoding,
                Charsets.UTF_8
            )
        )
        assertThat(fakeImageWireframe.resourceId).isEqualTo(String(fakeCacheData.resourceId!!, Charsets.UTF_8))
        verify(mockSerializerCallback).onReady()
    }

    @Test
    fun `M get data from cache but failover to creation W handleBitmap() { cache hit without resourceId }`() {
        // Given
        fakeCacheData.resourceId = null
        whenever(mockBase64LRUCache.get(mockDrawable)).thenReturn(fakeCacheData)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
        verify(mockSerializerCallback).onReady()
    }

    @Test
    fun `M register cache only once for callbacks W handleBitmap() { multiple calls }`() {
        // When
        repeat(5) {
            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = mockDrawable.intrinsicWidth,
                drawableHeight = mockDrawable.intrinsicHeight,
                imageWireframe = fakeImageWireframe,
                base64SerializerCallback = mockSerializerCallback
            )
        }

        // Then
        verify(mockApplicationContext, times(1)).registerComponentCallbacks(mockBase64LRUCache)
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
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        if (RESOURCE_ENDPOINT_FEATURE_FLAG) {
            verifyNoInteractions(mockBase64Utils)
        } else {
            verify(mockBase64Utils, times(1)).serializeToBase64String(any())
        }
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
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockBase64Utils)
        verify(mockSerializerCallback).onReady()
    }

    @Test
    fun `M log error W handleBitmap() { base64Lru does not subclass ComponentCallbacks2 }`() {
        // Given
        val fakeBase64CacheInstance = FakeNonComponentsCallbackCache()
        testedBase64Serializer = Base64Serializer.Builder(
            logger = mockLogger,
            threadPoolExecutor = mockExecutorService,
            bitmapPool = mockBitmapPool,
            base64LRUCache = fakeBase64CacheInstance,
            drawableUtils = mockDrawableUtils,
            base64Utils = mockBase64Utils,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString(),
            webPImageCompression = mockWebPImageCompression
        ).build()

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = mockDrawable.intrinsicWidth,
                drawableHeight = mockDrawable.intrinsicHeight,
                imageWireframe = fakeImageWireframe,
                base64SerializerCallback = mockSerializerCallback
            )
        }

        // Then
        verify(mockApplicationContext, times(1)).registerComponentCallbacks(mockBitmapPool)
    }

    @Test
    fun `M calculate base64 W handleBitmap() { cache miss }`() {
        // Given
        whenever(mockBase64LRUCache.get(mockDrawable)).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
    fun `M use the same ThreadPoolExecutor W build()`() {
        // When
        val instance1 = Base64Serializer.Builder(
            bitmapPool = mockBitmapPool,
            base64LRUCache = mockBase64LRUCache,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString()
        ).build()
        val instance2 = Base64Serializer.Builder(
            bitmapPool = mockBitmapPool,
            base64LRUCache = mockBase64LRUCache,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString()
        ).build()

        // Then
        assertThat(instance1.getThreadPoolExecutor()).isEqualTo(
            instance2.getThreadPoolExecutor()
        )
    }

    @Test
    fun `M cache base64 string W handleBitmap() { and got only base64 string }`() {
        // Given
        whenever(mockBase64Utils.serializeToBase64String(any()))
            .thenReturn(String(fakeBase64Encoding, Charsets.UTF_8))
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(null)
        val expectedHash = CacheData(fakeBase64Encoding, null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockStateListDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        if (!RESOURCE_ENDPOINT_FEATURE_FLAG) {
            verify(mockBase64LRUCache, times(1)).put(mockStateListDrawable, expectedHash)
        }
    }

    @Test
    fun `M not try to cache base64 W handleBitmap() { and did not get base64 }`() {
        // Given
        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn("")
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockStateListDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBase64LRUCache, times(0)).put(any(), any())
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W handleBitmap() { no bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
    fun `M not use bitmap from bitmapDrawable W handleBitmap() { bitmap was recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
    fun `M use scaled bitmap from bitmapDrawable W handleBitmap() { has bitmap }`() {
        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
    fun `M draw bitmap W handleBitmap() { bitmapDrawable where bitmap has no height }`() {
        // Given
        whenever(mockBitmap.height).thenReturn(0)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
    fun `M not cache bitmap W handleBitmap() { BitmapDrawable with bitmap not resized }`() {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockBitmap)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
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
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        if (!RESOURCE_ENDPOINT_FEATURE_FLAG) {
            verify(mockBitmapPool).put(any())
        }
    }

    @Test
    fun `M cache bitmap W handleBitmap() { from BitmapDrawable with null bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapPool, times(1)).put(any())
    }

    @Test
    fun `M cache bitmap W handleBitmap() { not a BitmapDrawable }`() {
        // Given
        val mockLayerDrawable = mock<LayerDrawable>()

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockLayerDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapPool, times(1)).put(any())
    }

    @Test
    fun `M return correct callback W handleBitmap() { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: Base64SerializerCallback,
        @Mock mockSecondCallback: Base64SerializerCallback
    ) {
        // Given
        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                imageWireframe = fakeImageWireframe,
                base64SerializerCallback = mockFirstCallback
            )
            Thread.sleep(1500)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                imageWireframe = fakeImageWireframe,
                base64SerializerCallback = mockSecondCallback
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

        whenever(mockBase64Utils.serializeToBase64String(fakeImageCompressionByteArray))
            .thenReturn(String(fakeBase64Encoding, Charsets.UTF_8))

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockBitmapDrawable,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            imageWireframe = fakeImageWireframe,
            base64SerializerCallback = mockSerializerCallback
        )

        val drawableCaptor = argumentCaptor<Drawable>()
        val intCaptor = argumentCaptor<Int>()
        val displayMetricsCaptor = argumentCaptor<DisplayMetrics>()
        val configCaptor = argumentCaptor<Bitmap.Config>()
        val bitmapCreationCallbackCaptor = argumentCaptor<Base64Serializer.BitmapCreationCallback>()

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
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
    }

    @Test
    fun `M only send resource once W handleBitmap { call twice on the same image }`(
        @Mock mockCreatedBitmap: Bitmap,
        @StringForgery fakeResourceId: String,
        @StringForgery fakeResource: String
    ) {
        if (RESOURCE_ENDPOINT_FEATURE_FLAG) {
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

            whenever(mockBase64Utils.serializeToBase64String(fakeImageCompressionByteArray))
                .thenReturn(String(fakeBase64Encoding, Charsets.UTF_8))

            // When
            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockBitmapDrawable,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                imageWireframe = fakeImageWireframe,
                base64SerializerCallback = mockSerializerCallback
            )

            // Then

            // second time
            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockBitmapDrawable,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                imageWireframe = fakeImageWireframe,
                base64SerializerCallback = mockSerializerCallback
            )

            verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
                identifier = eq(fakeResourceId),
                applicationId = eq(fakeApplicationid.toString()),
                resourceData = eq(fakeByteArray)
            )
        }
    }

    private fun createBase64Serializer(): Base64Serializer {
        val builder = Base64Serializer.Builder(
            logger = mockLogger,
            threadPoolExecutor = mockExecutorService,
            bitmapPool = mockBitmapPool,
            base64LRUCache = mockBase64LRUCache,
            drawableUtils = mockDrawableUtils,
            base64Utils = mockBase64Utils,
            webPImageCompression = mockWebPImageCompression,
            md5HashGenerator = mockMD5HashGenerator,
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString()
        )
        return builder.build()
    }

    // this is in order to test having a class that implements
    // Cache, but does NOT implement ComponentCallbacks2
    private class FakeNonComponentsCallbackCache : Cache<Drawable, CacheData> {

        override fun size(): Int = 0

        override fun clear() {}
    }
}
