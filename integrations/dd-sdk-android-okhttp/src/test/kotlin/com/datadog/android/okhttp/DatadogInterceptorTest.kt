/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.okhttp.internal.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.okhttp.trace.DeterministicTraceSampler
import com.datadog.android.okhttp.trace.NoOpTracedRequestListener
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptorNotSendingSpanTest
import com.datadog.android.okhttp.utils.verifyLog
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class DatadogInterceptorTest : TracingInterceptorNotSendingSpanTest() {

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    @FloatForgery(0f, 100f)
    var fakeTracingSampleRate: Float = 0f

    private lateinit var fakeAttributes: Map<String, Any?>

    override fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>>,
        globalTracerProvider: () -> DatadogTracer?,
        localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer
    ): TracingInterceptor {
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mock()
        whenever(rumMonitor.mockSdkCore.firstPartyHostResolver) doReturn mockResolver
        return DatadogInterceptor(
            sdkInstanceName = null,
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            rumResourceAttributesProvider = mockRumAttributesProvider,
            traceSampler = mockTraceSampler,
            traceContextInjection = TraceContextInjection.ALL,
            redacted404ResourceName = fakeRedacted404Resources,
            localTracerFactory = localTracerFactory,
            globalTracerProvider = globalTracerProvider
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
        whenever(mockTraceSampler.getSampleRate()) doReturn fakeTracingSampleRate
    }

    @Test
    fun `M notify monitor once W intercept()`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)
        testedInterceptor.intercept(mockChain)

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
    }

    @Test
    fun `M instantiate with default values W init() { no tracing hosts specified }`() {
        // When
        val interceptor = DatadogInterceptor.Builder(emptyMap()).build()

        // Then
        assertThat(interceptor.tracedHosts).isEmpty()
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(DeterministicTraceSampler::class.java)
        assertThat(interceptor.traceSampler.getSampleRate()).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLE_RATE
        )
    }

    @Test
    fun `M instantiate with default values W init() { traced hosts specified }`(
        @StringForgery(regex = "[a-z]+\\.[a-z]{3}") hosts: List<String>
    ) {
        // When
        val interceptor = DatadogInterceptor.Builder(hosts).build()

        // Then
        assertThat(interceptor.tracedHosts.keys).containsAll(hosts)
        val allHeaderTypes = interceptor.tracedHosts
            .values
            .fold(mutableSetOf<TracingHeaderType>()) { acc, tracingHeaderTypes ->
                acc.apply { this += tracingHeaderTypes }
            }
        assertThat(allHeaderTypes).isEqualTo(
            setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
        )
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(DeterministicTraceSampler::class.java)
        assertThat(interceptor.traceSampler.getSampleRate()).isEqualTo(
            TracingInterceptor.DEFAULT_TRACE_SAMPLE_RATE
        )
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request, empty response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        fakeResponseBody = ""
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(0L),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful streaming request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        forge: Forge
    ) {
        // Given
        val mimeType = forge.anElementFrom(DatadogInterceptor.STREAM_CONTENT_TYPES)
        fakeMediaType = mimeType.toMediaType()
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val kind = RumResourceKind.NATIVE

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(null),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful websocket request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery websocketHash: String
    ) {
        // Given
        stubChain(mockChain, statusCode) {
            header("Sec-WebSocket-Accept", websocketHash)
        }
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(null),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M remove all GraphQL headers W intercept() {request with GraphQL headers}`(
        forge: Forge,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String,
        @StringForgery fakeGraphQLVariables: String,
        @StringForgery fakeGraphQLPayload: String,
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forgeRequest(forge) { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_NAME_HEADER, fakeGraphQLName)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_TYPE_HEADER, fakeGraphQLType)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_VARIABLES_HEADER, fakeGraphQLVariables)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_PAYLOAD_HEADER, fakeGraphQLPayload)
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue

        // Verify GraphQL headers are removed
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_NAME_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_TYPE_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_VARIABLES_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_PAYLOAD_HEADER]).isNull()

        // Verify other headers are preserved
        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(cleanedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    @Test
    fun `M pass GraphQL attributes to RUM W intercept() {request with GraphQL headers}`(
        forge: Forge,
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String,
        @StringForgery fakeGraphQLVariables: String,
        @StringForgery fakeGraphQLPayload: String
    ) {
        // Given
        fakeRequest = forgeRequest(forge) { builder ->
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_NAME_HEADER, fakeGraphQLName)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_TYPE_HEADER, fakeGraphQLType)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_VARIABLES_HEADER, fakeGraphQLVariables)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_PAYLOAD_HEADER, fakeGraphQLPayload)
        }
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(emptyMap())
                )

                // Capture the actual attributes passed to stopResource
                val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    any(),
                    stopAttrsCaptor.capture()
                )

                val actualStopAttrs = stopAttrsCaptor.firstValue

                // Verify GraphQL attributes are present
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_NAME]).isEqualTo(fakeGraphQLName)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_TYPE]).isEqualTo(fakeGraphQLType)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_VARIABLES]).isEqualTo(fakeGraphQLVariables)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_PAYLOAD]).isEqualTo(fakeGraphQLPayload)

                // Verify fakeAttributes are included
                fakeAttributes.forEach { (key, value) ->
                    assertThat(actualStopAttrs[key]).isEqualTo(value)
                }

                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request, unknown method}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeMethod: String,
        forge: Forge
    ) {
        // Given
        fakeRequest = forgeRequest(forge) {
            it.method(fakeMethod, null)
        }
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(RumResourceMethod.GET),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            DatadogInterceptor.UNSUPPORTED_HTTP_METHOD.format(Locale.US, fakeMethod)
        )
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request + not sampled}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request empty response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .body("".toResponseBody(fakeMediaType))
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
                .build()
        }
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(emptyMap())
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(0L),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request null response body}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
                .build()
        }
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(emptyMap())
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(null),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request empty response + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .body("".toResponseBody(fakeMediaType))
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
                .build()
        }
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(emptyMap())
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(0L),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request null response body + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
                .build()
        }
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(emptyMap())
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(null),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request throwing response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = fakeMediaType

                    override fun contentLength(): Long = -1L

                    override fun source(): BufferedSource {
                        val buffer = Buffer()
                        return spy(buffer).apply {
                            whenever(request(any())) doThrow IOException()
                        }
                    }
                })
                .build()
        }
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(null),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {success request throwing response + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain) {
            Response.Builder()
                .request(fakeRequest)
                .protocol(Protocol.HTTP_2)
                .code(statusCode)
                .message("HTTP $statusCode")
                .header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = fakeMediaType

                    override fun contentLength(): Long = -1

                    override fun source(): BufferedSource {
                        val buffer = Buffer()
                        return spy(buffer).apply {
                            whenever(request(any())) doThrow IOException()
                        }
                    }
                })
                .build()
        }
        val expectedStartAttrs = emptyMap<String, Any?>()
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(null),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {failing request}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        val expectedStopAttrs = mapOf(
            RumAttributes.TRACE_ID to fakeTraceIdAsString,
            RumAttributes.SPAN_ID to fakeSpanId.toString(),
            RumAttributes.RULE_PSR to fakeTracingSampleRate / 100
        ) + fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {failing request + not sampled}`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain, statusCode)
        val expectedStartAttrs = emptyMap<String, Any?>()
        // no span -> shouldn't have trace/spans IDs
        val expectedStopAttrs = fakeAttributes
        val mimeType = fakeMediaType?.type
        val kind = when {
            mimeType != null -> RumResourceKind.fromMimeType(mimeType)
            else -> RumResourceKind.NATIVE
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    eq(kind),
                    eq(expectedStopAttrs)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {throwing request}`(
        @Forgery throwable: Throwable
    ) {
        // Given
        val expectedStartAttrs = emptyMap<String, Any?>()
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        // When
        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        // Then
        inOrder(rumMonitor.mockInstance) {
            argumentCaptor<ResourceId> {
                verify(rumMonitor.mockInstance).startResource(
                    capture(),
                    eq(fakeMethod),
                    eq(fakeUrl),
                    eq(expectedStartAttrs)
                )
                verify(rumMonitor.mockInstance).stopResourceWithError(
                    capture(),
                    eq(null),
                    eq("OkHttp request error $fakeMethod $fakeUrl"),
                    eq(RumErrorSource.NETWORK),
                    eq(throwable),
                    eq(fakeAttributes)
                )
                assertThat(firstValue).isEqualTo(secondValue)
            }
        }
    }

    // region graphQL headers

    @Test
    fun `M pass request unchanged W intercept() {request without GraphQL headers}`(
        forge: Forge,
        @StringForgery fakeUserAgent: String,
        @StringForgery fakeCustomHeader: String
    ) {
        // Given
        fakeRequest = forgeRequest(forge) { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader("Custom-Header", fakeCustomHeader)
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val passedRequest = requestCaptor.firstValue

        assertThat(passedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(passedRequest.headers["Custom-Header"]).isEqualTo(fakeCustomHeader)
        assertThat(passedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    @Test
    fun `M remove partial GraphQL headers W intercept() {request with some GraphQL headers}`(
        forge: Forge,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forgeRequest(forge) { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_NAME_HEADER, fakeGraphQLName)
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue

        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_NAME_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_TYPE_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_VARIABLES_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_PAYLOAD_HEADER]).isNull()
        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(cleanedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    @Test
    fun `M remove GraphQL headers with empty values W intercept() {request with empty GraphQL headers}`(
        forge: Forge,
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forgeRequest(forge) { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_NAME_HEADER, "")
            builder.addHeader(DatadogInterceptor.DD_GRAPHQL_TYPE_HEADER, "")
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue

        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_NAME_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_TYPE_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_VARIABLES_HEADER]).isNull()
        assertThat(cleanedRequest.headers[DatadogInterceptor.DD_GRAPHQL_PAYLOAD_HEADER]).isNull()
        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(cleanedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    // endregion
}
