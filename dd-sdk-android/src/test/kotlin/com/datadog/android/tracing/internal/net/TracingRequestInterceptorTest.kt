/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.net

import android.content.Context
import android.util.Log
import com.datadog.android.DatadogConfig
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapInject
import io.opentracing.util.GlobalTracer
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracingRequestInterceptorTest {

    @Spy
    lateinit var testedInterceptor: TracingRequestInterceptor

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder

    @Mock
    lateinit var mockSpanContext: SpanContext

    @Mock
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockSpan2: Span

    @Mock
    lateinit var mockResponse: Response

    lateinit var mockDevLogHandler: LogHandler
    lateinit var mockAppContext: Context

    @RegexForgery("GET|POST|DELETE")
    lateinit var fakeMethod: String

    @RegexForgery("[a-z]+/[a-z]+")
    lateinit var fakeMimeType: String

    @RegexForgery("http://[a-z0-9_]{8}\\.[a-z]{3}/")
    lateinit var fakeUrl: String

    @RegexForgery("http://[a-z0-9_]{8}\\.[a-z]{3}/")
    lateinit var fakeUrl2: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeBodyContent: String

    @Forgery
    lateinit var fakeThrowable: Throwable

    var fakeBody: RequestBody? = null
    lateinit var fakeRequest: Request

    @Forgery
    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakePackageName: String

    @RegexForgery("\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    @BeforeEach
    fun `set up`() {
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        mockDevLogHandler = mockDevLogHandler()
        whenever(mockTracer.buildSpan("okhttp.request")) doReturn mockSpanBuilder
        whenever(mockLocalTracer.buildSpan("okhttp.request")) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()).doReturn(mockSpan, mockSpan2, null)
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpan2.context()) doReturn mockSpanContext

        doReturn(mockLocalTracer).whenever(testedInterceptor).buildLocalTracer()

        fakeBody = if (fakeMethod == "POST") RequestBody.create(null, fakeBodyContent) else null
        fakeRequest = Request.Builder()
            .url(fakeUrl)
            .method(fakeMethod, fakeBody)
            .build()

        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            mock()
        )

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
    fun `injects tracing header on transformRequest`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHABETICAL) value: String
    ) {
        doAnswer { invocation ->
            val carrier = invocation.arguments[2] as TextMapInject
            carrier.put(key, value)
        }.whenever(mockTracer).inject<TextMapInject>(any(), any(), any())

        val transformedRequest = testedInterceptor.transformRequest(fakeRequest)

        assertThat(transformedRequest.url()).isEqualTo(fakeRequest.url())
        assertThat(transformedRequest.method()).isEqualTo(fakeMethod)
        assertThat(transformedRequest.header(key)).isEqualTo(value)
        inOrder(mockSpan) {
            verify(mockSpan).setTag("http.url", fakeUrl)
            verify(mockSpan).setTag("http.method", fakeMethod)
            verify(mockSpan, never()).finish()
        }
    }

    @Test
    fun `starts and stop span around successful request`(
        @IntForgery(200, 300) statusCode: Int
    ) {
        whenever(mockResponse.code()) doReturn statusCode

        val transformedRequest = testedInterceptor.transformRequest(fakeRequest)
        testedInterceptor.handleResponse(transformedRequest, mockResponse)

        inOrder(mockSpan) {
            verify(mockSpan).setTag("http.url", fakeUrl)
            verify(mockSpan).setTag("http.method", fakeMethod)
            verify(mockSpan).setTag("http.status_code", statusCode)
            verify(mockSpan).finish()
        }
    }

    @Test
    fun `starts and stop span around successful request multithreaded`(
        @IntForgery(200, 300) statusCode: Int
    ) {
        val fakeRequest2 = Request.Builder()
            .url(fakeUrl2)
            .method(fakeMethod, fakeBody)
            .build()
        whenever(mockResponse.code()) doReturn statusCode
        val countDownLatch = CountDownLatch(2)
        val runnable1 = Runnable {
            val transformedRequest = testedInterceptor.transformRequest(fakeRequest)
            Thread.sleep(200)
            testedInterceptor.handleResponse(transformedRequest, mockResponse)
            countDownLatch.countDown()
        }
        val runnable2 = Runnable {
            val transformedRequest = testedInterceptor.transformRequest(fakeRequest2)
            Thread.sleep(200)
            testedInterceptor.handleResponse(transformedRequest, mockResponse)
            countDownLatch.countDown()
        }

        Thread(runnable1).start()
        Thread(runnable2).start()
        countDownLatch.await(2, TimeUnit.SECONDS)

        inOrder(mockSpan) {
            verify(mockSpan).setTag(
                eq("http.url"),
                argThat<String> { this in arrayOf(fakeUrl, fakeUrl2) })
            verify(mockSpan).setTag("http.method", fakeMethod)
            verify(mockSpan).setTag("http.status_code", statusCode)
            verify(mockSpan).finish()
        }
        inOrder(mockSpan2) {
            verify(mockSpan2).setTag(
                eq("http.url"),
                argThat<String> { this in arrayOf(fakeUrl, fakeUrl2) })
            verify(mockSpan2).setTag("http.method", fakeMethod)
            verify(mockSpan2).setTag("http.status_code", statusCode)
            verify(mockSpan2).finish()
        }
    }

    @Test
    fun `starts and stop span around throwing request`() {
        val sw = StringWriter()
        fakeThrowable.printStackTrace(PrintWriter(sw))

        val transformedRequest = testedInterceptor.transformRequest(fakeRequest)
        testedInterceptor.handleThrowable(transformedRequest, fakeThrowable)

        inOrder(mockSpan) {
            verify(mockSpan).setTag("http.url", fakeUrl)
            verify(mockSpan).setTag("http.method", fakeMethod)
            verify(mockSpan).setTag("error.msg", fakeThrowable.message)
            verify(mockSpan).setTag("error.type", fakeThrowable.javaClass.canonicalName)
            verify(mockSpan).setTag("error.stack", sw.toString())
            verify(mockSpan).finish()
        }
    }

    @Test
    fun `warns the user if no tracer registered and TracingFeature not initialized`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        TracesFeature.invokeMethod("stop")

        val transformedRequest = testedInterceptor.transformRequest(fakeRequest)

        verifyZeroInteractions(mockTracer, mockLocalTracer)
        verify(mockDevLogHandler)
            .handleLog(Log.WARN, TracingRequestInterceptor.WARNING_NO_TRACER)
        assertThat(transformedRequest).isSameAs(fakeRequest)
    }

    @Test
    fun `uses the local tracer if no tracer is registered`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)

        val transformedRequest = testedInterceptor.transformRequest(fakeRequest)

        verifyZeroInteractions(mockTracer)
        inOrder(mockLocalTracer, mockSpan) {
            verify(mockLocalTracer).buildSpan("okhttp.request")
            verify(mockSpan).setTag("http.url", fakeUrl)
            verify(mockSpan).setTag("http.method", fakeMethod)
            verify(mockSpan, never()).finish()
        }
        verify(mockDevLogHandler)
            .handleLog(Log.WARN, TracingRequestInterceptor.WARNING_DEFAULT_TRACER)
        assertThat(transformedRequest).isNotSameAs(fakeRequest)
    }

    @Test
    fun `when registering a GlobalTracer the local tracer will be dropped`(
        @IntForgery(min = 200, max = 299) statusCode: Int
    ) {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)

        testedInterceptor.transformRequest(fakeRequest)

        GlobalTracer.registerIfAbsent(mockTracer)
        testedInterceptor.transformRequest(fakeRequest)

        inOrder(mockSpan, mockSpan2, mockTracer, mockLocalTracer) {
            verify(mockLocalTracer).buildSpan("okhttp.request")
            verify(mockSpan).setTag("http.url", fakeUrl)
            verify(mockSpan).setTag("http.method", fakeMethod)
            verify(mockSpan).context()
            verify(mockLocalTracer).inject(
                any(), same(Format.Builtin.TEXT_MAP_INJECT), any()
            )

            verify(mockTracer).buildSpan("okhttp.request")
            verify(mockSpan2).setTag("http.url", fakeUrl)
            verify(mockSpan2).setTag("http.method", fakeMethod)
            verify(mockSpan2).context()
            verify(mockTracer).inject(
                any(), same(Format.Builtin.TEXT_MAP_INJECT), any()
            )
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `create only one local tracer when called from multiple threads`() {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        val countDownLatch = CountDownLatch(2)

        Thread {
            testedInterceptor.transformRequest(fakeRequest)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedInterceptor.transformRequest(fakeRequest)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(testedInterceptor).buildLocalTracer()
        verify(mockLocalTracer, times(2)).buildSpan("okhttp.request")
    }
}
