/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.tracing

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
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
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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

    lateinit var testedInterceptor: TracingInterceptor

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
    lateinit var mockDevLogHandler: LogHandler

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response
    lateinit var fakeSpanId: String
    lateinit var fakeTraceId: String
    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String
    lateinit var mockAppContext: Context
    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogHandler = mockDevLogHandler()
        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString(),
            envName = forge.anAlphabeticalString()
        )

        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)

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
        TracesFeature.initialize(mockAppContext, fakeConfig, mock(), mock(), mock(), mock(), mock())
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
        val value = forge.anAlphaNumericalString()
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val response = testedInterceptor.intercept(mockChain)

        assertThat(response).isSameAs(fakeResponse)
        argumentCaptor<Request> {
            verify(mockChain).proceed(capture())

            assertThat(lastValue.header(key)).isEqualTo(value)
        }
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

        try {
            testedInterceptor.intercept(mockChain)
            throw IllegalStateException("Should have failed !")
        } catch (e: Throwable) {
            assertThat(e).isSameAs(throwable)
        }

        verify(mockSpan).setTag("http.url", fakeRequest.url().toString())
        verify(mockSpan).setTag("http.method", fakeRequest.method())
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).finish()
    }

    @Test
    fun `warns the user if no tracer registered and TracingFeature not initialized`(
        @IntForgery(min = 200, max = 299) statusCode: Int
    ) {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        TracesFeature.invokeMethod("stop")
        Datadog.setVerbosity(Log.VERBOSE)
        setupFakeResponse(statusCode)
        val spiedInterceptor = spy(testedInterceptor)
        val mockLocalTracer: Tracer = mock()
        doReturn(mockLocalTracer).whenever(spiedInterceptor).buildLocalTracer()

        // when
        spiedInterceptor.intercept(mockChain)

        verifyZeroInteractions(mockLocalTracer)
        verifyZeroInteractions(mockTracer)
        verify(mockDevLogHandler)
            .handleLog(
                Log.WARN,
                "You added the TracingInterceptor to your OkHttpClient " +
                    "but you did not enable the TracesFeature."
            )
    }

    @Test
    fun `uses the local tracer if no tracer is registered`(
        @IntForgery(min = 200, max = 299) statusCode: Int
    ) {
        // given
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        Datadog.setVerbosity(Log.VERBOSE)
        setupFakeResponse(statusCode)
        val spiedInterceptor = spy(testedInterceptor)
        val mockLocalTracer: Tracer = mock()
        doReturn(mockLocalTracer).whenever(spiedInterceptor).buildLocalTracer()
        whenever(mockLocalTracer.buildSpan(any())).thenReturn(mockSpanBuilder)

        // when
        val response = spiedInterceptor.intercept(mockChain)

        // then
        verify(mockLocalTracer).buildSpan("okhttp.request")
        verify(mockSpan).setTag("http.url", fakeRequest.url().toString())
        verify(mockSpan).setTag("http.method", fakeRequest.method())
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)

        verify(mockDevLogHandler)
            .handleLog(
                Log.WARN,
                "You added the TracingInterceptor to your OkHttpClient, " +
                    "but you didn't register any Tracer. " +
                    "We automatically created a local tracer for you. " +
                    "If you choose to register a GlobalTracer we will do the switch for you."
            )
    }

    @Test
    fun `when registering a global tracer the local tracer will be dropped`(
        @IntForgery(min = 200, max = 299) statusCode: Int
    ) {
        // given
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        setupFakeResponse(statusCode)
        val spiedInterceptor = spy(testedInterceptor)
        val mockLocalTracer: Tracer = mock()
        doReturn(mockLocalTracer).whenever(spiedInterceptor).buildLocalTracer()
        whenever(mockLocalTracer.buildSpan(any())).thenReturn(mockSpanBuilder)
        spiedInterceptor.intercept(mockChain)

        // when
        GlobalTracer.registerIfAbsent(mockTracer)
        spiedInterceptor.intercept(mockChain)

        // then
        val localTracerReference: AtomicReference<Tracer> =
            spiedInterceptor.getFieldValue("localTracerReference")
        assertThat(localTracerReference.get()).isNull()
    }

    @Test
    fun `when called from multiple threads will only create one local tracer`(
        @IntForgery(min = 200, max = 299) statusCode: Int
    ) {
        // given
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        setupFakeResponse(statusCode)
        val spiedInterceptor = spy(testedInterceptor)
        val mockLocalTracer1: Tracer = mock()
        val mockLocalTracer2: Tracer = mock()
        doReturn(mockLocalTracer1)
            .doReturn(mockLocalTracer2)
            .whenever(spiedInterceptor).buildLocalTracer()
        whenever(mockLocalTracer1.buildSpan(any())).thenReturn(mockSpanBuilder)
        whenever(mockLocalTracer2.buildSpan(any())).thenReturn(mockSpanBuilder)

        val countDownLatch = CountDownLatch(2)
        // when
        Thread {
            spiedInterceptor.intercept(mockChain)
            countDownLatch.countDown()
        }.start()
        Thread {
            spiedInterceptor.intercept(mockChain)
            countDownLatch.countDown()
        }.start()

        // then
        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockLocalTracer1, times(2)).buildSpan("okhttp.request")
        verifyZeroInteractions(mockLocalTracer2)
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
