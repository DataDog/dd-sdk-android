/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import android.widget.ImageView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.recorder.base64.Cache.Companion.DOES_NOT_IMPLEMENT_COMPONENTCALLBACKS
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
    lateinit var mockAsyncImageProcessingCallback: AsyncImageProcessingCallback

    private lateinit var fakeBase64String: String

    private lateinit var fakeByteArray: ByteArray

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockBase64LRUCache: Base64LRUCache

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockImageView: ImageView

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

    @FloatForgery(min = 0f, max = 1f)
    var mockDensity: Float = 0f

    @Forgery
    lateinit var fakeImageWireframe: MobileSegment.Wireframe.ImageWireframe

    @BeforeEach
    fun setup(forge: Forge) {
        fakeBase64String = forge.aString()
        fakeByteArray = forge.aString().toByteArray()

        fakeImageWireframe.base64 = ""
        fakeImageWireframe.isEmpty = true

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeByteArray)
        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn(fakeBase64String)

        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull()
            )
        ).thenReturn(mockBitmap)

        whenever(mockExecutorService.submit(any())).then {
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
    fun `M callback with startProcessingImage W handleBitmap()`() {
        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockAsyncImageProcessingCallback).startProcessingImage()
    }

    @Test
    fun `M callback with finishProcessingImage W handleBitmap() { failed to create bmp }`() {
        // Given
        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull()
            )
        ).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockAsyncImageProcessingCallback).finishProcessingImage()
    }

    @Test
    fun `M callback with finishProcessingImage W handleBitmap() { created bmp async }`() {
        // Given
        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull()
            )
        ).thenReturn(mockBitmap)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        assertThat(fakeImageWireframe.base64).isEqualTo(fakeBase64String)
        assertThat(fakeImageWireframe.isEmpty).isFalse
        verify(mockAsyncImageProcessingCallback).finishProcessingImage()
    }

    @Test
    fun `M get base64 from cache W handleBitmap() { cache hit }`(forge: Forge) {
        // Given
        val fakeBase64String = forge.anAsciiString()
        whenever(mockBase64LRUCache.get(mockDrawable)).thenReturn(fakeBase64String)

        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull()
            )
        )
            .thenReturn(mockBitmap)
        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeByteArray)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verifyNoInteractions(mockDrawableUtils)
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
                asyncImageProcessingCallback = mockAsyncImageProcessingCallback
            )
        }

        // Then
        verify(mockApplicationContext, times(1)).registerComponentCallbacks(mockBase64LRUCache)
    }

    @Test
    fun `M log error W handleBitmap() { base64Lru does not subclass ComponentCallbacks2 }`() {
        // Given
        val fakeBase64CacheInstance = FakeBase64LruCache()
        testedBase64Serializer = Base64Serializer.Builder(
            logger = mockLogger,
            threadPoolExecutor = mockExecutorService,
            bitmapPool = mockBitmapPool,
            base64LRUCache = fakeBase64CacheInstance,
            drawableUtils = mockDrawableUtils,
            base64Utils = mockBase64Utils,
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
                asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull()
        )
    }

    @Test
    fun `M use the same ThreadPoolExecutor W build()`() {
        // When
        val instance1 = Base64Serializer.Builder().build()
        val instance2 = Base64Serializer.Builder().build()

        // Then
        assertThat(instance1.getThreadPoolExecutor()).isEqualTo(
            instance2.getThreadPoolExecutor()
        )
    }

    @Test
    fun `M cache base64 string W handleBitmap() { and got base64 string }`() {
        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockStateListDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockBase64LRUCache, times(1)).put(mockStateListDrawable, fakeBase64String)
    }

    @Test
    fun `M not try to cache base64 W handleBitmap() { and did not get base64 }`() {
        // Given
        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn("")

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockStateListDrawable,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            imageWireframe = fakeImageWireframe,
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull()
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull()
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            config = anyOrNull()
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            config = anyOrNull()
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockBitmapPool).put(any())
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
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
            asyncImageProcessingCallback = mockAsyncImageProcessingCallback
        )

        // Then
        verify(mockBitmapPool, times(1)).put(any())
    }

    @Test
    fun `M call drawableUtils W getDrawableScaledDimensions()`() {
        // When
        testedBase64Serializer.getDrawableScaledDimensions(
            view = mockImageView,
            drawable = mockDrawable,
            density = mockDensity
        )

        // Then
        verify(mockDrawableUtils).getDrawableScaledDimensions(
            view = mockImageView,
            drawable = mockDrawable,
            density = mockDensity
        )
    }

    @Test
    fun `M return correct callback W handleBitmap() { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: AsyncImageProcessingCallback,
        @Mock mockSecondCallback: AsyncImageProcessingCallback
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
                asyncImageProcessingCallback = mockFirstCallback
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
                asyncImageProcessingCallback = mockSecondCallback
            )
            Thread.sleep(500)
            countDownLatch.countDown()
        }

        // When
        thread1.start()
        thread2.start()

        // Then
        countDownLatch.await()
        verify(mockFirstCallback).finishProcessingImage()
        verify(mockSecondCallback).finishProcessingImage()
    }

    private fun createBase64Serializer(): Base64Serializer {
        val builder = Base64Serializer.Builder(
            logger = mockLogger,
            threadPoolExecutor = mockExecutorService,
            bitmapPool = mockBitmapPool,
            base64LRUCache = mockBase64LRUCache,
            drawableUtils = mockDrawableUtils,
            base64Utils = mockBase64Utils,
            webPImageCompression = mockWebPImageCompression
        )
        return builder.build()
    }

    // this is in order to test having a class that implements
    // Cache, but does NOT implement ComponentCallbacks2
    private class FakeBase64LruCache : Cache<Drawable, String> {
        override fun put(value: String) {
            super.put(value)
        }

        override fun put(element: Drawable, value: String) {
            super.put(element, value)
        }

        override fun get(element: Drawable): String? {
            return super.get(element)
        }

        override fun size(): Int {
            return 0
        }

        override fun clear() {}
    }
}
