/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TraceContextInjection
import com.datadog.android.trace.TracingHeaderType
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingHeaderTypePropagationTest {

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore

        mockServer = MockWebServer()

        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalDatadogTracer.clear()
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    @Test
    fun `M inject B3 single header W call is made { B3 configured }`() {
        verifyHeadersInjected(
            TracingHeaderType.B3,
            HEADER_B3
        )
    }

    @Test
    fun `M inject B3Multi headers W call is made { B3MULTI configured }`() {
        verifyHeadersInjected(
            TracingHeaderType.B3MULTI,
            HEADER_B3_TRACE_ID,
            HEADER_B3_SPAN_ID,
            HEADER_B3_SAMPLED
        )
    }

    @Test
    fun `M inject Datadog headers W call is made { DATADOG configured }`() {
        verifyHeadersInjected(
            TracingHeaderType.DATADOG,
            HEADER_DD_TRACE_ID,
            HEADER_DD_PARENT_ID,
            HEADER_DD_SAMPLING_PRIORITY
        )
    }

    @Test
    fun `M inject TraceContext headers W call is made { TRACECONTEXT configured }`() {
        verifyHeadersInjected(
            TracingHeaderType.TRACECONTEXT,
            HEADER_TRACEPARENT,
            HEADER_TRACESTATE
        )
    }

    // region utilities

    private fun verifyHeadersInjected(
        headerType: TracingHeaderType,
        vararg expectedHeaders: String
    ) {
        // Given
        mockServer.enqueue(MockResponse())
        mockServer.start()
        val tracer = DatadogTracing.newTracerBuilder(stubSdkCore)
            .withTracingHeadersTypes(setOf(headerType))
            .withSampleRate(100.0)
            .build()
        GlobalDatadogTracer.registerIfAbsent(tracer)
        val client = OkHttpClient.Builder()
            .addInterceptor(
                TracingInterceptor.Builder(mapOf(mockServer.hostName to setOf(headerType)))
                    .setTraceContextInjection(TraceContextInjection.ALL)
                    .setSdkInstanceName(stubSdkCore.name)
                    .build()
            )
            .build()

        // When
        client.newCall(Request.Builder().url(mockServer.url("/")).build()).execute()

        // Then
        val recordedRequest = mockServer.takeRequest()
        expectedHeaders.forEach { header ->
            assertThat(recordedRequest.getHeader(header)).isNotEmpty()
        }
    }

    // endregion

    companion object {
        // B3 Single header
        private const val HEADER_B3 = "b3"

        // B3 Multi headers
        private const val HEADER_B3_TRACE_ID = "X-B3-TraceId"
        private const val HEADER_B3_SPAN_ID = "X-B3-SpanId"
        private const val HEADER_B3_SAMPLED = "X-B3-Sampled"

        // Datadog headers
        private const val HEADER_DD_TRACE_ID = "x-datadog-trace-id"
        private const val HEADER_DD_PARENT_ID = "x-datadog-parent-id"
        private const val HEADER_DD_SAMPLING_PRIORITY = "x-datadog-sampling-priority"

        // W3C TraceContext headers
        private const val HEADER_TRACEPARENT = "traceparent"
        private const val HEADER_TRACESTATE = "tracestate"
    }
}
