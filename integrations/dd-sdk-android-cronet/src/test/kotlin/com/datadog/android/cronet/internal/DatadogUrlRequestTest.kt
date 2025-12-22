/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogUrlRequestTest {

    @Mock
    lateinit var mockDelegate: UrlRequest

    @Mock
    lateinit var mockRumResourceInstrumentation: RumResourceInstrumentation

    @Forgery
    lateinit var fakeRequestInfo: HttpRequestInfo

    lateinit var testedRequest: DatadogUrlRequest

    @BeforeEach
    fun setup() {
        testedRequest = DatadogUrlRequest(
            info = fakeRequestInfo,
            delegate = mockDelegate,
            rumResourceInstrumentation = mockRumResourceInstrumentation
        )
    }

    @Test
    fun `M delegate to request W start()`() {
        // When
        testedRequest.start()

        // Then
        verify(mockDelegate).start()
    }

    @Test
    fun `M delegate to request W followRedirect()`() {
        // When
        testedRequest.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
    }

    @Test
    fun `M delegate to request W read()`() {
        // Given
        val mockBuffer = mock<ByteBuffer>()

        // When
        testedRequest.read(mockBuffer)

        // Then
        verify(mockDelegate).read(mockBuffer)
    }

    @Test
    fun `M delegate to request W cancel()`() {
        // When
        testedRequest.cancel()

        // Then
        verify(mockDelegate).cancel()
    }

    @Test
    fun `M delegate to request W isDone`(@BoolForgery fakeDone: Boolean) {
        // Given
        whenever(mockDelegate.isDone).thenReturn(fakeDone)

        // When
        val result = testedRequest.isDone

        // Then
        verify(mockDelegate).isDone
        assertThat(result).isEqualTo(fakeDone)
    }

    @Test
    fun `M delegate to request W getStatus()`() {
        // Given
        val mockListener = mock<UrlRequest.StatusListener>()

        // When
        testedRequest.getStatus(mockListener)

        // Then
        verify(mockDelegate).getStatus(mockListener)
    }

    @Test
    fun `M startResource W start()`() {
        // When
        testedRequest.start()

        // Then
        verify(mockRumResourceInstrumentation).startResource(fakeRequestInfo)
    }

    @Test
    fun `M sendWaitForResourceTimingEvent W start()`() {
        // When
        testedRequest.start()

        // Then
        mockRumResourceInstrumentation.sendWaitForResourceTimingEvent(fakeRequestInfo)
    }
}
