/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.core.internal.net.RequestInterceptor
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.propagation.ExtractedContext
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import io.opentracing.propagation.TextMapExtract
import io.opentracing.util.GlobalTracer
import java.math.BigInteger
import okhttp3.HttpUrl
import okhttp3.Request
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
internal class RumRequestInterceptorTest {

    lateinit var testedInterceptor: RequestInterceptor

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockResponse: Response

    @Mock
    lateinit var mockRequest: Request

    @RegexForgery("GET|POST|PUT|DELETE")
    lateinit var fakeMethod: String

    @RegexForgery("[a-z]+/[a-z]+")
    lateinit var fakeMimeType: String

    @RegexForgery("http://[a-z0-9_]{8}\\.[a-z]{3}/")
    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeThrowable: Throwable

    @BeforeEach
    fun `set up`() {
        whenever(mockRequest.url()) doReturn HttpUrl.parse(fakeUrl)!!
        whenever(mockRequest.method()) doReturn fakeMethod

        testedInterceptor = RumRequestInterceptor()

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
    fun `starts Rum Resource event on transformRequest`() {
        val transformedRequest = testedInterceptor.transformRequest(mockRequest)

        assertThat(transformedRequest).isSameAs(mockRequest)
        verify(mockRumMonitor).startResource(
            mockRequest,
            fakeMethod,
            fakeUrl,
            emptyMap()
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `starts Rum Resource event with span info on transformRequest within Span`(
        @LongForgery traceId: Long
    ) {
        doAnswer {
            return@doAnswer ExtractedContext(
                BigInteger.valueOf(traceId),
                BigInteger.ZERO,
                0, "origin", emptyMap(), emptyMap()
            )
        }.whenever(mockTracer).extract<TextMapExtract>(any(), any())

        val transformedRequest = testedInterceptor.transformRequest(mockRequest)

        assertThat(transformedRequest).isSameAs(mockRequest)
        verify(mockRumMonitor).startResource(
            mockRequest,
            fakeMethod,
            fakeUrl,
            mapOf(RumAttributes.TRACE_ID to traceId.toString())
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource on handleResponse (GET without ContentType)`(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn null
        whenever(mockRequest.method()) doReturn "GET"

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            RumResourceKind.UNKNOWN,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource on handleResponse (GET with ContentType)`(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn fakeMimeType
        whenever(mockRequest.method()) doReturn "GET"

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            RumResourceKind.fromMimeType(fakeMimeType),
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource on handleResponse (POST)`(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn fakeMimeType
        whenever(mockRequest.method()) doReturn "POST"

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            RumResourceKind.XHR,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource on handleResponse (PUT)`(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn fakeMimeType
        whenever(mockRequest.method()) doReturn "PUT"

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            RumResourceKind.XHR,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource on handleResponse (DELETE)`(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn fakeMimeType
        whenever(mockRequest.method()) doReturn "DELETE"

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            RumResourceKind.XHR,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource with Error on handleThrowable`(
        @IntForgery(200, 300) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn fakeMimeType
        whenever(mockRequest.method()) doReturn fakeMethod

        testedInterceptor.handleThrowable(mockRequest, fakeThrowable)

        verify(mockRumMonitor).stopResourceWithError(
            mockRequest,
            "OkHttp request error $fakeMethod $fakeUrl",
            "network",
            fakeThrowable
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource and add Error on handleResponse 4xx`(
        @IntForgery(400, 500) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn null
        whenever(mockRequest.method()) doReturn fakeMethod
        val kind = if (fakeMethod == "GET") RumResourceKind.UNKNOWN else RumResourceKind.XHR

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            kind,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verify(mockRumMonitor).addError(
            "OkHttp request error $fakeMethod $fakeUrl",
            "network",
            null,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `stops Rum Resource and add Error on handleResponse 5xx`(
        @IntForgery(500, 600) statusCode: Int,
        @StringForgery(StringForgeryType.ASCII) body: String
    ) {
        whenever(mockResponse.code()) doReturn statusCode
        whenever(mockResponse.body()) doReturn ResponseBody.create(null, body)
        whenever(mockResponse.header(RumRequestInterceptor.HEADER_CT)) doReturn null
        whenever(mockRequest.method()) doReturn fakeMethod
        val kind = if (fakeMethod == "GET") RumResourceKind.UNKNOWN else RumResourceKind.XHR

        testedInterceptor.handleResponse(mockRequest, mockResponse)

        verify(mockRumMonitor).stopResource(
            mockRequest,
            kind,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode,
                RumAttributes.NETWORK_BYTES_WRITTEN to body.toByteArray().size.toLong()
            )
        )
        verify(mockRumMonitor).addError(
            "OkHttp request error $fakeMethod $fakeUrl",
            "network",
            null,
            mapOf(
                RumAttributes.HTTP_STATUS_CODE to statusCode
            )
        )
        verifyNoMoreInteractions(mockRumMonitor)
    }
}
