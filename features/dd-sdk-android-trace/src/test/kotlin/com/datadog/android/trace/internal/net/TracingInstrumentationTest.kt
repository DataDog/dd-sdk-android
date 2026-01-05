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
import com.datadog.android.api.instrumentation.network.HttpRequestInfoModifier
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.trace.NetworkTracedRequestListener
import com.datadog.android.trace.NetworkTracingInstrumentation
import com.datadog.android.trace.NetworkTracingInstrumentationConfiguration
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.Tags
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.withMockPropagationHelper
import com.datadog.android.trace.internal.DatadogPropagationHelper
import com.datadog.android.trace.internal.DatadogTracingToolkit
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
internal class TracingInstrumentationTest {

    private lateinit var testedInstrumentation: NetworkTracingInstrumentation

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockTracerProvider: TracerProvider

    @Mock
    lateinit var mockTracer: DatadogTracer

    @Mock
    lateinit var mockSpan: DatadogSpan

    @Mock
    lateinit var mockSpanBuilder: DatadogSpanBuilder

    @Mock
    lateinit var mockSpanContext: DatadogSpanContext

    @Mock
    lateinit var mockTraceSampler: Sampler<DatadogSpan>

    @Mock
    lateinit var mockNetworkTracedRequestListener: NetworkTracedRequestListener

    @Mock
    lateinit var mockLocalFirstPartyHostResolver: DefaultFirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTracingFeature: FeatureScope

    @Mock
    lateinit var mockRumFeature: FeatureScope

    @Mock
    lateinit var mockRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockRequestModifier: HttpRequestInfoModifier

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

        datadogRegistryRegisterMethod.invoke(datadogRegistryField.get(null), null, mockSdkCore)

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeature
        whenever(mockSdkCore.firstPartyHostResolver) doReturn mock()

        whenever(mockRequestInfo.url) doReturn fakeUrl
        whenever(mockRequestInfo.method) doReturn fakeMethod
        whenever(mockRequestInfo.headers) doReturn emptyMap()
        whenever(mockRequestInfo.modify()) doReturn mockRequestModifier
        whenever(mockRequestModifier.result()) doReturn mockRequestInfo

