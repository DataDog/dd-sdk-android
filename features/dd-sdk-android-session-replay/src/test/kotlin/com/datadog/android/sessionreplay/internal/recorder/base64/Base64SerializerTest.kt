/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
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
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockCallback: AsyncImageProcessingCallback

    private lateinit var fakeBase64String: String

    private lateinit var fakeByteArray: ByteArray

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockBase64LruCache: Base64LRUCache

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
                applicationContext = any(),
                drawable = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull()
            )
        ).thenReturn(mockBitmap)

        whenever(mockExecutorService.submit(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        testedBase64Serializer = createBase64Serializer()

        testedBase64Serializer.registerAsyncLoadingCallback(mockCallback)
    }

    @Test
    fun `M callback with startProcessingImage W handleBitmap()`() {
        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            imageWireframe = fakeImageWireframe
        )

        // Then
        verify(mockCallback).startProcessingImage()
    }

    @Test
    fun `M callback with finishProcessingImage W handleBitmap() { failed to create bmp }`() {
        // Given
        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                applicationContext = any(),
                drawable = any(),
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
            imageWireframe = fakeImageWireframe
        )

        // Then
        verify(mockCallback).finishProcessingImage()
    }

    @Test
    fun `M callback with finishProcessingImage W handleBitmap() { created bmp async }`() {
        // Given
        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                applicationContext = any(),
                drawable = any(),
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
            imageWireframe = fakeImageWireframe
        )

        // Then
        assertThat(fakeImageWireframe.base64).isEqualTo(fakeBase64String)
        assertThat(fakeImageWireframe.isEmpty).isFalse
        verify(mockCallback).finishProcessingImage()
    }

    @Test
    fun `M get base64 from cache W handleBitmap() { cache hit }`(forge: Forge) {
        // Given
        val fakeBase64String = forge.anAsciiString()
        whenever(mockBase64LruCache.get(mockDrawable)).thenReturn(fakeBase64String)

        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                applicationContext = any(),
                drawable = any(),
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
            imageWireframe = fakeImageWireframe
        )

        // Then
        verifyNoInteractions(mockDrawableUtils)
    }

    @Test
    fun `M register cache only once for callbacks W handleBitmap() { multiple calls and instances }`() {
        // Given
        Base64Serializer.isCacheRegisteredForCallbacks = false
        val secondInstance = createBase64Serializer()

        // When
        repeat(5) {
            secondInstance.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                imageWireframe = fakeImageWireframe
            )

            testedBase64Serializer.handleBitmap(
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                drawable = mockDrawable,
                imageWireframe = fakeImageWireframe
            )
        }

        // Then
        verify(mockApplicationContext, times(1)).registerComponentCallbacks(mockBase64LruCache)
    }

    @Test
    fun `M calculate base64 W handleBitmap() { cache miss }`() {
        // Given
        whenever(mockBase64LruCache.get(mockDrawable)).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockDrawable,
            imageWireframe = fakeImageWireframe
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            applicationContext = any(),
            drawable = any(),
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
            imageWireframe = fakeImageWireframe
        )

        // Then
        verify(mockBase64LruCache, times(1)).put(mockStateListDrawable, fakeBase64String)
    }

    @Test
    fun `M not try to cache base64 W handleBitmap() { and did not get base64 string }`() {
        // Given
        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn("")

        // When
        testedBase64Serializer.handleBitmap(
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            drawable = mockStateListDrawable,
            imageWireframe = fakeImageWireframe
        )

        // Then
        verify(mockBase64LruCache, times(0)).put(any(), any())
    }

    private fun createBase64Serializer() = Base64Serializer.Builder().build(
        threadPoolExecutor = mockExecutorService,
        drawableUtils = mockDrawableUtils,
        base64Utils = mockBase64Utils,
        webPImageCompression = mockWebPImageCompression,
        base64LruCache = mockBase64LruCache,
        bitmapPool = mockBitmapPool
    )
}
