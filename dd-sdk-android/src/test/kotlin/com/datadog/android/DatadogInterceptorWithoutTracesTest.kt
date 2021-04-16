/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.content.Context
import android.util.Log
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.TracedRequestListener
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.TracingInterceptorTest
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockDevLogHandler
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.DDSpanContext
import com.datadog.opentracing.DDTracer
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.trace.api.interceptor.MutableSpan
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.SpanContext
import io.opentracing.Tracer
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogInterceptorWithoutTracesTest {

    lateinit var testedInterceptor: TracingInterceptor

    // region Mocks

    @Mock
    lateinit var mockLocalTracer: Tracer

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequestListener: TracedRequestListener

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    lateinit var mockDevLogHandler: LogHandler

    lateinit var mockAppContext: Context

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockSpanBuilder: DDTracer.DDSpanBuilder

    @Mock
    lateinit var mockSpanContext: DDSpanContext

    @Mock
    lateinit var mockSpan: DDSpan

    // endregion

    // region Fakes

    lateinit var fakeMethod: String
    var fakeBody: String? = null
    var fakeMediaType: MediaType? = null

    @StringForgery(type = StringForgeryType.ASCII)
    lateinit var fakeResponseBody: String

    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeConfig: Configuration.Feature.Tracing

    @Forgery
    lateinit var fakeRumConfig: Configuration.Feature.RUM

    @StringForgery
    lateinit var fakePackageName: String

    @StringForgery(regex = "\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    lateinit var fakeResourceAttributes: Map<String, Any?>

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(type = StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    // endregion

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDevLogHandler = mockDevLogHandler()
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        Datadog.setVerbosity(Log.VERBOSE)

        whenever(mockLocalTracer.buildSpan(TracingInterceptor.SPAN_NAME)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.withOrigin(DatadogInterceptor.ORIGIN_RUM)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.asChildOf(null as SpanContext?)) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId

        val mediaType = forge.anElementFrom("application", "image", "text", "model") +
            "/" + forge.anAlphabeticalString()
        fakeMediaType = MediaType.parse(mediaType)
        fakeRequest = forgeRequest(forge)
        testedInterceptor = DatadogInterceptor(
            emptyList(),
            mockRequestListener,
            mockDetector,
            mockRumAttributesProvider
        ) { mockLocalTracer }
        TracesFeature.initialize(
            mockAppContext,
            fakeConfig
        )
        RumFeature.initialize(
            mockAppContext,
            fakeRumConfig
        )

        fakeResourceAttributes = forge.exhaustiveAttributes()

        whenever(
            mockRumAttributesProvider.onProvideAttributes(
                any(),
                anyOrNull(),
                anyOrNull()
            )
        ) doReturn fakeResourceAttributes
    }

    @AfterEach
    fun `tear down`() {
        TracesFeature.stop()
        RumFeature.stop()
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = fakeResourceAttributes
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
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResource(
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
        val expectedStopAttrs = fakeResourceAttributes
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
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                fakeResponseBody.toByteArray().size.toLong(),
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 starts and stop RUM Resource 𝕎 intercept() for throwing request`(
        @Forgery throwable: Throwable
    ) {
        // Given
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = fakeResourceAttributes
        val requestId = identifyRequest(fakeRequest)
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        // When
        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                expectedStartAttrs
            )
            verify(rumMonitor.mockInstance).stopResourceWithError(
                requestId,
                null,
                "OkHttp request error $fakeMethod $fakeUrl",
                RumErrorSource.NETWORK,
                throwable,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 create and drop a Span with info 𝕎 intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        whenever(mockDetector.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(DatadogInterceptor.ORIGIN_RUM)
        verify(mockSpan).drop()
        Assertions.assertThat(response).isSameAs(fakeResponse)
    }

    @Test
    fun `𝕄 create and drop a span with info 𝕎 intercept() for failing request {4xx}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        whenever(mockDetector.isFirstPartyUrl(HttpUrl.get(fakeUrl))).thenReturn(true)
        stubChain(mockChain, statusCode)

        val response = testedInterceptor.intercept(mockChain)

        verify(mockSpanBuilder).withOrigin(DatadogInterceptor.ORIGIN_RUM)
        verify(mockSpan as MutableSpan).setResourceName(fakeUrl)
        verify(mockSpan as MutableSpan).setError(true)
        verify(mockSpan).drop()
        Assertions.assertThat(response).isSameAs(fakeResponse)
    }

    // region Internal

    private fun stubChain(chain: Interceptor.Chain, statusCode: Int) {
        fakeResponse = forgeResponse(statusCode)

        whenever(chain.request()) doReturn fakeRequest
        whenever(chain.proceed(any())) doReturn fakeResponse
    }

    private fun forgeRequest(
        forge: Forge,
        configure: (Request.Builder) -> Unit = {}
    ): Request {
        val protocol = forge.anElementFrom("http", "https")
        val host = forge.aStringMatching(TracingInterceptorTest.HOSTNAME_PATTERN)
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

    private fun forgeResponse(statusCode: Int): Response {
        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("HTTP $statusCode")
            .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
            .body(ResponseBody.create(fakeMediaType, fakeResponseBody))
        return builder.build()
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val coreFeature = CoreFeatureTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor, coreFeature)
        }
    }
}
