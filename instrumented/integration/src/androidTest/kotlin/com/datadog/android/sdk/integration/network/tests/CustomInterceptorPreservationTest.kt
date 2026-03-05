/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.tests

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android._InternalProxy
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.okhttp.configureDatadogInstrumentation
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.sdk.integration.network.tests.CustomInterceptorPreservationTest.Companion.withDatadogInstrumentation
import com.datadog.android.sdk.integration.network.utils.NetworkTestConfig
import com.datadog.android.sdk.integration.network.utils.NetworkTestConfig.asyncTest
import com.datadog.android.sdk.integration.network.utils.TestEchoWebServer
import com.datadog.android.sdk.integration.network.wrappers.HttpTestClientWrapper
import com.datadog.android.sdk.okhttp.RecordingDispatcher
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

/**
 * Integration test verifying that custom interceptor modifications are preserved
 * when using [withDatadogInstrumentation].
 *
 * Tests cover:
 * - Custom app and network interceptor headers arrive at the server
 * - RUM resource timing data is present when an upstream interceptor rewrites the URL
 */
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
@RunWith(JUnit4::class)
internal class CustomInterceptorPreservationTest {

    private val echoServer = TestEchoWebServer()
    private val rumMockServer = createRumMockServer()
    private val capturedRumEvents = mutableListOf<JsonObject>()

    @Before
    fun setUp() {
        echoServer.start()
        rumMockServer.start()
        setupDatadogSdk()
    }

    @After
    fun tearDown() {
        shutdownDatadogSdk()
        echoServer.shutdown()
        rumMockServer.shutdown()

        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir
            .deleteRecursively()
    }

    @Test
    fun customAppInterceptorHeadersMustBePreserved() = asyncTest {
        // Given
        val client = OkHttpClient.Builder()
            .withCustomHeadersInterceptor(APP_INTERCEPTOR_HEADER, APP_INTERCEPTOR_VALUE)
            .withDatadogInstrumentation()
            .build()

        val request = Request.Builder()
            .url(composeUrl(NetworkTestConfig.Endpoint.forMethod(HttpSpec.Method.GET)))
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(HttpSpec.StatusCode.OK)
        response.body?.parseHeaders()?.let { echoedHeaders ->
            assertThat(echoedHeaders).containsKey(APP_INTERCEPTOR_HEADER.lowercase())
            assertThat(echoedHeaders[APP_INTERCEPTOR_HEADER.lowercase()])
                .isEqualTo(APP_INTERCEPTOR_VALUE)
        }
    }

    @Test
    fun customNetworkInterceptorHeadersMustBePreserved() = asyncTest {
        // Given
        val client = OkHttpClient.Builder()
            .withCustomHeadersNetworkInterceptor(NETWORK_INTERCEPTOR_HEADER, NETWORK_INTERCEPTOR_VALUE)
            .withDatadogInstrumentation()
            .build()

        val request = Request.Builder()
            .url(composeUrl(NetworkTestConfig.Endpoint.forMethod(HttpSpec.Method.GET)))
            .get()
            .build()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(HttpSpec.StatusCode.OK)
        response.body?.parseHeaders()?.let { echoedHeaders ->
            assertThat(echoedHeaders).containsKey(NETWORK_INTERCEPTOR_HEADER.lowercase())
            assertThat(echoedHeaders[NETWORK_INTERCEPTOR_HEADER.lowercase()])
                .isEqualTo(NETWORK_INTERCEPTOR_VALUE)
        }
    }

    @Test
    fun bothCustomAndTracingHeadersMustBePreserved() = asyncTest {
        // Given
        val request = Request.Builder()
            .url(composeUrl(NetworkTestConfig.Endpoint.forMethod(HttpSpec.Method.POST)))
            .post(BODY)
            .build()

        val client = createHeaderTestClient()

        // When
        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(HttpSpec.StatusCode.OK)
        response.body?.parseHeaders()?.let { echoedHeaders ->
            // Custom headers preserved
            assertThat(echoedHeaders).containsKey(APP_INTERCEPTOR_HEADER.lowercase())
            assertThat(echoedHeaders).containsKey(NETWORK_INTERCEPTOR_HEADER.lowercase())

            // Datadog tracing headers injected
            assertThat(echoedHeaders).containsKey(DATADOG_TRACE_ID_HEADER)
            assertThat(echoedHeaders).containsKey(DATADOG_PARENT_ID_HEADER)
        }
    }

