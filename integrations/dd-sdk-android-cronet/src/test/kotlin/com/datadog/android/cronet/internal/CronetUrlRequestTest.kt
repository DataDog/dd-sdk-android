/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation.Companion.buildResourceId
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.nio.ByteBuffer
import java.util.UUID
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

    lateinit var fakeRequestContext: CronetRequestContext

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

        fakeRequestContext = CronetRequestContext(
            url = forge.anUrlString(),
            engine = mockEngine,
            requestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(forge.anElementFrom(HttpSpec.Method.values())) }

        testedRequest = CronetUrlRequest(
            initialRequestInfo = fakeRequestContext.asCronetRequestInfo(),
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
    fun `M tag the request info with a UUID W start()`() {
        // When
        testedRequest.start()

        // Then
        assertThat(captureStartedRequestInfos().single().tag(UUID::class.java)).isNotNull()
    }

    @Test
    fun `M resolve the same ResourceId on start and on finish W start()`() {
        // When
        testedRequest.start()

        // Then
        // The RUM start/wait events are built with generateUuid = true from the info handed to the
        // callback, while the timing/stop events are built with generateUuid = false from the info
        // annotated on the delegate request. All of them must resolve to a single ResourceId.
        val startedId = buildResourceId(captureStartedRequestInfos().single(), generateUuid = true)
        val annotatedId = buildResourceId(captureAnnotatedRequestInfos().single(), generateUuid = false)
        // Asserting on the uuid rather than on ResourceId equality: ResourceId#equals falls back to
        // comparing keys as soon as one of the uuids is null, which would hide a missing uuid.
        assertThat(annotatedId.uuid).isNotNull()
        assertThat(startedId.uuid).isEqualTo(annotatedId.uuid)
        assertThat(startedId.key).isEqualTo(annotatedId.key)
    }

    @Test
    fun `M reuse the application UUID W start() { UUID annotation already set }`() {
        // Given
        val fakeApplicationUuid = UUID.randomUUID()
        fakeRequestContext.addRequestAnnotation(fakeApplicationUuid)
        testedRequest = CronetUrlRequest(
            initialRequestInfo = fakeRequestContext.asCronetRequestInfo(),
            requestCallback = mockCallback
        )

        // When
        testedRequest.start()

        // Then
        // Reused rather than replaced: the annotations belong to the application, which reads them
        // back from RequestFinishedInfo.
        assertThat(captureStartedRequestInfos().single().tag(UUID::class.java))
            .isEqualTo(fakeApplicationUuid)
        verify(mockDelegateBuilder).addRequestAnnotation(fakeApplicationUuid)
    }

    @Test
    fun `M resolve distinct ResourceIds W start() { concurrent requests to the same URL }`() {
        // Given
        val otherRequest = CronetUrlRequest(
            initialRequestInfo = fakeRequestContext.asCronetRequestInfo(),
            requestCallback = mockCallback
        )

        // When
        testedRequest.start()
        otherRequest.start()

        // Then
        val (firstInfo, secondInfo) = captureStartedRequestInfos(times = 2)
        assertThat(buildResourceId(firstInfo, generateUuid = false))
            .isNotEqualTo(buildResourceId(secondInfo, generateUuid = false))
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

    private fun captureStartedRequestInfos(times: Int = 1): List<CronetHttpRequestInfo> {
        val captor = argumentCaptor<CronetHttpRequestInfo>()
        verify(mockCallback, times(times)).onRequestStarted(captor.capture())
        return captor.allValues
    }

    private fun captureAnnotatedRequestInfos(): List<CronetHttpRequestInfo> {
        val captor = argumentCaptor<Any>()
        verify(mockDelegateBuilder, atLeastOnce()).addRequestAnnotation(captor.capture())
        return captor.allValues.filterIsInstance<CronetHttpRequestInfo>()
    }
}
