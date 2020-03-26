/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.rum.internal.monitor.NoOpRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.propagation.ExtractedContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import io.opentracing.propagation.TextMapExtract
import io.opentracing.util.GlobalTracer
import java.math.BigInteger
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
internal class RumInterceptorTest {
    lateinit var testedInterceptor: Interceptor

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockChain: Interceptor.Chain

    lateinit var fakeUrl: String
    var fakeMimeType: String? = null
    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMimeType = forge.aStringMatching("[a-z]+/[a-z]+")
        fakeUrl = forge.aStringMatching("http://[a-z0-9_]{8}\\.[a-z]{3}/")

        testedInterceptor = RumInterceptor()

        GlobalRum.registerIfAbsent(mockRumMonitor)
        GlobalTracer.registerIfAbsent(mockTracer)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `starts and stop a Rum Resource event around GET request`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        setupFakeResponse(statusCode, "GET", forge.anAsciiString())

        val response = testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                "GET",
                fakeUrl,
                emptyMap()
            )
            verify(mockRumMonitor).stopResource(
                fakeRequest,
                RumResourceKind.fromMimeType(fakeMimeType!!),
                mapOf(
                    RumAttributes.HTTP_STATUS_CODE to statusCode,
                    RumAttributes.NETWORK_BYTES_WRITTEN to
                        (fakeResponse.body()?.contentLength() ?: 0)
                )
            )
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `starts and stop a Rum Resource event around GET request without contentType`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        fakeMimeType = null
        setupFakeResponse(statusCode, "GET", forge.anAsciiString())

        val response = testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                "GET",
                fakeUrl,
                emptyMap()
            )
            verify(mockRumMonitor).stopResource(
                fakeRequest,
                RumResourceKind.UNKNOWN,
                mapOf(
                    RumAttributes.HTTP_STATUS_CODE to statusCode,
                    RumAttributes.NETWORK_BYTES_WRITTEN to
                        (fakeResponse.body()?.contentLength() ?: 0)
                )
            )
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `starts and stop a Rum Resource event around POST request`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        setupFakeResponse(statusCode, "POST", forge.anAsciiString())

        val response = testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                "POST",
                fakeUrl,
                emptyMap()
            )
            verify(mockRumMonitor).stopResource(
                fakeRequest,
                RumResourceKind.XHR,
                mapOf(
                    RumAttributes.HTTP_STATUS_CODE to statusCode,
                    RumAttributes.NETWORK_BYTES_WRITTEN to
                        (fakeResponse.body()?.contentLength() ?: 0)
                )
            )
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `starts and stop a Rum Resource event around PUT request`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        setupFakeResponse(statusCode, "PUT", forge.anAsciiString())

        val response = testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                "PUT",
                fakeUrl,
                emptyMap()
            )
            verify(mockRumMonitor).stopResource(
                fakeRequest,
                RumResourceKind.XHR,
                mapOf(
                    RumAttributes.HTTP_STATUS_CODE to statusCode,
                    RumAttributes.NETWORK_BYTES_WRITTEN to
                        (fakeResponse.body()?.contentLength() ?: 0)
                )
            )
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `starts and stop a Rum Resource event around DELETE request`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        setupFakeResponse(statusCode, "DELETE", forge.anAsciiString())

        val response = testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                "DELETE",
                fakeUrl,
                emptyMap()
            )
            verify(mockRumMonitor).stopResource(
                fakeRequest,
                RumResourceKind.XHR,
                mapOf(
                    RumAttributes.HTTP_STATUS_CODE to statusCode,
                    RumAttributes.NETWORK_BYTES_WRITTEN to
                        (fakeResponse.body()?.contentLength() ?: 0)
                )
            )
        }
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `sends a Rum Error event around request throwing exception`(
        forge: Forge
    ) {
        setupFakeResponse(
            200,
            forge.anElementFrom("GET", "POST", "PUT", "DELETE"),
            forge.anAsciiString()
        )
        val throwable = forge.aThrowable()
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        val error = try {
            testedInterceptor.intercept(mockChain)
            null
        } catch (t: Throwable) {
            t
        }

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                fakeRequest.method(),
                fakeUrl,
                emptyMap()
            )
            verify(mockRumMonitor).stopResourceWithError(
                fakeRequest,
                "OkHttp error on ${fakeRequest.method()}",
                "network",
                throwable
            )
        }
        assertThat(error).isSameAs(throwable)
    }

    @Test
    fun `extract the trace ID if any`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        @LongForgery traceId: Long,
        forge: Forge
    ) {
        val method = forge.anElementFrom("GET", "POST", "PUT", "DELETE")
        setupFakeResponse(statusCode, method, forge.anAsciiString())
        doAnswer {
            return@doAnswer ExtractedContext(
                BigInteger.valueOf(traceId),
                BigInteger.ZERO,
                0, "origin", emptyMap(), emptyMap()
            )
        }.whenever(mockTracer).extract<TextMapExtract>(any(), any())

        val response = testedInterceptor.intercept(mockChain)

        inOrder(mockRumMonitor) {
            verify(mockRumMonitor).startResource(
                fakeRequest,
                method,
                fakeUrl,
                mapOf(
                    RumAttributes.TRACE_ID to traceId.toString()
                )
            )
            verify(mockRumMonitor).stopResource(
                eq(fakeRequest),
                any(),
                eq(
                    mapOf(
                        RumAttributes.HTTP_STATUS_CODE to statusCode,
                        RumAttributes.NETWORK_BYTES_WRITTEN to
                            (fakeResponse.body()?.contentLength() ?: 0)
                    )
                )
            )
        }
        assertThat(response).isSameAs(fakeResponse)
        verifyNoMoreInteractions(mockRumMonitor)
    }

    // region Internal

    private fun setupFakeResponse(
        statusCode: Int,
        method: String,
        response: String
    ) {
        fakeRequest = Request.Builder()
            .url(fakeUrl)
            .apply {
                when (method) {
                    "POST" -> post(RequestBody.create(null, "{}".toByteArray()))
                    "GET" -> get()
                    "DELETE" -> delete()
                    "PUT" -> put(RequestBody.create(null, "{}".toByteArray()))
                }
            }
            .build()

        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .body(ResponseBody.create(null, response.toByteArray()))
            .message("STATUS $statusCode")
            .apply {
                fakeMimeType?.let { header("Content-Type", it) }
            }
            .build()

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(fakeRequest)) doReturn fakeResponse
    }

    // endregion
}
