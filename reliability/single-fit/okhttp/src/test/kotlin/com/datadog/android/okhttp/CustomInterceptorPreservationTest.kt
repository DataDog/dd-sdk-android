/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubEvent
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.replace
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicLong

/**
 * Integration test verifying that custom interceptor modifications are preserved
 * when using [OkHttpClient.Builder.configureDatadogInstrumentation].
 *
 * Tests cover:
 * - Custom app and network interceptor headers arrive at the server
 * - RUM resource timing data is present when an upstream interceptor rewrites the URL
 */
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomInterceptorPreservationTest {

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer
    private lateinit var rumMonitor: RumMonitor

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore

        mockServer = MockWebServer()
        mockServer.start()

        // Make time.deviceTimeNs return incrementing nanoseconds so EventListener timing is non-zero
        val nanoCounter = AtomicLong(1_000_000_000L)
        whenever(stubSdkCore.time.deviceTimeNs).thenAnswer { nanoCounter.addAndGet(1_000_000_000L) }

        val fakeApplicationId = forge.anAlphabeticalString()
        Rum.enable(
            RumConfiguration.Builder(fakeApplicationId)
                .trackNonFatalAnrs(false)
                .build(),
            stubSdkCore
        )

        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)

        GlobalDatadogTracer.replace(
            DatadogTracing.newTracerBuilder(stubSdkCore)
                .withPartialFlushMinSpans(1)
        )

        rumMonitor = GlobalRumMonitor.get(stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalDatadogTracer.clear()
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    @Test
    fun `M preserve custom app interceptor headers W call is made`() {
        // Given
        mockServer.enqueue(MockResponse())
        val client = OkHttpClient.Builder()
            .withCustomHeadersInterceptor(APP_INTERCEPTOR_HEADER, APP_INTERCEPTOR_VALUE)
            .withRumAndApmInstrumentation()
            .build()

        val request = Request.Builder().url(mockServer.url("/get")).get().build()

        // When
        client.newCall(request).execute()

        // Then
        val recorded = mockServer.takeRequest()
        assertThat(recorded.getHeader(APP_INTERCEPTOR_HEADER)).isEqualTo(APP_INTERCEPTOR_VALUE)
    }

    @Test
    fun `M preserve custom network interceptor headers W call is made`() {
        // Given
        mockServer.enqueue(MockResponse())
        val client = OkHttpClient.Builder()
            .withCustomHeadersNetworkInterceptor(NETWORK_INTERCEPTOR_HEADER, NETWORK_INTERCEPTOR_VALUE)
            .withRumAndApmInstrumentation()
            .build()

        val request = Request.Builder().url(mockServer.url("/get")).get().build()

        // When
        client.newCall(request).execute()

        // Then
        val recorded = mockServer.takeRequest()
        assertThat(recorded.getHeader(NETWORK_INTERCEPTOR_HEADER)).isEqualTo(NETWORK_INTERCEPTOR_VALUE)
    }

    @Test
    fun `M preserve both custom and Datadog tracing headers W call is made`() {
        // Given
        mockServer.enqueue(MockResponse())
        val client = OkHttpClient.Builder()
            .withCustomHeadersInterceptor(APP_INTERCEPTOR_HEADER, APP_INTERCEPTOR_VALUE)
            .withCustomHeadersNetworkInterceptor(NETWORK_INTERCEPTOR_HEADER, NETWORK_INTERCEPTOR_VALUE)
            .withRumAndApmInstrumentation()
            .build()

        val request = Request.Builder().url(mockServer.url("/post")).get().build()

        // When
        client.newCall(request).execute()

        // Then
        val recorded = mockServer.takeRequest()
        assertThat(recorded.getHeader(APP_INTERCEPTOR_HEADER)).isEqualTo(APP_INTERCEPTOR_VALUE)
        assertThat(recorded.getHeader(NETWORK_INTERCEPTOR_HEADER)).isEqualTo(NETWORK_INTERCEPTOR_VALUE)
        assertThat(recorded.getHeader(DATADOG_TRACE_ID_HEADER)).isNotNull
        assertThat(recorded.getHeader(DATADOG_PARENT_ID_HEADER)).isNotNull
    }

    @Test
    fun `M have RUM resource timings W request is made { upstream interceptor rewrites URL }`() {
        // Given
        mockServer.enqueue(MockResponse())
        val rewrittenUrl = mockServer.url("/get").toString()
        val client = OkHttpClient.Builder()
            .withUrlReplacementInterceptor(rewrittenUrl)
            .withRumAndApmInstrumentation()
            .build()

        rumMonitor.startView(RUM_VIEW_KEY, RUM_VIEW_NAME)

        val request = Request.Builder()
            .url(mockServer.url("/original-path"))
            .get()
            .build()

        // When
        client.newCall(request).execute().body?.string()

        // Then
        val resourceEvents = getResourceEvents()
        assertThat(resourceEvents).isNotEmpty

        val resourceData = resourceEvents.first().asResourceJson()
        assertThat(resourceData.get("url")?.asString).contains("/get")
        assertThat(resourceData.get("duration")?.asLong).isNotNull.isGreaterThan(0L)
        assertThat(resourceData.getAsJsonObject("download"))
            .withFailMessage {
                "Expected download timing to be present in resource event, " +
                    "but it was null. This may indicate a resource key mismatch between " +
                    "startResource() and sendTiming() when an upstream interceptor rewrites the URL."
            }.isNotNull
    }

    @Test
    fun `M have RUM resource timings W request is made { app interceptor rewrites method, URL and body }`() {
        // Given
        mockServer.enqueue(MockResponse())
        val rewrittenUrl = mockServer.url("/post").toString()
        val client = OkHttpClient.Builder()
            .withMethodUrlAndBodyReplacementInterceptor(rewrittenUrl, BODY)
            .withRumOnlyInstrumentation()
            .build()

        rumMonitor.startView(RUM_VIEW_KEY, RUM_VIEW_NAME)

        val request = Request.Builder()
            .url(mockServer.url("/original-path"))
            .get()
            .build()

        // When
        client.newCall(request).execute().body?.string()

        // Then
        val recorded = mockServer.takeRequest()
        assertThat(recorded.method).isEqualTo(HttpSpec.Method.POST)
        assertThat(recorded.path).isEqualTo("/post")
        assertThat(recorded.body.readUtf8()).isEqualTo(BODY_STRING)

        val resourceEvents = getResourceEvents()
        assertThat(resourceEvents).isNotEmpty

        val resourceData = resourceEvents.first().asResourceJson()
        assertThat(resourceData.get("url")?.asString).contains("/post")
        assertThat(resourceData.get("method")?.asString).isEqualTo(HttpSpec.Method.POST)
        assertThat(resourceData.getAsJsonObject("download"))
            .withFailMessage {
                "Expected download timing to be present in RUM-only resource event, " +
                    "but it was null. This may indicate a resource key mismatch between " +
                    "startResource() and sendTiming() after an app interceptor rewrites " +
                    "method/url/body."
            }.isNotNull
    }

    // region helpers

    private fun getResourceEvents(): List<StubEvent> {
        val events = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        return events.filter { event ->
            val json = JsonParser.parseString(event.eventData).asJsonObject
            json.get("type")?.asString == "resource"
        }
    }

    private fun StubEvent.asResourceJson(): JsonObject {
        return JsonParser.parseString(eventData)
            .asJsonObject
            .getAsJsonObject("resource")
    }

    private fun OkHttpClient.Builder.withRumAndApmInstrumentation() = configureDatadogInstrumentation(
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
            .setSdkInstanceName(stubSdkCore.name),
        apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
            mapOf(
                mockServer.hostName to setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
            )
        ).setSdkInstanceName(stubSdkCore.name).setTraceSampleRate(100f)
    )

    private fun OkHttpClient.Builder.withRumOnlyInstrumentation() = configureDatadogInstrumentation(
        rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration()
            .setSdkInstanceName(stubSdkCore.name),
        apmInstrumentationConfiguration = null
    )

    private fun OkHttpClient.Builder.withUrlReplacementInterceptor(url: String) = addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(url).build())
    }

    private fun OkHttpClient.Builder.withMethodUrlAndBodyReplacementInterceptor(
        url: String,
        body: okhttp3.RequestBody
    ) = addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(url).method(HttpSpec.Method.POST, body).build())
    }

    private fun OkHttpClient.Builder.withCustomHeadersInterceptor(
        key: String,
        value: String
    ) = addInterceptor { chain -> chain.proceedWithHeader(key, value) }

    private fun OkHttpClient.Builder.withCustomHeadersNetworkInterceptor(
        key: String,
        value: String
    ) = addNetworkInterceptor { chain -> chain.proceedWithHeader(key, value) }

    private fun Interceptor.Chain.proceedWithHeader(key: String, value: String) =
        proceed(request().newBuilder().addHeader(key, value).build())

    // endregion

    companion object {
        private const val APP_INTERCEPTOR_HEADER = "X-Custom-App-Header"
        private const val APP_INTERCEPTOR_VALUE = "app-interceptor-value"
        private const val NETWORK_INTERCEPTOR_HEADER = "X-Custom-Network-Header"
        private const val NETWORK_INTERCEPTOR_VALUE = "network-interceptor-value"
        private const val DATADOG_TRACE_ID_HEADER = "x-datadog-trace-id"
        private const val DATADOG_PARENT_ID_HEADER = "x-datadog-parent-id"

        private const val RUM_VIEW_KEY = "test-view"
        private const val RUM_VIEW_NAME = "TestView"

        private const val BODY_STRING = "{\"test\":true}"
        private val BODY = BODY_STRING.toRequestBody(HttpSpec.ContentType.APPLICATION_JSON.toMediaType())

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("Unused")
        fun getTestConfigurations(): List<TestConfiguration> = listOf(mainLooper)
    }
}
