/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.tracing.NoOpTracedRequestListener
import com.datadog.android.tracing.TracingHeaderType
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.TracingInterceptorNotSendingSpanTest
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.v2.core.NoOpSdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
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
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogInterceptorTest : TracingInterceptorNotSendingSpanTest() {

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    @FloatForgery(0f, 1f)
    var fakeTracingSamplingRate: Float = 0f

    private lateinit var fakeAttributes: Map<String, Any?>

    override fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>>,
        factory: (Set<TracingHeaderType>) -> Tracer
    ): TracingInterceptor {
        whenever((Datadog.globalSdkCore as DatadogCore).rumFeature) doReturn mock()
        return DatadogInterceptor(
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            firstPartyHostResolver = mockResolver,
            rumResourceAttributesProvider = mockRumAttributesProvider,
            traceSampler = mockTraceSampler,
            localTracerFactory = factory
        )
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        fakeAttributes = forge.exhaustiveAttributes()
        whenever(
            mockRumAttributesProvider.onProvideAttributes(
                any(),
                anyOrNull(),
                anyOrNull()
            )
        ) doReturn fakeAttributes
        whenever(mockTraceSampler.getSamplingRate()) doReturn fakeTracingSamplingRate
    }

    @AfterEach
    override fun `tear down`() {
        super.`tear down`()
        Datadog.globalSdkCore = NoOpSdkCore()
    }

    @Test
    fun `M notify monitor W init()`() {
        // Given

        // When

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
    }

    @Test
    fun `M instantiate with default values W init() { no tracing hosts specified }`() {
        // When
        val interceptor = DatadogInterceptor()

        // Then
        assertThat(interceptor.tracedHosts).isEmpty()
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(RateBasedSampler::class.java)
        val traceSampler = interceptor.traceSampler as RateBasedSampler
        assertThat(traceSampler.sampleRate).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLING_RATE / 100
        )
    }

    @Test
    fun `M instantiate with default values W init() { traced hosts specified }`(
        @StringForgery(regex = "[a-z]+\\.[a-z]{3}") hosts: List<String>
    ) {
        // When
        val interceptor = DatadogInterceptor(hosts)

        // Then
        assertThat(interceptor.tracedHosts.keys).containsAll(hosts)
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(RateBasedSampler::class.java)
        val traceSampler = interceptor.traceSampler as RateBasedSampler
        assertThat(traceSampler.sampleRate).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLING_RATE / 100
        )
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId,
            RumAttributes.RULE_PSR to fakeTracingSamplingRate
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
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
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request + not sampled}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
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
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request empty response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .body(ResponseBody.create(fakeMediaType, ""))
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
                .build()
        }
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId,
            RumAttributes.RULE_PSR to fakeTracingSamplingRate
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                emptyMap()
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                null,
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request empty response + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .body(ResponseBody.create(fakeMediaType, ""))
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
                .build()
        }
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            verify(rumMonitor.mockInstance).startResource(
                requestId,
                fakeMethod,
                fakeUrl,
                emptyMap()
            )
            verify(rumMonitor.mockInstance).stopResource(
                requestId,
                statusCode,
                null,
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {successful request throwing response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = fakeMediaType

                    override fun contentLength(): Long = fakeResponseBody.length.toLong()

                    override fun source(): BufferedSource {
                        return mock<BufferedSource>().apply {
                            whenever(this.request(any())) doThrow IOException()
                        }
                    }
                })
                .build()
        }
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId,
            RumAttributes.RULE_PSR to fakeTracingSamplingRate
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
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
                null,
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {success request throwing response + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type().orEmpty())
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = fakeMediaType

                    override fun contentLength(): Long = fakeResponseBody.length.toLong()

                    override fun source(): BufferedSource {
                        return mock<BufferedSource>().apply {
                            whenever(this.request(any())) doThrow IOException()
                        }
                    }
                })
                .build()
        }
        val expectedStartAttrs = emptyMap<String, Any?>()
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
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
                null,
                kind,
                expectedStopAttrs
            )
        }
    }

    @Test
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {failing request}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceId,
            RumAttributes.SPAN_ID to fakeSpanId,
            RumAttributes.RULE_PSR to fakeTracingSamplingRate
        ) + fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
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
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {failing request + not sampled}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample()).thenReturn(false)
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val requestId = identifyRequest(fakeRequest)
        val mimeType = fakeMediaType?.type()
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
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
    fun `𝕄 start and stop RUM Resource 𝕎 intercept() {throwing request}`(
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
                fakeAttributes
            )
        }
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext, coreFeature, rumMonitor)
        }
    }
}
