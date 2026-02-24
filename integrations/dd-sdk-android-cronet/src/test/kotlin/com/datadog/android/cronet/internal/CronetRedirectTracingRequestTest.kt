/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.tests.elmyr.URL_FORGERY_PATTERN
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetRedirectTracingRequestTest {
    @Mock
    lateinit var mockDelegate: UrlRequest

    @Mock
    lateinit var mockApmNetworkInstrumentation: ApmNetworkInstrumentation

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: CronetRequestCallback

    @Mock
    lateinit var mockExecutor: Executor

    @StringForgery(regex = URL_FORGERY_PATTERN)
    lateinit var fakeUrl: String

    @Mock
    lateinit var mockNewTraceState: RequestTracingState

    private lateinit var testedRequest: CronetRedirectTracingRequestWrapper

    private val requestTracingState = AtomicReference<RequestTracingState?>(null)

    @BeforeEach
    fun `set up`() {
        testedRequest = CronetRedirectTracingRequestWrapper(
            delegate = mockDelegate,
            newLocationUrl = null,
            redirectStatusCode = null,
            apmNetworkInstrumentation = null,
            previousRequestInfo = null,
            apmTracingStateHolder = AtomicReference<RequestTracingState?>(null)
        )
        whenever(mockApmNetworkInstrumentation.onRequest(any())) doReturn mockNewTraceState
    }

    @Test
    fun `M delegate followRedirect W followRedirect()`() {
        // When
        testedRequest.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
    }

    @Test
    fun `M delegate start W start()`() {
        // When
        testedRequest.start()

        // Then
        verify(mockDelegate).start()
    }

    @Test
    fun `M delegate cancel W cancel()`() {
        // When
        testedRequest.cancel()

        // Then
        verify(mockDelegate).cancel()
    }

    @Test
    fun `M return delegate W delegate property`() {
        // Then
        assertThat(testedRequest.delegate).isSameAs(mockDelegate)
    }

    // region followRedirect instrumentation

    @Test
    fun `M replace method to GET W followRedirect() {method replacement redirect, POST}`(
        forge: Forge
    ) {
        // Given
        val statusCode = forge.anInt(HttpSpec.StatusCode.MOVED_PERMANENTLY, HttpSpec.StatusCode.SEE_OTHER)

        testedRequest = createCronetWrapper(
            redirectStatusCode = statusCode,
            previousRequestInfo = createRequestContext(HttpSpec.Method.POST).asCronetRequestInfo()
        )

        // When
        testedRequest.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
        argumentCaptor<HttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.url).isEqualTo(fakeUrl)
            assertThat(firstValue.method).isEqualTo(HttpSpec.Method.GET)
        }
        verify(mockApmNetworkInstrumentation).removeTracingHeaders(any())
        assertThat(requestTracingState.get()).isSameAs(mockNewTraceState)
    }

    @Test
    fun `M preserve method W followRedirect() {non method replacement redirect redirect, POST}`(
        forge: Forge
    ) {
        // Given
        val statusCode = forge.anInt(HttpSpec.StatusCode.TEMPORARY_REDIRECT, HttpSpec.StatusCode.PERMANENT_REDIRECT)

        testedRequest = createCronetWrapper(
            redirectStatusCode = statusCode,
            previousRequestInfo = createRequestContext(HttpSpec.Method.POST).asCronetRequestInfo()
        )

        // When
        testedRequest.followRedirect()

        // Then
        argumentCaptor<HttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.url).isEqualTo(fakeUrl)
            assertThat(firstValue.method).isEqualTo(HttpSpec.Method.POST)
        }
    }

    @Test
    fun `M preserve method W followRedirect() {non-replaceable methods}`(
        forge: Forge
    ) {
        // Given
        val statusCode = forge.anInt(HttpSpec.StatusCode.MOVED_PERMANENTLY, HttpSpec.StatusCode.PERMANENT_REDIRECT)
        val method = forge.anElementFrom(HttpSpec.Method.GET, HttpSpec.Method.HEAD)

        testedRequest = createCronetWrapper(
            redirectStatusCode = statusCode,
            previousRequestInfo = createRequestContext(method).asCronetRequestInfo()
        )

        // When
        testedRequest.followRedirect()

        // Then
        argumentCaptor<HttpRequestInfo> {
            verify(mockApmNetworkInstrumentation).onRequest(capture())
            assertThat(firstValue.url).isEqualTo(fakeUrl)
            assertThat(firstValue.method).isEqualTo(method)
        }
    }

    @Test
    fun `M not instrument redirect W followRedirect() {null apmNetworkInstrumentation}`(
        @IntForgery(
            min = HttpSpec.StatusCode.MOVED_PERMANENTLY,
            max = HttpSpec.StatusCode.PERMANENT_REDIRECT
        ) statusCode: Int
    ) {
        // Given
        testedRequest = createCronetWrapper(
            redirectStatusCode = statusCode,
            previousRequestInfo = createRequestContext(HttpSpec.Method.POST).asCronetRequestInfo(),
            apmInstrumentation = null
        )

        // When
        testedRequest.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
        assertThat(requestTracingState.get()).isNull()
    }

    @Test
    fun `M not instrument redirect W followRedirect() {null newLocationUrl}`(
        @IntForgery(
            min = HttpSpec.StatusCode.MOVED_PERMANENTLY,
            max = HttpSpec.StatusCode.PERMANENT_REDIRECT
        ) statusCode: Int
    ) {
        // Given
        testedRequest = createCronetWrapper(
            redirectStatusCode = statusCode,
            previousRequestInfo = createRequestContext(HttpSpec.Method.POST).asCronetRequestInfo(),
            newLocationUrl = null
        )

        // When
        testedRequest.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
        verify(mockApmNetworkInstrumentation, never()).onRequest(any())
        assertThat(requestTracingState.get()).isNull()
    }

    @Test
    fun `M not instrument redirect W followRedirect() {null previousRequestInfo}`(
        @IntForgery(
            min = HttpSpec.StatusCode.MOVED_PERMANENTLY,
            max = HttpSpec.StatusCode.PERMANENT_REDIRECT
        ) statusCode: Int
    ) {
        // Given
        testedRequest = createCronetWrapper(
            redirectStatusCode = statusCode,
            previousRequestInfo = null
        )

        // When
        testedRequest.followRedirect()

        // Then
        verify(mockDelegate).followRedirect()
        verify(mockApmNetworkInstrumentation, never()).onRequest(any())
        assertThat(requestTracingState.get()).isNull()
    }

    // endregion

    // region private

    private fun createRequestContext(method: String) = CronetRequestContext(
        url = fakeUrl,
        engine = mockEngine,
        requestCallback = mockCallback,
        executor = mockExecutor,
        requestParams = CronetRequestParams(method = method)
    )

    private fun createCronetWrapper(
        redirectStatusCode: Int,
        previousRequestInfo: HttpRequestInfo?,
        newLocationUrl: String? = fakeUrl,
        apmInstrumentation: ApmNetworkInstrumentation? = mockApmNetworkInstrumentation
    ) = CronetRedirectTracingRequestWrapper(
        delegate = mockDelegate,
        newLocationUrl = newLocationUrl,
        redirectStatusCode = redirectStatusCode,
        apmNetworkInstrumentation = apmInstrumentation,
        previousRequestInfo = previousRequestInfo,
        apmTracingStateHolder = requestTracingState
    )

    // endregion
}