    @Test
    fun rumResourceTimingsMustBePresentWhenUpstreamInterceptorRewritesUrl() = asyncTest {
        // Given
        val originalPath = "/original-path"
        val rewrittenPath = NetworkTestConfig.Endpoint.forMethod(HttpSpec.Method.GET)

        val client = OkHttpClient.Builder()
            .withUrlReplacementInterceptor(composeUrl(rewrittenPath))
            .withDatadogInstrumentation()
            .build()

        GlobalRumMonitor.get().startView(RUM_VIEW_KEY, RUM_VIEW_NAME)

        // When
        val request = Request.Builder()
            .url(composeUrl(originalPath))
            .get()
            .build()

        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(HttpSpec.StatusCode.OK)
        response.body?.string()
        ConditionWatcher {
            val resourceEvents = synchronized(capturedRumEvents) {
                capturedRumEvents
                    .filter {
                        it.has("type") && it.get("type").asString == "resource"
                    }
                    .map { ResourceEvent.fromJsonObject(it) }
            }

            assertThat(resourceEvents).isNotEmpty

            val resource = resourceEvents.first().resource
            assertThat(resource.url).contains(rewrittenPath)
            assertThat(resource.duration).isNotNull.isGreaterThan(0L)
            assertThat(resource.download)
                .withFailMessage {
                    "Expected download timing to be present in resource event, " +
                        "but it was null. This may indicate a resource key mismatch between " +
                        "startResource() and sendTiming() when an upstream interceptor " +
                        "rewrites the URL."
                }.isNotNull

            true
        }.doWait(timeoutMs = CONDITION_TIMEOUT_MS)
    }

    @Test
    fun rumOnlyResourceTimingsMustBePresentWhenAppInterceptorRewritesMethodUrlAndBody() = asyncTest {
        // Given
        val originalPath = "/original-path"
        val rewrittenPath = NetworkTestConfig.Endpoint.forMethod(HttpSpec.Method.POST)

        val client = OkHttpClient.Builder()
            .withMethodUrlAndBodyReplacementInterceptor(composeUrl(rewrittenPath), BODY)
            .withRumOnlyDatadogInstrumentation()
            .build()

        GlobalRumMonitor.get().startView(RUM_VIEW_KEY, RUM_VIEW_NAME)

        // When
        val request = Request.Builder()
            .url(composeUrl(originalPath))
            .get()
            .build()

        val response = client.newCall(request).execute()

        // Then
        assertThat(response.code).isEqualTo(HttpSpec.StatusCode.OK)
        response.body?.parseResponse()?.let { responsePayload ->
            assertThat(responsePayload["method"]).isEqualTo(HttpSpec.Method.POST)
            assertThat(responsePayload["path"]).isEqualTo(rewrittenPath)
            assertThat(responsePayload["body"]).isEqualTo(BODY_STRING)
        }

        ConditionWatcher {
            val resourceEvents = synchronized(capturedRumEvents) {
                capturedRumEvents
                    .filter {
                        it.has("type") && it.get("type").asString == "resource"
                    }
                    .map { ResourceEvent.fromJsonObject(it) }
            }

            assertThat(resourceEvents).isNotEmpty

            val resource = resourceEvents.first().resource
            assertThat(resource.url).contains(rewrittenPath)
            assertThat(resource.method?.name).isEqualTo(HttpSpec.Method.POST)
            assertThat(resource.download)
                .withFailMessage {
                    "Expected download timing to be present in RUM-only resource event, " +
                        "but it was null. This may indicate a resource key mismatch between " +
                        "startResource() and sendTiming() after an app interceptor rewrites " +
                        "method/url/body."
                }.isNotNull

            true
        }.doWait(timeoutMs = CONDITION_TIMEOUT_MS)
    }

    private fun createRumMockServer() = MockWebServer().apply {
        dispatcher = RecordingDispatcher(true) { _, _, textBody ->
            val events = textBody.split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull {
                    try {
                        JsonParser.parseString(it).asJsonObject
                    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                        null
                    }
                }

            synchronized(capturedRumEvents) {
                capturedRumEvents.addAll(events)
            }
        }
    }

