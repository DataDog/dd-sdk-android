/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.Bitmap
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.AsyncImageProcessingCallback
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer.Companion.BITMAP_SIZE_LIMIT_BYTES
import com.datadog.android.sessionreplay.internal.utils.Base64Utils
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class Base64SerializerTest {
    lateinit var testedBase64Serializer: Base64Serializer

    @Mock
    lateinit var mockDrawableUtils: DrawableUtils

    @Mock
    lateinit var mockWebPImageCompression: ImageCompression

    @Mock
    lateinit var mockBase64Utils: Base64Utils

    @Mock
    lateinit var mockCallback: AsyncImageProcessingCallback

    @Forgery
    lateinit var fakeImageWireframe: MobileSegment.Wireframe.ImageWireframe

    lateinit var fakeBase64String: String
    lateinit var fakeBitmap: Bitmap

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @BeforeEach
    fun setup(forge: Forge) {
        fakeBase64String = forge.aString()
        fakeBitmap = mock()

        val fakeByteOutputStream: ByteArrayOutputStream = mock()
        whenever(mockWebPImageCompression.compressBitmapToStream(any())).thenReturn(fakeByteOutputStream)
        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn(fakeBase64String)

        fakeImageWireframe.base64 = ""
        fakeImageWireframe.isEmpty = true

        whenever(mockBase64Utils.serializeToBase64String(any())).thenReturn(fakeBase64String)

        whenever(mockExecutorService.submit(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        testedBase64Serializer = Base64Serializer.Builder().build(
            threadPoolExecutor = mockExecutorService,
            drawableUtils = mockDrawableUtils,
            base64Utils = mockBase64Utils,
            webPImageCompression = mockWebPImageCompression
        )

        testedBase64Serializer.registerAsyncLoadingCallback(mockCallback)
    }

    @Test
    fun `M callback with startProcessingImage W handleBitmap()`() {
        // When
        testedBase64Serializer.handleBitmap(
            displayMetrics = mock(),
            drawable = mock(),
            imageWireframe = mock()
        )

        // Then
        verify(mockCallback).startProcessingImage()
    }

    @Test
    fun `M callback with finishProcessingImage W handleBitmap() { failed to create bmp }`() {
        // Given
        whenever(mockDrawableUtils.createBitmapFromDrawable(any(), any())).thenReturn(null)

        // When
        testedBase64Serializer.handleBitmap(
            displayMetrics = mock(),
            drawable = mock(),
            imageWireframe = mock()
        )

        // Then
        verify(mockCallback).finishProcessingImage()
    }

    @Test
    fun `M callback with finishProcessingImage W handleBitmap { created bmp async }`() {
        // Given
        whenever(mockDrawableUtils.createBitmapFromDrawable(any(), any())).thenReturn(fakeBitmap)

        // When
        testedBase64Serializer.handleBitmap(
            displayMetrics = mock(),
            drawable = mock(),
            imageWireframe = fakeImageWireframe
        )

        // Then
        assertThat(fakeImageWireframe.base64).isEqualTo(fakeBase64String)
        assertThat(fakeImageWireframe.isEmpty).isFalse()
        verify(mockCallback).finishProcessingImage()
    }

    @Test
    fun `M return empty base64 string W image over size limit`() {
        // Given

        whenever(mockDrawableUtils.createBitmapFromDrawable(any(), any())).thenReturn(fakeBitmap)
        val mockByteArrayOutputStream: ByteArrayOutputStream = mock()
        whenever(mockByteArrayOutputStream.size()).thenReturn(BITMAP_SIZE_LIMIT_BYTES + 1)
        whenever(mockWebPImageCompression.compressBitmapToStream(any())).thenReturn(mockByteArrayOutputStream)

        // When
        testedBase64Serializer.handleBitmap(
            displayMetrics = mock(),
            drawable = mock(),
            imageWireframe = fakeImageWireframe
        )

        // Then
        assertThat(fakeImageWireframe.base64).isEmpty()
        assertThat(fakeImageWireframe.isEmpty).isTrue()
        verify(mockCallback).finishProcessingImage()
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
}
