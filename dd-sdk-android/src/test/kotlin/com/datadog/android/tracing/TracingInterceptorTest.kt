/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.propagation.TextMapInject
import io.opentracing.util.GlobalTracer
import java.io.PrintWriter
import java.io.StringWriter
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
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
internal class TracingInterceptorTest {

    lateinit var testedInterceptor: Interceptor

    @Mock
    lateinit var mockTracer: Tracer
    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder
    @Mock
    lateinit var mockSpanContext: DDSpanContext
    @Mock
    lateinit var mockSpan: Span
    @Mock
    lateinit var mockChain: Interceptor.Chain

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response
    lateinit var fakeSpanId: String
    lateinit var fakeTraceId: String

    @BeforeEach
    fun `set up`(forge: Forge) {

        fakeSpanId = forge.anHexadecimalString()
        fakeTraceId = forge.anHexadecimalString()
        whenever(mockTracer.buildSpan("okhttp.request")) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId

        val fakeUrl = forge.aStringMatching("http://[a-z0-9_]{8}\\.[a-z]{3}")
        val builder = Request.Builder()
            .url(fakeUrl)
        if (forge.aBool()) {
            val fakeBody = forge.anAlphabeticalString()
            builder.post(RequestBody.create(null, fakeBody.toByteArray()))
        } else {
            builder.get()
        }

        fakeRequest = builder.build()
        testedInterceptor = TracingInterceptor()

        GlobalTracer.registerIfAbsent(mockTracer)
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `injects header when preparing request`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        setupFakeResponse(statusCode)
        val key = forge.anAlphabeticalString()
        val value = forge.anAsciiString()
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `starts a trace around request and fills request info`(
        @IntForgery(min = 200, max = 299) statusCode: Int,
        forge: Forge
    ) {
        setupFakeResponse(statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeRequest.url().toString())
        verify(mockSpan).setTag("http.method", fakeRequest.method())
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `starts a trace around throwing request and fills request info`(
        forge: Forge
    ) {
        val throwable = RuntimeException(forge.anAlphabeticalString())
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable
        val sw = StringWriter()

        try {
            testedInterceptor.intercept(mockChain)
            throw IllegalStateException("Should have failed !")
        } catch (e: Throwable) {
            e.printStackTrace(PrintWriter(sw))
            assertThat(e).isSameAs(throwable)
        }

        verify(mockSpan).setTag("http.url", fakeRequest.url().toString())
        verify(mockSpan).setTag("http.method", fakeRequest.method())
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", sw.toString())
        verify(mockSpan).finish()
    }

    // region Internal

    private fun setupFakeResponse(statusCode: Int = 200) {

        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("{}")

        fakeResponse = builder.build()

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse
    }

    // endregion
}
