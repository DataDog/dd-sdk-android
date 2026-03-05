/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.tests.elmyr.anUrlString
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetUrlRequestTest {

    @Mock
    lateinit var mockBuiltRequest: UrlRequest

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: CronetRequestCallback

    @Mock
    lateinit var mockExecutor: Executor

    @Mock
    lateinit var mockDelegateBuilder: UrlRequest.Builder

    @Mock
    lateinit var mockRequestInfoBuilder: HttpRequestInfoBuilder

    @Mock
    lateinit var mockRequestTracingState: RequestTracingState

    lateinit var testedRequest: CronetUrlRequest

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockEngine.apmNetworkInstrumentation) doReturn null
        whenever(mockEngine.newDelegateUrlRequestBuilder(any(), any(), any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.setHttpMethod(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.addHeader(any(), any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.addRequestAnnotation(any())) doReturn mockDelegateBuilder
        whenever(mockDelegateBuilder.build()) doReturn mockBuiltRequest
        whenever(mockRequestTracingState.requestInfoBuilder) doReturn mockRequestInfoBuilder
        whenever(mockCallback.onRequestStarted(any())) doReturn mockRequestTracingState

        val requestContext = CronetRequestContext(
            url = forge.anUrlString(),
            engine = mockEngine,
            requestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(forge.anElementFrom(HttpSpec.Method.values())) }

        testedRequest = CronetUrlRequest(
            initialRequestInfo = requestContext.asCronetRequestInfo(),
            requestCallback = mockCallback
        )
    }

    @Test
    fun `M delegate to request W start()`() {
        // When
        testedRequest.start()

        // Then
        verify(mockBuiltRequest).start()
    }

    @Test
    fun `M delegate to request W followRedirect()`() {
        // Given
        testedRequest.start()

        // When
        testedRequest.followRedirect()

        // Then
        verify(mockBuiltRequest).followRedirect()
    }

    @Test
    fun `M delegate to request W read()`() {
        // Given
        testedRequest.start()
        val mockBuffer = mock<ByteBuffer>()

        // When
        testedRequest.read(mockBuffer)

        // Then
        verify(mockBuiltRequest).read(mockBuffer)
    }

    @Test
    fun `M delegate to request W cancel()`() {
        // Given
        testedRequest.start()

        // When
        testedRequest.cancel()

        // Then
        verify(mockBuiltRequest).cancel()
    }

    @Test
    fun `M delegate to request W isDone`(@BoolForgery fakeDone: Boolean) {
        // Given
        testedRequest.start()
        whenever(mockBuiltRequest.isDone).thenReturn(fakeDone)

        // When
        val result = testedRequest.isDone

        // Then
        verify(mockBuiltRequest).isDone
        assertThat(result).isEqualTo(fakeDone)
    }

    @Test
    fun `M delegate to request W getStatus()`() {
        // Given
        testedRequest.start()
        val mockListener = mock<UrlRequest.StatusListener>()

        // When
        testedRequest.getStatus(mockListener)

        // Then
        verify(mockBuiltRequest).getStatus(mockListener)
    }

    @Test
    fun `M call onRequestStarted W start()`() {
        // When
        testedRequest.start()

        // Then
        verify(mockCallback).onRequestStarted(any<CronetHttpRequestInfo>())
    }

    @Test
    fun `M do nothing W cancel() { before start }`() {
        // When
        testedRequest.cancel()

        // Then
        verifyNoInteractions(mockBuiltRequest)
    }

    @Test
    fun `M do nothing W followRedirect() { before start }`() {
        // When
        testedRequest.followRedirect()

        // Then
        verifyNoInteractions(mockBuiltRequest)
    }

    @Test
    fun `M do nothing W read() { before start }`() {
        // Given
        val mockBuffer = mock<ByteBuffer>()

        // When
        testedRequest.read(mockBuffer)

        // Then
        verifyNoInteractions(mockBuiltRequest)
    }

    @Test
    fun `M do nothing W getStatus() { before start }`() {
        // Given
        val mockListener = mock<UrlRequest.StatusListener>()

        // When
        testedRequest.getStatus(mockListener)

        // Then
        verifyNoInteractions(mockBuiltRequest)
    }

    @Test
    fun `M return false W isDone { before start }`() {
        // When
        val result = testedRequest.isDone

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(mockBuiltRequest)
    }
}
