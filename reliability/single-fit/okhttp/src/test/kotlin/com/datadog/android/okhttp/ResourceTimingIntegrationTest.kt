/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubEvent
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.tests.utils.MainLooperTestConfiguration
import com.datadog.android.okhttp.tests.utils.unregisterGlobalRumMonitor
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * End-to-end integration test for the OkHttp resource timing feature.
 *
 * Wires [DatadogInterceptor] and [DatadogEventListener.Factory] on a real [OkHttpClient]
 * against a [MockWebServer] and asserts that RUM resource events carry the timing
 * breakdown sub-objects (dns / connect / first_byte / download).
 *
 * Coverage that unit tests cannot provide: the interceptor and the listener must agree
 * on the [com.datadog.android.rum.resource.ResourceId] derived from the request tag for
 * timings to reach the correct resource scope, and concurrent requests to the same URL
 * must each produce a distinct resource event.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ResourceTimingIntegrationTest {

    @StringForgery
    lateinit var fakeViewKey: String

    @StringForgery
    lateinit var fakeViewName: String

    @StringForgery
    lateinit var fakeResponseBody: String

    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var rumMonitor: RumMonitor

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        // Drive deviceTimeNs from a strictly-advancing counter so every OkHttp callback
        // captures a unique value.
        val clockNs = AtomicLong(0L)
        whenever(stubSdkCore.time.deviceTimeNs).thenAnswer { clockNs.addAndGet(CLOCK_TICK_NS) }

        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore

        mockServer = MockWebServer()

        val fakeApplicationId = forge.anAlphabeticalString()
        val rumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .build()
        Rum.enable(rumConfiguration, stubSdkCore)

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(tracedHosts = listOf(mockServer.hostName))
                    .setSdkInstanceName(stubSdkCore.name)
                    .build()
            )
            .eventListenerFactory(DatadogEventListener.Factory(stubSdkCore.name))
            .build()

        rumMonitor = GlobalRumMonitor.get(stubSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        unregisterGlobalRumMonitor(stubSdkCore)
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    @Test
    fun `M populate resource timing breakdown W single HTTP request`() {
        // Given
        rumMonitor.startView(fakeViewKey, fakeViewName)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(fakeResponseBody))

        // When
        okHttpClient.newCall(
            Request.Builder().url(mockServer.url("/api/foo")).build()
        ).execute().close()

        // Then
        val resourceEvents = getResourceEvents()
        assertThat(resourceEvents).hasSize(1)

        val resource = resourceEvents.single().resourceJson()
        assertThat(resource.get("url").asString)
            .isEqualTo(mockServer.url("/api/foo").toString())
        assertThat(resource.get("status_code").asInt).isEqualTo(200)
        assertThat(resource.get("duration").asLong).isGreaterThan(0)

        // Timing sub-objects are only emitted when the listener's addResourceTiming reaches
        // the RUM resource scope under the SAME ResourceId used by the interceptor's
        // startResource. This is the contract that ties the two components together.
        assertHasTimingSection(resource, "dns")
        assertHasTimingSection(resource, "connect")
        assertHasTimingSection(resource, "first_byte", requireDurationGreaterThanZero = true)
        assertHasTimingSection(resource, "download")
        assertThat(resource.has("ssl"))
            .withFailMessage("Plain HTTP request must not produce an ssl timing section")
            .isFalse()
    }

    @Test
    fun `M still emit resource timing W response status is 4xx`() {
        // Given
        rumMonitor.startView(fakeViewKey, fakeViewName)
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody(fakeResponseBody))

        // When
        okHttpClient.newCall(
            Request.Builder().url(mockServer.url("/api/missing")).build()
        ).execute().close()

        // Then
        // On HTTP >= 400 the listener flushes timings early from responseHeadersEnd rather
        // than waiting for callEnd, since the body may never be fully delivered.
        val resourceEvents = getResourceEvents()
        assertThat(resourceEvents).hasSize(1)

        val resource = resourceEvents.single().resourceJson()
        assertThat(resource.get("status_code").asInt).isEqualTo(404)
        assertHasTimingSection(resource, "dns")
        assertHasTimingSection(resource, "connect")
        assertHasTimingSection(resource, "first_byte", requireDurationGreaterThanZero = true)
    }

    @Test
    fun `M emit error event W call failed { socket disconnected at start }`() {
        // Given
        rumMonitor.startView(fakeViewKey, fakeViewName)
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // When
        // The dropped socket causes OkHttp to throw an IOException, which routes through
        // EventListener.callFailed and DatadogInterceptor's throwable handling.
        @Suppress("SwallowedException")
        try {
            okHttpClient.newCall(
                Request.Builder().url(mockServer.url("/api/disconnect")).build()
            ).execute().close()
        } catch (_: IOException) {
            // Intentionally ignored.
        }

        // Then
        // stopResourceWithError closes the resource scope by emitting a RUM error event
        // (with error.resource populated), not a resource event. Asserting on both event
        // types pins this contract: resource events are reserved for successful exchanges.
        assertThat(getResourceEvents()).isEmpty()

        val errorEvents = getEventsByType("error")
        assertThat(errorEvents).hasSize(1)
        val errorEvent = ErrorEvent.fromJson(errorEvents.single().eventData)
        assertThat(errorEvent.error.resource)
            .withFailMessage("Network failure error must carry an 'error.resource' section")
            .isNotNull
        assertThat(errorEvent.error.resource!!.url)
            .isEqualTo(mockServer.url("/api/disconnect").toString())
    }

    @Test
    fun `M emit one resource per concurrent request W concurrent calls to same URL`() {
        // Given
        rumMonitor.startView(fakeViewKey, fakeViewName)
        repeat(CONCURRENT_REQUESTS) {
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody(fakeResponseBody))
        }
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(CONCURRENT_REQUESTS)

        // When
        repeat(CONCURRENT_REQUESTS) {
            executor.execute {
                try {
                    startGate.await()
                    okHttpClient.newCall(
                        Request.Builder().url(mockServer.url("/api/concurrent")).build()
                    ).execute().close()
                } finally {
                    doneGate.countDown()
                }
            }
        }
        startGate.countDown()
        check(doneGate.await(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Concurrent requests did not complete within $CONCURRENT_TIMEOUT_SECONDS seconds"
        }
        executor.shutdown()

        // Then
        // Each concurrent request produces its own resource event with its own status
        // and total duration. Per-phase timing values may be cross-attributed between
        // concurrent same-URL requests; tracked under RUM-13566.
        val resourceEvents = getResourceEvents()
        assertThat(resourceEvents).hasSize(CONCURRENT_REQUESTS)
        resourceEvents.forEach { event ->
            val resource = event.resourceJson()
            assertThat(resource.get("url").asString)
                .isEqualTo(mockServer.url("/api/concurrent").toString())
            assertThat(resource.get("status_code").asInt).isEqualTo(200)
            assertThat(resource.get("duration").asLong).isGreaterThan(0)
            assertThat(resource.has("first_byte")).isTrue()
        }
    }

    // region Helpers

    private fun getResourceEvents(): List<StubEvent> = getEventsByType("resource")

    private fun getEventsByType(type: String): List<StubEvent> {
        val events = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        return events.filter { event ->
            JsonParser.parseString(event.eventData).asJsonObject.get("type")?.asString == type
        }
    }

    private fun StubEvent.resourceJson(): JsonObject {
        val root = JsonParser.parseString(eventData).asJsonObject
        return root.getAsJsonObject("resource")
    }

    private fun assertHasTimingSection(
        resource: JsonObject,
        section: String,
        requireDurationGreaterThanZero: Boolean = false
    ) {
        assertThat(resource.has(section))
            .withFailMessage("Resource event JSON is missing timing section '%s'", section)
            .isTrue()
        val sectionJson = resource.getAsJsonObject(section)
        assertThat(sectionJson.has("start"))
            .withFailMessage("Timing section '%s' is missing 'start'", section)
            .isTrue()
        assertThat(sectionJson.has("duration"))
            .withFailMessage("Timing section '%s' is missing 'duration'", section)
            .isTrue()
        if (requireDurationGreaterThanZero) {
            assertThat(sectionJson.get("duration").asLong)
                .withFailMessage("Timing section '%s' has non-positive duration", section)
                .isGreaterThan(0)
        }
    }

    // endregion

    companion object {
        private const val CONCURRENT_REQUESTS = 5
        private const val CONCURRENT_TIMEOUT_SECONDS = 10L

        // 1 ms per call — large enough to be human-readable in failure messages, small
        // enough that even thousands of callbacks across all tests stay well within Long.
        private const val CLOCK_TICK_NS = 1_000_000L

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("Unused")
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
