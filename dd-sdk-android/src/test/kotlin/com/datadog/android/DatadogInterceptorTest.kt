/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.TracingInterceptorNotSendingSpanTest
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogInterceptorTest : TracingInterceptorNotSendingSpanTest() {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Forgery
    lateinit var fakeRumConfig: Configuration.Feature.RUM

    override fun instantiateTestedInterceptor(
        tracedHosts: List<String>,
        factory: () -> Tracer
    ): TracingInterceptor {
        RumFeature.initialize(
            mockAppContext,
            fakeRumConfig
        )
        GlobalRum.registerIfAbsent(mockRumMonitor)
        return DatadogInterceptor(tracedHosts, mockRequestListener, mockDetector, factory)
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @AfterEach
    fun `tear down RUM`() {
        GlobalRum.isRegistered.set(false)
        RumFeature.stop()
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId
        )
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            fakeMethod in DatadogInterceptor.xhrMethods -> RumResourceKind.XHR
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.UNKNOWN
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(mockRumMonitor).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() for failing request`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId
        )
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            fakeMethod in DatadogInterceptor.xhrMethods -> RumResourceKind.XHR
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.UNKNOWN
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(mockRumMonitor).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() for throwing request`(
        @Forgery throwable: Throwable
    ) {
        // Given
        val expectedStartAttrs = emptyMap<String, Any?>()
        val requestId = identifyRequest(fakeRequest)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        // When
        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(mockRumMonitor).stopResourceWithError(
                requestId,
                null,
                "OkHttp request error $fakeMethod $fakeUrl",
                RumErrorSource.NETWORK,
                throwable
            )
        }
    }
}
