/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import datadog.opentracing.DDSpanContext
import datadog.trace.api.interceptor.MutableSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.TextMapExtract
import io.opentracing.propagation.TextMapInject
import io.opentracing.util.GlobalTracer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
internal open class TracingInterceptorTest {

    lateinit var testedInterceptor: TracingInterceptor

    // region Mocks

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockSpanBuilder: Tracer.SpanBuilder

    @Mock
    lateinit var mockSpanContext: DDSpanContext

    @Mock(extraInterfaces = [MutableSpan::class])
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequestListener: TracedRequestListener

    lateinit var mockDevLogHandler: LogHandler

    lateinit var mockAppContext: Context

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    // endregion

    // region Fakes

    @RegexForgery(HOSTNAME_PATTERN)
    lateinit var fakeHostName: String

    @RegexForgery(IPV4_PATTERN)
    lateinit var fakeHostIp: String

    lateinit var fakeMethod: String
    var fakeBody: String? = null
    var fakeMediaType: MediaType? = null

    @StringForgery(StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeConfig: DatadogConfig.TracesConfig

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakePackageName: String

    @RegexForgery("\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    // endregion

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogHandler = mockDevLogHandler()
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        Datadog.setVerbosity(Log.VERBOSE)

        whenever(mockTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.asChildOf(null as SpanContext?)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId

        val mediaType = forge.anElementFrom("application", "image", "text", "model") +
            "/" + forge.anAlphabeticalString()
        fakeMediaType = MediaType.parse(mediaType)
        fakeRequest = buildRequest(forge)
        val tracedHosts = listOf(fakeHostIp, fakeHostName)
        testedInterceptor = instantiateTestedInterceptor(tracedHosts) { mockLocalTracer }
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig,
            mock(), mock(), mock(), mock(), mock(), mock(), mock()
        )

        GlobalRum.registerIfAbsent(mockRumMonitor)
        GlobalTracer.registerIfAbsent(mockTracer)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    open fun instantiateTestedInterceptor(
        tracedHosts: List<String>,
        factory: () -> Tracer
    ): TracingInterceptor {
        return TracingInterceptor(tracedHosts, mockRequestListener, factory)
    }

    @Test
    fun `injects header when preparing request`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        setupFakeResponse(statusCode)
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
    fun `injects header with parent context from tag when preparing request`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        val parentSpan: Span = mock()
        val parentSpanContext: SpanContext = mock()
        whenever(parentSpan.context()) doReturn parentSpanContext
        whenever(mockSpanBuilder.asChildOf(parentSpanContext)) doReturn mockSpanBuilder
        fakeRequest = buildRequest(forge) { it.tag(Span::class.java, parentSpan) }
        setupFakeResponse(statusCode)
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
        verify(mockSpanBuilder).asChildOf(parentSpanContext)
    }

    @Test
    fun `updates header with parent context when preparing request`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key: String,
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) value: String,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @LongForgery(min = 0) traceId: Long,
        @LongForgery(min = 0) spanId: Long,
        forge: Forge
    ) {
        val parentSpanContext: SpanContext = mock()
        whenever(mockTracer.extract<TextMapExtract>(any(), any())) doReturn parentSpanContext
        whenever(mockSpanBuilder.asChildOf(any<SpanContext>())) doReturn mockSpanBuilder
        fakeRequest = buildRequest(forge)
        setupFakeResponse(statusCode)
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
        verify(mockSpanBuilder).asChildOf(parentSpanContext)
    }

    @Test
    fun `creates a trace around request and fills request info`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        setupFakeResponse(statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `creates a trace around failing request and fills request info`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        setupFakeResponse(statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan as MutableSpan).setResourceName(fakeUrl)
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan as MutableSpan).setError(true)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `creates a trace around 404 request and fills request info`() {
        setupFakeResponse(404)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", 404)
        verify(mockSpan as MutableSpan).setError(true)
        verify(mockSpan as MutableSpan).setResourceName(TracingInterceptor.RESOURCE_NAME_404)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `creates a trace around throwing request and fills request info`(
        @Forgery throwable: Throwable
    ) {
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).finish()
    }

    @Test
    fun `warns the user if no tracer registered and TracingFeature not initialized`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        TracesFeature.invokeMethod("stop")
        setupFakeResponse(statusCode)

        testedInterceptor.intercept(mockChain)

        verifyZeroInteractions(mockLocalTracer)
        verifyZeroInteractions(mockTracer)
        verify(mockDevLogHandler)
            .handleLog(
                Log.WARN,
                TracingInterceptor.WARNING_TRACING_DISABLED
            )
    }

    @Test
    fun `creates a trace with automatic tracer if none is registered`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val localSpanBuilder: Tracer.SpanBuilder = mock()
        val localSpan: Span = mock(extraInterfaces = arrayOf(MutableSpan::class))
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        setupFakeResponse(statusCode)
        whenever(localSpanBuilder.asChildOf(null as SpanContext?)) doReturn localSpanBuilder
        whenever(localSpanBuilder.start()) doReturn localSpan
        whenever(localSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        val response = testedInterceptor.intercept(mockChain)

        verify(localSpan).setTag("http.url", fakeUrl)
        verify(localSpan).setTag("http.method", fakeMethod)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(localSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
        verify(mockDevLogHandler)
            .handleLog(
                Log.WARN,
                TracingInterceptor.WARNING_DEFAULT_TRACER
            )
    }

    @Test
    fun `when registering a global tracer the local tracer will be dropped`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        val localSpanBuilder: Tracer.SpanBuilder = mock()
        val localSpan: Span = mock(extraInterfaces = arrayOf(MutableSpan::class))
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        setupFakeResponse(statusCode)
        whenever(localSpanBuilder.asChildOf(null as SpanContext?)) doReturn localSpanBuilder
        whenever(localSpanBuilder.start()) doReturn localSpan
        whenever(localSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn localSpanBuilder

        val response1 = testedInterceptor.intercept(mockChain)
        val expectedResponse1 = fakeResponse
        GlobalTracer.registerIfAbsent(mockTracer)
        setupFakeResponse(statusCode)
        val response2 = testedInterceptor.intercept(mockChain)
        val expectedResponse2 = fakeResponse

        verify(localSpan).setTag("http.url", fakeUrl)
        verify(localSpan).setTag("http.method", fakeMethod)
        verify(localSpan).setTag("http.status_code", statusCode)
        verify(localSpan).finish()
        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).finish()
        assertThat(response1).isSameAs(expectedResponse1)
        assertThat(response2).isSameAs(expectedResponse2)
        verify(mockDevLogHandler)
            .handleLog(
                Log.WARN,
                TracingInterceptor.WARNING_DEFAULT_TRACER
            )
    }

    @Test
    fun `calls the listener after successfull request`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery(StringForgeryType.ALPHABETICAL) tagKey: String,
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        setupFakeResponse(statusCode)
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("http.status_code", statusCode)
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `calls the listener after throwing request`(
        @Forgery throwable: Throwable,
        @StringForgery(StringForgeryType.ALPHABETICAL) tagKey: String,
        @StringForgery(StringForgeryType.ALPHA_NUMERICAL) tagValue: String
    ) {
        whenever(
            mockRequestListener.onRequestIntercepted(any(), any(), anyOrNull(), anyOrNull())
        ).doAnswer {
            val span = it.arguments[1] as Span
            span.setTag(tagKey, tagValue)
            return@doAnswer Unit
        }
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockSpan).setTag("http.url", fakeUrl)
        verify(mockSpan).setTag("http.method", fakeMethod)
        verify(mockSpan).setTag("error.type", throwable.javaClass.canonicalName)
        verify(mockSpan).setTag("error.msg", throwable.message)
        verify(mockSpan).setTag("error.stack", throwable.loggableStackTrace())
        verify(mockSpan).setTag(tagKey, tagValue)
        verify(mockSpan).finish()
    }

    @Test
    fun `does nothing if the host is not in the tracedHost list`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        fakeRequest = buildRequest(forge, false)
        setupFakeResponse(statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verifyZeroInteractions(mockTracer, mockLocalTracer)
        assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `when called from multiple threads will only create one local tracer`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        var called = 0
        val tracedHosts = listOf(fakeHostIp, fakeHostName)
        testedInterceptor = instantiateTestedInterceptor(tracedHosts) {
            called++
            mockLocalTracer
        }
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        setupFakeResponse(statusCode)

        val countDownLatch = CountDownLatch(2)
        Thread {
            testedInterceptor.intercept(mockChain)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedInterceptor.intercept(mockChain)
            countDownLatch.countDown()
        }.start()

        // then
        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockLocalTracer, times(2)).buildSpan(TracingInterceptor.SPAN_NAME)
        assertThat(called).isEqualTo(1)
    }

    // region Internal

    internal fun buildRequest(
        forge: Forge,
        validHost: Boolean = true,
        configure: (Request.Builder) -> Unit = {}
    ): Request {
        val protocol = forge.anElementFrom("http", "https")
        val host = if (validHost) {
            forge.anElementFrom(fakeHostIp, fakeHostName)
        } else {
            forge.aString(3) { anAlphabeticalChar() } + fakeHostName
        }

        val path = forge.anAlphaNumericalString()
        fakeUrl = "$protocol://$host/$path"
        val builder = Request.Builder().url(fakeUrl)
        if (forge.aBool()) {
            fakeMethod = "POST"
            fakeBody = forge.anAlphabeticalString()
            builder.post(RequestBody.create(null, fakeBody!!.toByteArray()))
        } else {
            fakeMethod = forge.anElementFrom("GET", "HEAD", "DELETE")
            fakeBody = null
            builder.method(fakeMethod, null)
        }

        configure(builder)

        return builder.build()
    }

    internal fun setupFakeResponse(statusCode: Int = 200) {

        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
            .body(ResponseBody.create(fakeMediaType, fakeResponseBody))

        fakeResponse = builder.build()

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse
    }

    // endregion

    companion object {
        const val HOSTNAME_PATTERN =
            "([a-z0-9]|[a-z0-9][a-z0-9\\-][a-z0-9])\\.([a-z0-9]|[a-z0-9][a-z0-9\\-])[a-z0-9]"
        const val IPV4_PATTERN =
            "(([0-9]|[1-9][0-9]|1[0-9]){2}\\.|(2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
    }
}
