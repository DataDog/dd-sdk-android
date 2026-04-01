/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptorNotSendingSpanTest
import com.datadog.android.okhttp.trace.newAgentPropagationMock
import com.datadog.android.okhttp.trace.newSpanBuilderMock
import com.datadog.android.okhttp.trace.newSpanContextMock
import com.datadog.android.okhttp.trace.newSpanMock
import com.datadog.android.okhttp.trace.newTracerMock
import com.datadog.android.rum.NoOpRumResourceAttributesProvider
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Reproduction tests for RUMS-5093: Incorrect Timing and Ordering of Traces.
 *
 * Issue 1 (context propagation break): When DatadogInterceptor (application interceptor) AND a
 * network-level TracingInterceptor are both installed on the same OkHttpClient, the network
 * interceptor creates a child span Y (parentId=X) and overwrites x-datadog-parent-id=Y.
 * DatadogInterceptor.handleResponse() still stores X as RumAttributes.SPAN_ID.
 * The platform synthesises android.request with spanId=X, but backend spans have parentId=Y.
 * The trace tree is broken.
 *
 * The correct behaviour: RUM SPAN_ID must equal the x-datadog-parent-id header the backend receives.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RUMS5093Test : TracingInterceptorNotSendingSpanTest() {

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
            rumResourceAttributesProvider = NoOpRumResourceAttributesProvider(),
            traceSampler = mockTraceSampler,
            traceContextInjection = TraceContextInjection.ALL,
            redacted404ResourceName = fakeRedacted404Resources,
            localTracerFactory = localTracerFactory,
            globalTracerProvider = globalTracerProvider
        )
    }

    override fun getExpectedOrigin(): String = DatadogInterceptor.ORIGIN_RUM

    // region RUMS-5093 Issue 1: dual-interceptor context propagation break

    /**
     * FAILING TEST — proves Issue 1 of RUMS-5093.
     *
     * Scenario: DatadogInterceptor (application interceptor) is used with a network-level
     * TracingInterceptor on the same OkHttpClient.
     *
     * The DatadogInterceptor injects x-datadog-parent-id=X (appSpanId) into outgoing request
     * headers, then stores X as RumAttributes.SPAN_ID when the response arrives.
     *
     * A network-level TracingInterceptor (simulated here by mock chain behaviour) reads X from the
     * request headers, creates a child span with spanId=Y and parentId=X, and overwrites the header:
     * x-datadog-parent-id=Y.
     *
     * The backend therefore records backend spans with parentId=Y. The Datadog platform synthesises
     * android.request with spanId=X. Since no span with spanId=Y exists (the network interceptor
     * span is dropped by canSendSpan()), the trace tree is disconnected.
     *
     * CORRECT BEHAVIOUR: RumAttributes.SPAN_ID must equal the x-datadog-parent-id value
     * that is actually received by the backend.
     *
     * This test currently FAILS because the code stores spanId=X in RUM while the header reaching
     * the backend would have been overwritten to Y by the network interceptor.
     */
    @Test
    fun `M store SPAN_ID matching backend header W intercept() {network TracingInterceptor overwrites x-datadog-parent-id}`(
        @IntForgery(min = 200, max = 300) statusCode: Int,
        @LongForgery(min = 1) appSpanId: Long,
        @LongForgery(min = 1) networkChildSpanId: Long,
        forge: Forge
    ) {
        // Given
        // Application interceptor creates span X
        val appSpanIdStr = java.lang.Long.toUnsignedString(appSpanId)
        val appSpanContext: DatadogSpanContext = forge.newSpanContextMock(fakeSpanId = appSpanId)
        val appSpan: DatadogSpan = forge.newSpanMock(appSpanContext)
        val appSpanBuilder: DatadogSpanBuilder = forge.newSpanBuilderMock(appSpan, appSpanContext)
        val appPropagation = newAgentPropagationMock()
        // Ensure the sampler returns true for our custom appSpan so isSampled=true in interceptAndTrace
        whenever(mockTraceSampler.sample(appSpan)) doReturn true

        // Mock the application interceptor's propagation to inject appSpanId as x-datadog-parent-id
        doAnswer { invocation ->
            val requestBuilder = invocation.getArgument<Request.Builder>(1)
            val setter = invocation.getArgument<(Request.Builder, String, String) -> Unit>(2)
            setter.invoke(requestBuilder, TracingInterceptor.DATADOG_SPAN_ID_HEADER, appSpanIdStr)
            setter.invoke(
                requestBuilder,
                TracingInterceptor.DATADOG_LEAST_SIGNIFICANT_64_BITS_TRACE_ID_HEADER,
                fakeTraceIdAsString
            )
            setter.invoke(requestBuilder, TracingInterceptor.DATADOG_SAMPLING_PRIORITY_HEADER, "1")
        }.whenever(appPropagation)
            .inject(any<DatadogSpanContext>(), any<Request.Builder>(), any())

        val appTracer: DatadogTracer = forge.newTracerMock(appSpanBuilder, appPropagation)

        testedInterceptor = instantiateTestedInterceptor(
            tracedHosts = fakeLocalHosts,
            localTracerFactory = { _, _ -> appTracer },
            globalTracerProvider = { appTracer }
        )

        whenever(mockResolver.isFirstPartyUrl(fakeUrl.toHttpUrl())) doReturn true
        whenever(mockResolver.headerTypesForUrl(fakeUrl.toHttpUrl())) doReturn setOf(TracingHeaderType.DATADOG)
        whenever(mockChain.request()) doReturn fakeRequest

        // Simulate the network-level TracingInterceptor overwriting x-datadog-parent-id from X to Y:
        // When chain.proceed(request) is called, the network interceptor would:
        //   1. Read x-datadog-parent-id=X from the request
        //   2. Create child span Y (parentId=X)
        //   3. Inject x-datadog-parent-id=Y into the request before sending to backend
        //
        // We simulate this by having the chain return a response; the request that reached
        // "the backend" (chain.proceed argument) is captured below.
        // We then independently verify that RUM stores the same ID as what the backend gets.
        val networkChildSpanIdStr = java.lang.Long.toUnsignedString(networkChildSpanId)

        // The mock chain proceed simulates: network interceptor sees x-datadog-parent-id=X,
        // creates a child span Y, and passes a request with x-datadog-parent-id=Y to the actual
        // network layer. We simulate this by returning a response with the overwritten request:
        doAnswer { invocation ->
            val requestFromAppInterceptor = invocation.getArgument<Request>(0)
            // Verify precondition: app interceptor injected X before calling chain.proceed
            val headerBeforeNetworkInterceptor =
                requestFromAppInterceptor.header(TracingInterceptor.DATADOG_SPAN_ID_HEADER)
            check(headerBeforeNetworkInterceptor == appSpanIdStr) {
                "Test setup: expected app interceptor to have injected x-datadog-parent-id=$appSpanIdStr " +
                    "but found $headerBeforeNetworkInterceptor"
            }

            // Network interceptor overwrites x-datadog-parent-id with child span Y
            val requestToBackend = requestFromAppInterceptor.newBuilder()
                .removeHeader(TracingInterceptor.DATADOG_SPAN_ID_HEADER)
                .addHeader(TracingInterceptor.DATADOG_SPAN_ID_HEADER, networkChildSpanIdStr)
                .build()

            // Return a response (as if the backend responded to the request with Y in the header)
            Response.Builder()
                .request(requestToBackend)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("OK")
                .body("response".toResponseBody(null))
                .build()
        }.whenever(mockChain).proceed(any())

        // When
        testedInterceptor.intercept(mockChain)

        // Then — capture what SPAN_ID was stored in the RUM resource event
        val stopAttrsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(rumMonitor.mockInstance).stopResource(
            any<ResourceId>(),
            eq(statusCode),
            any(),
            any(),
            stopAttrsCaptor.capture()
        )
        val rumSpanId = stopAttrsCaptor.firstValue[RumAttributes.SPAN_ID] as? String

        // CORRECT BEHAVIOUR:
        // The SPAN_ID in the RUM resource event must equal the x-datadog-parent-id that the
        // backend actually received (networkChildSpanIdStr=Y after the network interceptor overwrite).
        //
        // CURRENT BEHAVIOUR (BUG):
        // - rumSpanId = appSpanIdStr (X), stored by DatadogInterceptor.handleResponse()
        // - x-datadog-parent-id at backend = networkChildSpanIdStr (Y, overwritten by network interceptor)
        // These are different → the trace tree is broken.
        //
        // This assertion FAILS, proving the bug.
        assertThat(rumSpanId)
            .withFailMessage(
                "RUMS-5093 Issue 1: RUM resource SPAN_ID [%s] must equal the x-datadog-parent-id " +
                    "received by the backend [%s]. " +
                    "A network-level TracingInterceptor overwrote the header from X=%s to Y=%s, " +
                    "but DatadogInterceptor.handleResponse() still stored X in RUM. " +
                    "The trace tree is broken: android.request(spanId=X) has no children because " +
                    "backend spans have parentId=Y.",
                rumSpanId,
                networkChildSpanIdStr,
                appSpanIdStr,
                networkChildSpanIdStr
            )
            .isEqualTo(networkChildSpanIdStr)
    }

    // endregion
}
