/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.coil

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import coil.request.ImageRequest
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogCoilRequestListenerTest {

    lateinit var underTest: DatadogCoilRequestListener

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Forgery
    lateinit var fakeException: Throwable

    lateinit var mockRequest: ImageRequest

    @BeforeEach
    fun `set up`() {
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockRumMonitor, mockSdkCore)
        }
        underTest = DatadogCoilRequestListener(mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    // region Unit Tests

    @Test
    fun `M send RUM error event W raw string path Request fails`(forge: Forge) {
        // GIVEN
        val fakePath = forge.aStringMatching("http://[a-z].[png|jpeg|gif]")
        mockRequest = mockImageRequest(fakePath)

        // WHEN
        underTest.onError(mockRequest, fakeException)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogCoilRequestListener.REQUEST_ERROR_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        Assertions.assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogCoilRequestListener.REQUEST_PATH_TAG,
            fakePath
        )
    }

    @Test
    fun `M send RUM error event W Uri Request fails`(forge: Forge) {
        // GIVEN
        val fakePath = forgeImagePath(forge)
        val mockUri: Uri = mock {
            whenever(it.path).thenReturn(fakePath)
        }
        mockRequest = mockImageRequest(mockUri)

        // WHEN
        underTest.onError(mockRequest, fakeException)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogCoilRequestListener.REQUEST_ERROR_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        Assertions.assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogCoilRequestListener.REQUEST_PATH_TAG,
            fakePath
        )
    }

    @Test
    fun `M send RUM error event W HttpUrl Request fails`(forge: Forge) {
        // GIVEN
        val fakeHttpUrl = forgeImagePath(forge).toHttpUrl()
        mockRequest = mockImageRequest(fakeHttpUrl)

        // WHEN
        underTest.onError(mockRequest, fakeException)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogCoilRequestListener.REQUEST_ERROR_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        Assertions.assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogCoilRequestListener.REQUEST_PATH_TAG,
            fakeHttpUrl.toUrl().toString()
        )
    }

    @Test
    fun `M send RUM error event W File Request fails`(forge: Forge) {
        // GIVEN
        val fakeFile = File(forge.aStringMatching("[a-z]+/[a-z]+\\.(png|jpeg|gif)"))
        mockRequest = mockImageRequest(fakeFile)

        // WHEN
        underTest.onError(mockRequest, fakeException)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogCoilRequestListener.REQUEST_ERROR_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        Assertions.assertThat(argumentCaptor.firstValue).containsEntry(
            DatadogCoilRequestListener.REQUEST_PATH_TAG,
            fakeFile.path
        )
    }

    @Test
    fun `M send RUM error event W Drawable Request fails`() {
        // GIVEN
        val mockDrawable: Drawable = mock()
        mockRequest = mockImageRequest(mockDrawable)

        // WHEN
        underTest.onError(mockRequest, fakeException)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogCoilRequestListener.REQUEST_ERROR_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        Assertions.assertThat(argumentCaptor.firstValue).isEmpty()
    }

    @Test
    fun `M send RUM error event W Bitmap Request fails`() {
        // GIVEN
        val mockDrawable: Bitmap = mock()
        mockRequest = mockImageRequest(mockDrawable)

        // WHEN
        underTest.onError(mockRequest, fakeException)

        // THEN
        val argumentCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mockRumMonitor).addError(
            eq(DatadogCoilRequestListener.REQUEST_ERROR_MESSAGE),
            eq(RumErrorSource.SOURCE),
            eq(fakeException),
            argumentCaptor.capture()
        )
        Assertions.assertThat(argumentCaptor.firstValue).isEmpty()
    }

    // endregion

    // region Internals

    private fun mockImageRequest(data: Any): ImageRequest {
        return mock {
            whenever(it.data).thenReturn(data)
        }
    }

    private fun forgeImagePath(forge: Forge): String {
        return forge.aStringMatching("(http|https)://[a-z]+\\.(png|jpeg|gif)")
    }
    // endregion
}