    private fun setupDatadogSdk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val sdkConfig = Configuration.Builder(
            clientToken = FAKE_CLIENT_TOKEN,
            env = FAKE_ENV
        )
            .setBatchProcessingLevel(BatchProcessingLevel.HIGH)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .apply(_InternalProxy::allowClearTextHttp)
            .build()

        Datadog.initialize(context, sdkConfig, TrackingConsent.GRANTED)

        Rum.enable(
            RumConfiguration.Builder(UUID.randomUUID().toString())
                .useCustomEndpoint(rumMockServer.url("/rum").toString())
                .build()
        )

        Trace.enable(
            TraceConfiguration.Builder().build()
        )

        GlobalDatadogTracer.registerIfAbsent(
            DatadogTracing.newTracerBuilder()
                .withPartialFlushMinSpans(1)
                .build()
        )
    }

    private fun shutdownDatadogSdk() {
        GlobalRumMonitor.get().stopSession()
        GlobalDatadogTracer.clear()
        Datadog.stopInstance()
    }

    private fun composeUrl(url: String): String = echoServer.baseUrl + url

    companion object {
        private const val FAKE_CLIENT_TOKEN = "fake-token"
        private const val FAKE_ENV = "integration-test"
        private const val APP_INTERCEPTOR_HEADER = "X-Custom-App-Header"
        private const val APP_INTERCEPTOR_VALUE = "app-interceptor-value"
        private const val NETWORK_INTERCEPTOR_HEADER = "X-Custom-Network-Header"
        private const val NETWORK_INTERCEPTOR_VALUE = "network-interceptor-value"
        private const val DATADOG_TRACE_ID_HEADER = "x-datadog-trace-id"
        private const val DATADOG_PARENT_ID_HEADER = "x-datadog-parent-id"

        private const val RUM_VIEW_KEY = "test-view"
        private const val RUM_VIEW_NAME = "TestView"

        private const val CONDITION_TIMEOUT_MS = 30_000L
        private const val BODY_STRING = "{\"test\":true}"

        private val BODY = BODY_STRING.toRequestBody(HttpSpec.ContentType.APPLICATION_JSON.toMediaType())

        private fun createHeaderTestClient() = OkHttpClient.Builder()
            .withCustomHeadersInterceptor(APP_INTERCEPTOR_HEADER, APP_INTERCEPTOR_VALUE)
            .withCustomHeadersNetworkInterceptor(NETWORK_INTERCEPTOR_HEADER, NETWORK_INTERCEPTOR_VALUE)
            .withDatadogInstrumentation()
            .build()

        private fun OkHttpClient.Builder.withUrlReplacementInterceptor(url: String) = addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .url(url)
                    .build()
            )
        }

        private fun OkHttpClient.Builder.withMethodUrlAndBodyReplacementInterceptor(
            url: String,
            body: okhttp3.RequestBody
        ) = addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .url(url)
                    .method(HttpSpec.Method.POST, body)
                    .build()
            )
        }

        private fun OkHttpClient.Builder.withCustomHeadersInterceptor(
            key: String,
            value: String
        ) = addInterceptor { chain -> chain.proceedWithHeader(key, value) }

        private fun OkHttpClient.Builder.withCustomHeadersNetworkInterceptor(
            key: String,
            value: String
        ) = addNetworkInterceptor { chain -> chain.proceedWithHeader(key, value) }

        private fun Interceptor.Chain.proceedWithHeader(
            key: String,
            value: String
        ) = proceed(
            request()
                .newBuilder()
                .addHeader(key, value)
                .build()
        )

        private fun OkHttpClient.Builder.withDatadogInstrumentation() = configureDatadogInstrumentation(
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
                HttpTestClientWrapper.tracedHosts
            ).setTraceSampleRate(100f)
        )

        private fun OkHttpClient.Builder.withRumOnlyDatadogInstrumentation() = configureDatadogInstrumentation(
            rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
            apmInstrumentationConfiguration = null
        )

        private fun ResponseBody.parseHeaders(): Map<String, String> {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val parsed: Map<String, Any?> = Gson().fromJson(string(), type)
            @Suppress("UNCHECKED_CAST")
            return (parsed["headers"] as? Map<String, String>).orEmpty()
        }

        private fun ResponseBody.parseResponse(): Map<String, Any?> {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            return Gson().fromJson(string(), type)
        }
    }
}
