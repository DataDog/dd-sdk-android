/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.utils.toBase64
import com.datadog.android.okhttp.trace.NoOpTracedRequestListener
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptorNotSendingSpanTest
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.resource.ResourceHeadersExtractor
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.forge.anHttpHeaderMap
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
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.Locale
import java.util.UUID

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

    private var resourceHeadersExtractor: ResourceHeadersExtractor? = null

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
            globalTracerProvider = globalTracerProvider,
            resourceHeadersExtractor = resourceHeadersExtractor
        )
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @BeforeEach
    override fun `set up`(forge: Forge) {
        resourceHeadersExtractor = null
        super.`set up`(forge)
        fakeAttributes = forge.exhaustiveAttributes()
        @Suppress("DEPRECATION")
        whenever(
            mockRumAttributesProvider.onProvideAttributes(
                any<Request>(),
                anyOrNull<Response>(),
                anyOrNull<Throwable>()
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
    fun `M report LEGACY_OKHTTP library type W onSdkInstanceReady()`() {
        // Then
        verify(rumMonitor.mockInstance).reportNetworkingLibraryType(
            InternalTelemetryEvent.ApiUsage.NetworkInstrumentation.LibraryType.LEGACY_OKHTTP
        )
    }

    @Test
    fun `M call notifyResourceHeadersTrackingConfigured W onSdkInstanceReady() { extractor uses defaults }`() {
        // Given
        resourceHeadersExtractor = ResourceHeadersExtractor.Builder().build()
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        // When
        (testedInterceptor as DatadogInterceptor).onSdkInstanceReady(rumMonitor.mockSdkCore)

        // Then
        verify(rumMonitor.mockInstance).notifyResourceHeadersTrackingConfigured(
            InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode.DEFAULT_HEADERS
        )
    }

    @Test
    fun `M call notifyResourceHeadersTrackingConfigured W onSdkInstanceReady() { extractor uses custom }`() {
        // Given
        resourceHeadersExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders("x-request-id")
            .build()
        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        // When
        (testedInterceptor as DatadogInterceptor).onSdkInstanceReady(rumMonitor.mockSdkCore)

        // Then
        verify(rumMonitor.mockInstance).notifyResourceHeadersTrackingConfigured(
            InternalTelemetryEvent.ResourceHeadersTrackingConfigured.Mode.CUSTOM
        )
    }

    @Test
    fun `M not call notifyResourceHeadersTrackingConfigured W onSdkInstanceReady() { no extractor }`() {
        // Then - default tested interceptor was instantiated in set up with resourceHeadersExtractor = null
        verify(rumMonitor.mockInstance, never()).notifyResourceHeadersTrackingConfigured(any())
    }

    @Test
    fun `M call chain proceed with a different Request that contains UUID tag W intercept()`() {
        // Given
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val request = requestCaptor.firstValue

        assertThat(request).isNotSameAs(fakeRequest)
        assertThat(requestCaptor.firstValue.tag(UUID::class.java)).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful streaming request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M remove all GraphQL headers W intercept() {request with GraphQL headers}`(
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String,
        @StringForgery fakeGraphQLVariables: String,
        @StringForgery fakeGraphQLPayload: String,
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeGraphQLType.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, fakeGraphQLVariables.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, fakeGraphQLPayload.toBase64())
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue

        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue]).isNull()

        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(cleanedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    @Test
    fun `M pass GraphQL attributes to RUM W intercept() {request with GraphQL headers}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String,
        @StringForgery fakeGraphQLVariables: String,
        @StringForgery fakeGraphQLPayload: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeGraphQLType.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue, fakeGraphQLVariables.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue, fakeGraphQLPayload.toBase64())
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

                val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    any(),
                    stopAttrsCaptor.capture()
                )

                val actualStopAttrs = stopAttrsCaptor.firstValue

                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_NAME]).isEqualTo(fakeGraphQLName)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_TYPE]).isEqualTo(fakeGraphQLType)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_VARIABLES]).isEqualTo(fakeGraphQLVariables)
                assertThat(actualStopAttrs[RumAttributes.GRAPHQL_PAYLOAD]).isEqualTo(fakeGraphQLPayload)

                fakeAttributes.forEach { (key, value) ->
                    assertThat(actualStopAttrs[key]).isEqualTo(value)
                }

                assertThat(firstValue).isEqualTo(secondValue)
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request, unknown method}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeMethod: String
    ) {
        // Given
        fakeRequest = forgeRequest {
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request empty response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode) {
            body("".toResponseBody(fakeMediaType))
            header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request null response body}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode) {
            header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
            body(null)
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request empty response + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain, statusCode) {
            body("".toResponseBody(fakeMediaType))
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request null response body + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain, statusCode) {
            header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
            body(null)
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {successful request throwing response}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode) {
            header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
            body(object : ResponseBody() {
                override fun contentType(): MediaType? = fakeMediaType

                override fun contentLength(): Long = -1L

                override fun source(): BufferedSource {
                    val buffer = Buffer()
                    return spy(buffer).apply {
                        whenever(request(any())) doThrow IOException()
                    }
                }
            })
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    @Test
    fun `M start and stop RUM Resource W intercept() {success request throwing response + !smp}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        stubChain(mockChain, statusCode) {
            header(TracingInterceptor.HEADER_CT, fakeMediaType?.type.orEmpty())
            body(object : ResponseBody() {
                override fun contentType(): MediaType? = fakeMediaType

                override fun contentLength(): Long = -1

                override fun source(): BufferedSource {
                    val buffer = Buffer()
                    return spy(buffer).apply {
                        whenever(request(any())) doThrow IOException()
                    }
                }
            })
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
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
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    // region graphQL headers

    @Test
    fun `M pass request unchanged W intercept() {request without GraphQL headers}`(
        @StringForgery fakeUserAgent: String,
        @StringForgery fakeCustomHeader: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
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
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue

        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(cleanedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    @Test
    fun `M remove GraphQL headers with empty values W intercept() {request with empty GraphQL headers}`(
        @StringForgery fakeUserAgent: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
            builder.addHeader("User-Agent", fakeUserAgent)
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, "".toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, "".toBase64())
        }
        stubChain(mockChain, 200)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())
        val cleanedRequest = requestCaptor.firstValue

        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers[GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue]).isNull()
        assertThat(cleanedRequest.headers["User-Agent"]).isEqualTo(fakeUserAgent)
        assertThat(cleanedRequest.url.toString()).isEqualTo(fakeRequest.url.toString())
    }

    // endregion

    // region GraphQL attributes when not sampled

    @Test
    fun `M not pass GraphQL attributes W intercept() {request with GraphQL headers + not sampled}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String
    ) {
        // Given
        whenever(mockTraceSampler.sample(any())).thenReturn(false)
        fakeRequest = forgeRequest { builder ->
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeGraphQLType.toBase64())
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

                val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
                verify(rumMonitor.mockInstance).stopResource(
                    capture(),
                    eq(statusCode),
                    eq(fakeResponseBody.toByteArray().size.toLong()),
                    any(),
                    stopAttrsCaptor.capture()
                )

                // Neither GraphQL nor trace attributes should be present when not sampled
                assertThat(stopAttrsCaptor.firstValue.keys).doesNotContainAnyElementsOf(
                    listOf(
                        RumAttributes.GRAPHQL_OPERATION_NAME,
                        RumAttributes.GRAPHQL_OPERATION_TYPE,
                        RumAttributes.TRACE_ID,
                        RumAttributes.SPAN_ID
                    )
                )

                assertThat(firstValue).isEqualTo(secondValue)
                assertThat(firstValue.uuid).isEqualTo(secondValue.uuid).isNotNull
            }
        }
    }

    // endregion

    // region GraphQL errors

    @Test
    fun `M pass GraphQL errors W intercept() {GraphQL response with errors}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeGraphQLType.toBase64())
        }
        val graphQLResponseBody = """{"data":null,"errors":[{"message":"Something went wrong"}]}"""
        stubChain(mockChain, statusCode) {
            body(graphQLResponseBody.toResponseBody(HttpSpec.ContentType.APPLICATION_JSON.toMediaType()))
            header(TracingInterceptor.HEADER_CT, HttpSpec.ContentType.APPLICATION_JSON)
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<ResourceId> {
            val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
            verify(rumMonitor.mockInstance).stopResource(
                capture(),
                eq(statusCode),
                any(),
                any(),
                stopAttrsCaptor.capture()
            )

            val actualStopAttrs = stopAttrsCaptor.firstValue
            assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_NAME]).isEqualTo(fakeGraphQLName)
            assertThat(actualStopAttrs[RumAttributes.GRAPHQL_OPERATION_TYPE]).isEqualTo(fakeGraphQLType)
            assertThat(actualStopAttrs[RumAttributes.GRAPHQL_ERRORS]).isNotNull
            assertThat(actualStopAttrs[RumAttributes.GRAPHQL_ERRORS] as? String).contains("Something went wrong")
        }
    }

    @Test
    fun `M not pass GraphQL errors W intercept() {GraphQL response without errors}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String
    ) {
        // Given
        fakeRequest = forgeRequest { builder ->
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue, fakeGraphQLName.toBase64())
            builder.addHeader(GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue, fakeGraphQLType.toBase64())
        }
        val graphQLResponseBody = """{"data":{"user":{"name":"John"}}}"""
        stubChain(mockChain, statusCode) {
            body(graphQLResponseBody.toResponseBody(HttpSpec.ContentType.APPLICATION_JSON.toMediaType()))
            header(TracingInterceptor.HEADER_CT, HttpSpec.ContentType.APPLICATION_JSON)
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<ResourceId> {
            val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
            verify(rumMonitor.mockInstance).stopResource(
                capture(),
                eq(statusCode),
                any(),
                any(),
                stopAttrsCaptor.capture()
            )

            assertThat(stopAttrsCaptor.firstValue).doesNotContainKey(RumAttributes.GRAPHQL_ERRORS)
        }
    }

    @Test
    fun `M not pass GraphQL errors W intercept() {non-GraphQL request}`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        // No GraphQL headers on fakeRequest
        val responseBody = """{"errors":[{"message":"Something went wrong"}]}"""
        stubChain(mockChain, statusCode) {
            body(responseBody.toResponseBody("application/json".toMediaType()))
            header(TracingInterceptor.HEADER_CT, "application/json")
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        argumentCaptor<ResourceId> {
            val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
            verify(rumMonitor.mockInstance).stopResource(
                capture(),
                eq(statusCode),
                any(),
                any(),
                stopAttrsCaptor.capture()
            )

            assertThat(stopAttrsCaptor.firstValue).doesNotContainKey(RumAttributes.GRAPHQL_ERRORS)
        }
    }

    // endregion

    // region Resource Headers

    @Test
    fun `M include header attributes in stopResource W intercept() { trackResourceHeaders enabled }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        resourceHeadersExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders("x-request-id")
            .build()

        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        fakeRequest = forgeRequest { it.addHeader("X-Request-Id", "abc-123") }
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val keyCaptor = argumentCaptor<ResourceId>()
        val attrsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(rumMonitor.mockInstance).stopResource(
            keyCaptor.capture(),
            any(),
            anyOrNull(),
            any(),
            attrsCaptor.capture()
        )
        @Suppress("UNCHECKED_CAST")
        val reqHeaders = attrsCaptor.firstValue[RumAttributes.REQUEST_HEADERS] as? Map<String, String>
        assertThat(reqHeaders).isNotNull
        assertThat(reqHeaders).containsEntry("x-request-id", "abc-123")
    }

    @Test
    fun `M not include header attributes in stopResource W intercept() { no trackResourceHeaders }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val keyCaptor = argumentCaptor<ResourceId>()
        val attrsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(rumMonitor.mockInstance).stopResource(
            keyCaptor.capture(),
            any(),
            anyOrNull(),
            any(),
            attrsCaptor.capture()
        )
        assertThat(attrsCaptor.firstValue).doesNotContainKey(RumAttributes.REQUEST_HEADERS)
        assertThat(attrsCaptor.firstValue).doesNotContainKey(RumAttributes.RESPONSE_HEADERS)
    }

    @Test
    fun `M capture response headers W intercept() { trackResourceHeaders enabled }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        resourceHeadersExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders("x-cache")
            .build()

        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        fakeRequest = forgeRequest()
        fakeResponseBody = forge.anAlphabeticalString()
        fakeMediaType = "text/plain".toMediaType()
        stubChain(mockChain, statusCode) {
            header("X-Cache", "HIT")
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val keyCaptor = argumentCaptor<ResourceId>()
        val attrsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(rumMonitor.mockInstance).stopResource(
            keyCaptor.capture(),
            any(),
            anyOrNull(),
            any(),
            attrsCaptor.capture()
        )
        @Suppress("UNCHECKED_CAST")
        val resHeaders = attrsCaptor.firstValue[RumAttributes.RESPONSE_HEADERS] as? Map<String, String>
        assertThat(resHeaders).isNotNull
        assertThat(resHeaders).containsEntry("x-cache", "HIT")
    }

    @Test
    fun `M header attributes override provider attributes W intercept() { conflicting keys }`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        val fakeProviderAttributes = mapOf(
            RumAttributes.REQUEST_HEADERS to forge.anHttpHeaderMap(),
            RumAttributes.RESPONSE_HEADERS to forge.anHttpHeaderMap()
        )

        @Suppress("DEPRECATION")
        whenever(
            mockRumAttributesProvider.onProvideAttributes(
                any<Request>(),
                anyOrNull<Response>(),
                anyOrNull<Throwable>()
            )
        ) doReturn fakeProviderAttributes

        val fakeActualRequestHeaders = forge.anHttpHeaderMap()
        val fakeActualResponseHeaders = forge.anHttpHeaderMap()

        resourceHeadersExtractor = ResourceHeadersExtractor.Builder(includeDefaults = false)
            .captureHeaders(*(fakeActualResponseHeaders.keys + fakeActualRequestHeaders.keys).toTypedArray())
            .build()

        testedInterceptor = instantiateTestedInterceptor(fakeLocalHosts) { _, _ -> mockLocalTracer }

        fakeRequest = forgeRequest { builder ->
            fakeActualRequestHeaders.forEach { (key, value) -> builder.addHeader(key, value) }
        }
        stubChain(mockChain, statusCode) {
            fakeActualResponseHeaders.forEach { (key, value) -> header(key, value) }
        }

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        val attrsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(rumMonitor.mockInstance).stopResource(
            any<ResourceId>(),
            any(),
            anyOrNull(),
            any(),
            attrsCaptor.capture()
        )
        @Suppress("UNCHECKED_CAST")
        val reqHeaders = attrsCaptor.firstValue[RumAttributes.REQUEST_HEADERS] as? Map<String, String>
        assertThat(reqHeaders).isNotNull
        assertThat(reqHeaders).isEqualTo(fakeActualRequestHeaders)

        @Suppress("UNCHECKED_CAST")
        val resHeaders = attrsCaptor.firstValue[RumAttributes.RESPONSE_HEADERS] as? Map<String, String>
        assertThat(resHeaders).isNotNull
        assertThat(resHeaders).isEqualTo(fakeActualResponseHeaders)
    }

    // endregion
}
