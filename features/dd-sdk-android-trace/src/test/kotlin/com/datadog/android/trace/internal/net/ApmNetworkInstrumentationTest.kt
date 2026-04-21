/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.net

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.DeterministicTraceSampler
import com.datadog.android.trace.NetworkTracedRequestListener
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.Tags
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.withMockPropagationHelper
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.DatadogPropagationHelper
import com.datadog.android.trace.internal._TraceInternalProxy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.HttpURLConnection

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ApmNetworkInstrumentationTest {

    private lateinit var testedInstrumentation: ApmNetworkInstrumentation

    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockTracerProvider: TracerProvider

    @Mock
    lateinit var mockTracer: DatadogTracer

    lateinit var mockSpan: DatadogSpan

    lateinit var mockSpanBuilder: DatadogSpanBuilder

    @Mock
    lateinit var mockSpanContext: DatadogSpanContext

    lateinit var mockTraceSampler: Sampler<DatadogSpan>

    @Mock
    lateinit var mockNetworkTracedRequestListener: NetworkTracedRequestListener

    lateinit var mockLocalFirstPartyHostResolver: DefaultFirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTracingFeature: FeatureScope

    @Mock
    lateinit var mockRumFeature: FeatureScope

    lateinit var mockRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockRequestBuilder: HttpRequestInfoBuilder

    @Mock
    lateinit var mockResponseInfo: HttpResponseInfo

    @Mock
    lateinit var mockPropagationHelper: DatadogPropagationHelper

    @StringForgery
    lateinit var fakeNetworkInstrumentationName: String

    @StringForgery(regex = "https://[a-z]+\\.[a-z]{2,3}/[a-z]+")
    lateinit var fakeUrl: String

    @StringForgery
    lateinit var fakeMethod: String

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    private lateinit var fakeTracedHosts: Map<String, Set<TracingHeaderType>>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTracedHosts = forge.aMap {
            aStringMatching("[a-z]+\\.[a-z]{2,3}") to forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        }

        mockSdkCore = mock {
            on { internalLogger } doReturn mockInternalLogger
            on { getFeature(Feature.TRACING_FEATURE_NAME) } doReturn mockTracingFeature
            on { getFeature(Feature.RUM_FEATURE_NAME) } doReturn mockRumFeature
            on { firstPartyHostResolver } doReturn mock()
        }
        datadogRegistryRegisterMethod.invoke(datadogRegistryField.get(null), null, mockSdkCore)

        mockRequestInfo = mock(extraInterfaces = arrayOf(MutableHttpRequestInfo::class)) {
            on { url } doReturn fakeUrl
            on { method } doReturn fakeMethod
            on { headers } doReturn emptyMap()
        }

        whenever((mockRequestInfo as MutableHttpRequestInfo).newBuilder()) doReturn mockRequestBuilder
        whenever(mockRequestBuilder.build()) doReturn mockRequestInfo

        mockSpan = mock {
            on { isRootSpan } doReturn true
            on { samplingPriority } doReturn null
            on { context() } doReturn mockSpanContext
        }

        mockSpanBuilder = mock {
            on { withOrigin(anyOrNull()) } doAnswer { mockSpanBuilder }
            on { withParentContext(anyOrNull()) } doAnswer { mockSpanBuilder }
            on { start() } doReturn mockSpan
        }
        whenever(mockTracer.buildSpan(any())) doReturn mockSpanBuilder

        mockTraceSampler = mock {
            on { sample(mockSpan) } doReturn true
            on { getSampleRate() } doReturn fakeSampleRate
        }

        whenever(
            mockTracerProvider.provideTracer(
                any(),
                any(),
                any()
            )
        ) doReturn mockTracer

        mockLocalFirstPartyHostResolver = mock {
            on { isFirstPartyUrl(fakeUrl) } doReturn true
            on { headerTypesForUrl(fakeUrl) } doReturn setOf(TracingHeaderType.DATADOG)
            on { getAllHeaderTypes() } doReturn setOf(TracingHeaderType.DATADOG)
            on { isEmpty() } doReturn false
        }

        // Set up mock propagation helper to return null for sampling decision (falls through to sampler)
        whenever(mockPropagationHelper.extractSamplingDecision(any())) doReturn null

        testedInstrumentation = createInstrumentation()
    }

    @AfterEach
    fun `tear down`() {
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))
    }

    // region onRequest

    @Test
    fun `M return RequestTraceState with span W onRequest() {traceable first party url}`() {
        // Given
        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            assertThat(result.span).isEqualTo(mockSpan)
            assertThat(result.isSampled).isTrue()
            assertThat(result.sampleRate).isEqualTo(fakeSampleRate)
        }
    }

    @Test
    fun `M use rebased trace sample rate in tracing state W onRequest() {session sample rate tag present}`(
        @FloatForgery(min = 0f, max = 100f) fakeTraceSampleRate: Float,
        @FloatForgery(min = 0f, max = 99f) fakeSessionSampleRate: Float
    ) {
        // Given
        val traceId = mock<DatadogTraceId> {
            on { toLong() } doReturn 1L
        }
        whenever(mockSpanContext.traceId).thenReturn(traceId)
        whenever(mockSpanContext.tags).thenReturn(
            mapOf(LogAttributes.RUM_SESSION_SAMPLE_RATE to fakeSessionSampleRate)
        )
        whenever(mockSpanContext.setSamplingPriority(any())).thenReturn(true)

        testedInstrumentation = createInstrumentation(
            traceSampler = DeterministicTraceSampler(fakeTraceSampleRate)
        )

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            val expectedRebasedSampleRate = fakeTraceSampleRate * fakeSessionSampleRate /
                ApmNetworkInstrumentation.ALL_IN_SAMPLE_RATE.toFloat()
            assertThat(result.sampleRate).isEqualTo(expectedRebasedSampleRate)
        }
    }

    @Test
    fun `M set rebased trace sample rate metric W onRequest() {session sample rate tag present}`(
        @FloatForgery(min = 0f, max = 100f) fakeTraceSampleRate: Float,
        @FloatForgery(min = 0f, max = 99f) fakeSessionSampleRate: Float
    ) {
        // Given
        val traceId = mock<DatadogTraceId> {
            on { toLong() } doReturn 1L
        }
        whenever(mockSpanContext.traceId).thenReturn(traceId)
        whenever(mockSpanContext.tags).thenReturn(
            mapOf(LogAttributes.RUM_SESSION_SAMPLE_RATE to fakeSessionSampleRate)
        )
        whenever(mockSpanContext.setSamplingPriority(any())).thenReturn(true)

        testedInstrumentation = createInstrumentation(
            traceSampler = DeterministicTraceSampler(fakeTraceSampleRate)
        )

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            val expectedRebasedSampleRate = fakeTraceSampleRate * fakeSessionSampleRate /
                ApmNetworkInstrumentation.ALL_IN_SAMPLE_RATE.toFloat()
            verify(mockSpanContext).setMetric(
                eq(ApmNetworkInstrumentation.AGENT_PSR_ATTRIBUTE),
                eq(expectedRebasedSampleRate.toDouble() / 100.0)
            )
        }
    }

    @Test
    fun `M set span resource name W onRequest()`() {
        // Given
        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockSpan).resourceName = fakeUrl.substringBefore('?')
        }
    }

    @Test
    fun `M set span tags W onRequest()`() {
        // Given
        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockSpan).setTag(Tags.KEY_HTTP_URL, fakeUrl)
            verify(mockSpan).setTag(Tags.KEY_HTTP_METHOD, fakeMethod)
            verify(mockSpan).setTag(Tags.KEY_SPAN_KIND, Tags.VALUE_SPAN_KIND_CLIENT)
        }
    }

    @Test
    fun `M return RequestTraceState without span W onRequest() {not first party url}`() {
        // Given
        whenever(mockLocalFirstPartyHostResolver.isFirstPartyUrl(fakeUrl)) doReturn false
        whenever(mockSdkCore.firstPartyHostResolver.isFirstPartyUrl(fakeUrl)) doReturn false

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M return RequestTraceState with span W onRequest() {EXCLUDE_INTERNAL_REDIRECTS scope}`() {
        // Given
        testedInstrumentation = createInstrumentation(
            networkTracingScope = ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS
        )

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            assertThat(result.span).isNotNull()
            assertThat(result.isSampled).isTrue()
        }
    }

    @Test
    fun `M return RequestTraceState without span W onRequest() {tracer not available}`() {
        // Given
        whenever(mockTracerProvider.provideTracer(any(), any(), any())) doReturn null

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M return not sampled state W onRequest() {span not sampled}`() {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)) doReturn false

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            assertThat(result.span).isEqualTo(mockSpan)
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M propagate sampled headers W onRequest() {sampled}`() {
        // Given
        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockPropagationHelper).propagateSampledHeaders(
                eq(mockRequestBuilder),
                eq(mockTracer),
                eq(mockSpan),
                any()
            )
        }
    }

    @Test
    fun `M propagate not sampled headers W onRequest() {not sampled}`() {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)) doReturn false

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockPropagationHelper).propagateNotSampledHeaders(
                eq(mockRequestBuilder),
                eq(mockTracer),
                eq(mockSpan),
                any(),
                eq(TraceContextInjection.ALL),
                anyOrNull()
            )
        }
    }

    @Test
    fun `M use global first party resolver W onRequest() {local resolver has no headers}`() {
        // Given
        val mockGlobalResolver: DefaultFirstPartyHostHeaderTypeResolver = mock()
        whenever(mockLocalFirstPartyHostResolver.headerTypesForUrl(fakeUrl)) doReturn emptySet()
        whenever(mockSdkCore.firstPartyHostResolver) doReturn mockGlobalResolver
        whenever(mockGlobalResolver.headerTypesForUrl(fakeUrl)) doReturn setOf(TracingHeaderType.TRACECONTEXT)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockGlobalResolver).headerTypesForUrl(fakeUrl)
        }
    }

    // endregion

    // region onResponseSucceeded

    @Test
    fun `M set http status code tag W onResponseSucceeded()`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).setTag(Tags.KEY_HTTP_STATUS, fakeStatusCode)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 499])
    fun `M mark span as error W onResponseSucceeded() {4xx status code}`(
        fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).isError = true
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 201, 204, 301, 302, 500, 502, 503])
    fun `M not mark span as error W onResponseSucceeded() {non-4xx status code}`(
        fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan, never()).isError = true
        }
    }

    @Test
    fun `M redact 404 resource name W onResponseSucceeded() {404 status code, redaction enabled}`() {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn HttpURLConnection.HTTP_NOT_FOUND

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).resourceName = ApmNetworkInstrumentation.RESOURCE_NAME_404
        }
    }

    @Test
    fun `M not redact 404 resource name W onResponseSucceeded() {404 status code, redaction disabled}`() {
        // Given
        testedInstrumentation = createInstrumentation(redacted404ResourceName = false)

        whenever(mockResponseInfo.statusCode) doReturn HttpURLConnection.HTTP_NOT_FOUND

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan, never()).resourceName = ApmNetworkInstrumentation.RESOURCE_NAME_404
        }
    }

    @Test
    fun `M call traced request listener W onResponseSucceeded()`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int,
        @BoolForgery fakeIsSampled: Boolean
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState(isSampled = fakeIsSampled)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockNetworkTracedRequestListener).onRequestIntercepted(
                mockRequestInfo,
                mockSpan,
                mockResponseInfo,
                null
            )
        }
    }

    @Test
    fun `M finish or drop span W onResponseSucceeded() {RUM disabled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int,
        @BoolForgery fakeIsSampled: Boolean
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState(isSampled = fakeIsSampled)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            if (fakeIsSampled) verify(mockSpan).finish() else verify(mockSpan).drop()
        }
    }

    // endregion

    // region onResponseFailed

    @Test
    fun `M mark span as error W onResponseFailed()`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).isError = true
        }
    }

    @Test
    fun `M set error tags W onResponseFailed()`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        val fakeThrowable = IOException(fakeErrorMessage)

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).setTag(Tags.KEY_ERROR_MSG, fakeErrorMessage)
            verify(mockSpan).setTag(Tags.KEY_ERROR_TYPE, IOException::class.java.name)
            verify(mockSpan).setTag(eq(Tags.KEY_ERROR_STACK), any<String>())
        }
    }

    @Test
    fun `M call traced request listener W onResponseFailed()`(
        @Forgery fakeThrowable: Throwable,
        @BoolForgery fakeIsSampled: Boolean
    ) {
        // Given
        val traceState = createTraceState(isSampled = fakeIsSampled)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockNetworkTracedRequestListener).onRequestIntercepted(
                mockRequestInfo,
                mockSpan,
                null,
                fakeThrowable
            )
        }
    }

    @Test
    fun `M not set error tags W onResponseFailed() {not sampled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val traceState = createTraceState(isSampled = false)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan, never()).isError = true
            verify(mockSpan, never()).setTag(eq(Tags.KEY_ERROR_MSG), any<String>())
            verify(mockSpan, never()).setTag(eq(Tags.KEY_ERROR_TYPE), any<String>())
            verify(mockSpan, never()).setTag(eq(Tags.KEY_ERROR_STACK), any<String>())
        }
    }

    @Test
    fun `M finish or drop span W onResponseFailed() {RUM disabled}`(
        @Forgery fakeThrowable: Throwable,
        @BoolForgery fakeIsSampled: Boolean
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val traceState = createTraceState(isSampled = fakeIsSampled)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            if (fakeIsSampled) verify(mockSpan).finish() else verify(mockSpan).drop()
        }
    }

    // endregion

    // region canSendSpan

    @Test
    fun `M drop span W onResponseSucceeded() {canSendSpan=false, RUM enabled, sampled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int,
        forge: Forge
    ) {
        // Given
        val fakeScope = forge.aValueFrom(ApmNetworkTracingScope::class.java)
        testedInstrumentation = createInstrumentation(canSendSpan = false, networkTracingScope = fakeScope)
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).drop()
            verify(mockSpan, never()).finish()
        }
    }

    @Test
    fun `M finish span W onResponseSucceeded() {APP_LEVEL, canSendSpan=true, RUM enabled, sampled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given - canSendSpan=true is set in setUp
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).finish()
            verify(mockSpan, never()).drop()
        }
    }

    @Test
    fun `M drop span W onResponseSucceeded() {APP_LEVEL, canSendSpan=false, RUM disabled, sampled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        testedInstrumentation = createInstrumentation(canSendSpan = false)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).drop()
            verify(mockSpan, never()).finish()
        }
    }

    @Test
    fun `M drop span W onResponseFailed() {canSendSpan=false, RUM enabled, sampled}`(
        @Forgery fakeThrowable: Throwable,
        forge: Forge
    ) {
        // Given
        val fakeScope = forge.aValueFrom(ApmNetworkTracingScope::class.java)
        testedInstrumentation = createInstrumentation(canSendSpan = false, networkTracingScope = fakeScope)

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).drop()
            verify(mockSpan, never()).finish()
        }
    }

    @Test
    fun `M finish span W onResponseFailed() {APP_LEVEL, canSendSpan=true, RUM enabled, sampled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given - canSendSpan=true is set in setUp
        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).finish()
            verify(mockSpan, never()).drop()
        }
    }

    @Test
    fun `M drop span W onResponseFailed() {APP_LEVEL, canSendSpan=false, RUM disabled, sampled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        testedInstrumentation = createInstrumentation(canSendSpan = false)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).drop()
            verify(mockSpan, never()).finish()
        }
    }

    // endregion

    // region internalSdkCore null scenarios

    @Test
    fun `M return RequestTraceState without span W onRequest() { sdkCore not registered }`() {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = checkNotNull(testedInstrumentation.onRequest(mockRequestInfo))

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
            verifyNoInteractions(mockTracerProvider)
        }
    }

    @Test
    fun `M finish or drop span W onResponseSucceeded() { sdkCore not registered }`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int,
        @BoolForgery fakeIsSampled: Boolean
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = createTraceState(isSampled = fakeIsSampled)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            if (fakeIsSampled) verify(mockSpan).finish() else verify(mockSpan).drop()
        }
    }

    @Test
    fun `M finish or drop span W onResponseFailed() { sdkCore not registered }`(
        @Forgery fakeThrowable: Throwable,
        @BoolForgery fakeIsSampled: Boolean
    ) {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = createTraceState(isSampled = fakeIsSampled)

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            if (fakeIsSampled) verify(mockSpan).finish() else verify(mockSpan).drop()
        }
    }

    @Test
    fun `M call traced request listener W onResponseSucceeded() { sdkCore not registered, sampled }`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceeded(traceState, mockResponseInfo)

            // Then
            verify(mockNetworkTracedRequestListener).onRequestIntercepted(
                mockRequestInfo,
                mockSpan,
                mockResponseInfo,
                null
            )
        }
    }

    @Test
    fun `M call traced request listener W onResponseFailed() { sdkCore not registered, sampled }`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = createTraceState()

        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockNetworkTracedRequestListener).onRequestIntercepted(
                mockRequestInfo,
                mockSpan,
                null,
                fakeThrowable
            )
        }
    }

    // endregion

    // region reportInstrumentationError

    @Test
    fun `M log warning to maintainer W reportInstrumentationError() {sdkCore registered}`(
        @StringForgery fakeMessage: String
    ) {
        // When
        testedInstrumentation.reportInstrumentationError { fakeMessage }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            fakeMessage
        )
    }

    @Test
    fun `M not log W reportInstrumentationError() {sdkCore not registered}`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        // When
        testedInstrumentation.reportInstrumentationError { fakeMessage }

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region Builder companion methods

    @Test
    fun `M create builder W Builder() {with hosts map}`(forge: Forge) {
        // Given
        val fakeHosts = forge.aMap {
            forge.aStringMatching("[a-z]+\\.[a-z]{2,3}") to forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        }

        // When
        val builder = ApmNetworkInstrumentationConfiguration(fakeHosts)

        // Then
        assertThat(builder).isInstanceOf(ApmNetworkInstrumentationConfiguration::class.java)
    }

    @Test
    fun `M create builder with default headers W Builder() {with hosts list}`(forge: Forge) {
        // Given
        val fakeHosts = forge.aList { forge.aStringMatching("[a-z]+\\.[a-z]{2,3}") }

        // When
        val builder = ApmNetworkInstrumentationConfiguration(fakeHosts)

        // Then
        assertThat(builder).isInstanceOf(ApmNetworkInstrumentationConfiguration::class.java)
    }

    // endregion

    // region properties and utility methods

    @Test
    fun `M return sample rate W sampleRate`() {
        // When / Then
        assertThat(testedInstrumentation.sampleRate).isEqualTo(fakeSampleRate)
    }

    @Test
    fun `M return local header types W localHeaderTypes`() {
        // When / Then
        assertThat(testedInstrumentation.localHeaderTypes).isEqualTo(setOf(TracingHeaderType.DATADOG))
    }

    @Test
    fun `M delegate to propagation helper W removeTracingHeaders()`() {
        // Given
        _TraceInternalProxy.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.removeTracingHeaders(mockRequestBuilder)

            // Then
            verify(mockPropagationHelper).removeAllTracingHeaders(mockRequestBuilder)
        }
    }

    // endregion

    private fun createTraceState(isSampled: Boolean = true) = RequestTracingState(
        requestInfoBuilder = mockRequestBuilder,
        isSampled = isSampled,
        span = mockSpan,
        sampleRate = fakeSampleRate
    )

    private fun createInstrumentation(
        canSendSpan: Boolean = true,
        networkTracingScope: ApmNetworkTracingScope = ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS,
        redacted404ResourceName: Boolean = true,
        traceSampler: Sampler<DatadogSpan> = mockTraceSampler
    ) = ApmNetworkInstrumentation(
        canSendSpan = canSendSpan,
        sdkInstanceName = null,
        traceOrigin = null,
        tracerProvider = mockTracerProvider,
        redacted404ResourceName = redacted404ResourceName,
        traceSampler = traceSampler,
        injectionType = TraceContextInjection.ALL,
        tracedRequestListener = mockNetworkTracedRequestListener,
        localFirstPartyHostHeaderTypeResolver = mockLocalFirstPartyHostResolver,
        networkingLibraryName = fakeNetworkInstrumentationName,
        networkTracingScope = networkTracingScope
    )

    companion object {
        private val datadogRegistryField = Datadog::class.java.getDeclaredField("registry").apply {
            isAccessible = true
        }
        private val datadogRegistryRegisterMethod = datadogRegistryField.type.getMethod(
            "register",
            String::class.java,
            SdkCore::class.java
        )
        private val datadogRegistryClearMethod = datadogRegistryField.type.getMethod("clear")
    }
}