        whenever(mockSpanBuilder.withOrigin(anyOrNull())) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.withParentContext(anyOrNull())) doReturn mockSpanBuilder
        whenever(mockSpanBuilder.start()) doReturn mockSpan

        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpan.samplingPriority) doReturn null
        whenever(mockSpan.isRootSpan) doReturn true

        whenever(mockTraceSampler.sample(mockSpan)) doReturn true
        whenever(mockTraceSampler.getSampleRate()) doReturn fakeSampleRate

        whenever(mockTracer.buildSpan(any())) doReturn mockSpanBuilder

        whenever(
            mockTracerProvider.provideTracer(
                any(),
                any(),
                any()
            )
        ) doReturn mockTracer

        whenever(mockLocalFirstPartyHostResolver.isFirstPartyUrl(fakeUrl)) doReturn true
        whenever(mockLocalFirstPartyHostResolver.headerTypesForUrl(fakeUrl)) doReturn setOf(TracingHeaderType.DATADOG)
        whenever(mockLocalFirstPartyHostResolver.getAllHeaderTypes()) doReturn setOf(TracingHeaderType.DATADOG)
        whenever(mockLocalFirstPartyHostResolver.isEmpty()) doReturn false

        // Set up mock propagation helper to return null for sampling decision (falls through to sampler)
        whenever(mockPropagationHelper.extractSamplingDecision(any())) doReturn null

        testedInstrumentation = NetworkTracingInstrumentation(
            sdkInstanceName = null,
            traceOrigin = null,
            tracerProvider = mockTracerProvider,
            redacted404ResourceName = true,
            traceSampler = mockTraceSampler,
            injectionType = TraceContextInjection.ALL,
            tracedRequestListener = mockNetworkTracedRequestListener,
            localFirstPartyHostHeaderTypeResolver = mockLocalFirstPartyHostResolver,
            networkInstrumentationName = fakeNetworkInstrumentationName,
            rumApmLinkingEnabled = true,
            networkInstrumentationEnabled = true
        )
    }

    @AfterEach
    fun `tear down`() {
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))
    }

    // region onRequest

    @Test
    fun `M return RequestTraceState with span W onRequest() {traceable first party url}`() {
        // Given
        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            assertThat(result.span).isEqualTo(mockSpan)
            assertThat(result.isSampled).isTrue()
            assertThat(result.sampleRate).isEqualTo(fakeSampleRate)
            assertThat(result.rumApmLinkingEnabled).isTrue()
        }
    }

    @Test
    fun `M set span resource name W onRequest()`() {
        // Given
        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockSpan).resourceName = fakeUrl.substringBefore('?')
        }
    }

    @Test
    fun `M set span tags W onRequest()`() {
        // Given
        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
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

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M return RequestTraceState without span W onRequest() {network instrumentation disabled}`() {
        // Given
        testedInstrumentation = NetworkTracingInstrumentation(
            sdkInstanceName = null,
            traceOrigin = null,
            tracerProvider = mockTracerProvider,
            redacted404ResourceName = true,
            traceSampler = mockTraceSampler,
            injectionType = TraceContextInjection.ALL,
            tracedRequestListener = mockNetworkTracedRequestListener,
            localFirstPartyHostHeaderTypeResolver = mockLocalFirstPartyHostResolver,
            networkInstrumentationName = fakeNetworkInstrumentationName,
            rumApmLinkingEnabled = true,
            networkInstrumentationEnabled = false
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M return RequestTraceState without span W onRequest() {tracer not available}`() {
        // Given
        whenever(mockTracerProvider.provideTracer(any(), any(), any())) doReturn null

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M return not sampled state W onRequest() {span not sampled}`() {
        // Given
        whenever(mockTraceSampler.sample(mockSpan)) doReturn false

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            assertThat(result.span).isEqualTo(mockSpan)
            assertThat(result.isSampled).isFalse()
        }
    }

    @Test
    fun `M propagate sampled headers W onRequest() {sampled}`() {
        // Given
        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockPropagationHelper).propagateSampledHeaders(
                eq(mockRequestModifier),
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

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockPropagationHelper).propagateNotSampledHeaders(
                eq(mockRequestModifier),
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

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            verify(mockGlobalResolver).headerTypesForUrl(fakeUrl)
        }
    }

    // endregion

    // region onResponseSucceed

    @Test
    fun `M set http status code tag W onResponseSucceed()`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).setTag(Tags.KEY_HTTP_STATUS, fakeStatusCode)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 499])
    fun `M mark span as error W onResponseSucceed() {4xx status code}`(
        fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).isError = true
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 201, 204, 301, 302, 500, 502, 503])
    fun `M not mark span as error W onResponseSucceed() {non-4xx status code}`(
        fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan, never()).isError = true
        }
    }

    @Test
    fun `M redact 404 resource name W onResponseSucceed() {404 status code, redaction enabled}`() {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn HttpURLConnection.HTTP_NOT_FOUND

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).resourceName = NetworkTracingInstrumentation.RESOURCE_NAME_404
        }
    }

    @Test
    fun `M not redact 404 resource name W onResponseSucceed() {404 status code, redaction disabled}`() {
        // Given
        testedInstrumentation = NetworkTracingInstrumentation(
            sdkInstanceName = null,
            traceOrigin = null,
            tracerProvider = mockTracerProvider,
            redacted404ResourceName = false,
            traceSampler = mockTraceSampler,
            injectionType = TraceContextInjection.ALL,
            tracedRequestListener = mockNetworkTracedRequestListener,
            localFirstPartyHostHeaderTypeResolver = mockLocalFirstPartyHostResolver,
            networkInstrumentationName = fakeNetworkInstrumentationName,
            rumApmLinkingEnabled = true,
            networkInstrumentationEnabled = true
        )

        whenever(mockResponseInfo.statusCode) doReturn HttpURLConnection.HTTP_NOT_FOUND

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan, never()).resourceName = NetworkTracingInstrumentation.RESOURCE_NAME_404
        }
    }

    @Test
    fun `M call traced request listener W onResponseSucceed() {sampled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

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
    fun `M not call traced request listener W onResponseSucceed() {not sampled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verifyNoInteractions(mockNetworkTracedRequestListener)
        }
    }

    @Test
    fun `M finish span W onResponseSucceed() {sampled, RUM disabled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).finish()
        }
    }

    @Test
    fun `M drop span W onResponseSucceed() {not sampled, RUM disabled}`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).drop()
        }
    }

    // endregion

    // region onResponseFailed

    @Test
    fun `M mark span as error W onResponseFailed()`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
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

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).setTag(Tags.KEY_ERROR_MSG, fakeErrorMessage)
            verify(mockSpan).setTag(Tags.KEY_ERROR_TYPE, IOException::class.java.name)
            verify(mockSpan).setTag(eq(Tags.KEY_ERROR_STACK), any<String>())
        }
    }

    @Test
    fun `M call traced request listener W onResponseFailed() {sampled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
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
    fun `M not call traced request listener W onResponseFailed() {not sampled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verifyNoInteractions(mockNetworkTracedRequestListener)
        }
    }

    @Test
    fun `M not set error tags W onResponseFailed() {not sampled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
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
    fun `M finish span W onResponseFailed() {sampled, RUM disabled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).finish()
        }
    }

    @Test
    fun `M drop span W onResponseFailed() {not sampled, RUM disabled}`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).drop()
        }
    }

    // endregion

    // region internalSdkCore null scenarios

    @Test
    fun `M return RequestTraceState without span W onRequest() { sdkCore not registered }`() {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            val result = testedInstrumentation.onRequest(mockRequestInfo)

            // Then
            assertThat(result.span).isNull()
            assertThat(result.isSampled).isFalse()
            verifyNoInteractions(mockTracerProvider)
        }
    }

    @Test
    fun `M finish span W onResponseSucceed() { sdkCore not registered, sampled }`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        // When sdkCore is null, isRumEnabled returns false
        // Since !isRumEnabled = true, canSendSpan = true
        // With isSampled = true, span.finish() should be called
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).finish()
        }
    }

    @Test
    fun `M drop span W onResponseSucceed() { sdkCore not registered, not sampled }`(
        @IntForgery(min = 200, max = 299) fakeStatusCode: Int
    ) {
        // Given
        whenever(mockResponseInfo.statusCode) doReturn fakeStatusCode
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseSucceed(traceState, mockResponseInfo)

            // Then
            verify(mockSpan).drop()
        }
    }

    @Test
    fun `M finish span W onResponseFailed() { sdkCore not registered, sampled }`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = true,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).finish()
        }
    }

    @Test
    fun `M drop span W onResponseFailed() { sdkCore not registered, not sampled }`(
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        datadogRegistryClearMethod.invoke(datadogRegistryField.get(null))

        val traceState = RequestTraceState(
            requestModifier = mockRequestModifier,
            isSampled = false,
            span = mockSpan,
            sampleRate = fakeSampleRate,
            rumApmLinkingEnabled = true
        )

        DatadogTracingToolkit.withMockPropagationHelper(mockPropagationHelper) {
            // When
            testedInstrumentation.onResponseFailed(traceState, fakeThrowable)

            // Then
            verify(mockSpan).drop()
        }
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
        val builder = NetworkTracingInstrumentation.Configuration(fakeHosts)

        // Then
        assertThat(builder).isInstanceOf(NetworkTracingInstrumentationConfiguration::class.java)
    }

    @Test
    fun `M create builder with default headers W Builder() {with hosts list}`(forge: Forge) {
        // Given
        val fakeHosts = forge.aList { forge.aStringMatching("[a-z]+\\.[a-z]{2,3}") }

        // When
        val builder = NetworkTracingInstrumentation.Configuration(fakeHosts)

        // Then
        assertThat(builder).isInstanceOf(NetworkTracingInstrumentationConfiguration::class.java)
    }

    // endregion

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
