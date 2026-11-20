/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.okhttp.tests.elmyr.OkHttpConfigurator
import com.datadog.android.okhttp.tests.utils.MainLooperTestConfiguration
import com.datadog.android.okhttp.tests.utils.unregisterGlobalRumMonitor
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTraceApi
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
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.AutoClose
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration test for the cache evidence reported on RUM resources.
 *
 * Wires a real [OkHttpClient] with a real [Cache] against a [MockWebServer], through both the
 * legacy [DatadogInterceptor] and the [configureDatadogInstrumentation] paths, and asserts the
 * `resource.delivery_type` and `resource.transfer_size` written on the RUM resource events.
 */
@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CacheDeliveryIntegrationTest {

    @StringForgery
    lateinit var fakeViewKey: String

    @StringForgery
    lateinit var fakeViewName: String

    @StringForgery
    lateinit var fakeResponseBody: String

    @TempDir
    lateinit var tempCacheDir: File

    @AutoClose
    private lateinit var stubSdkCore: StubSDKCore
    private lateinit var mockServer: MockWebServer
    private lateinit var cache: Cache

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val registry: Any = Datadog::class.java.getStaticValue("registry")
        val instances: MutableMap<String, SdkCore> = registry.getFieldValue("instances")
        instances += stubSdkCore.name to stubSdkCore

        mockServer = MockWebServer()
        cache = Cache(tempCacheDir, CACHE_SIZE_BYTES)

        val rumConfiguration = RumConfiguration.Builder(forge.anAlphabeticalString())
            .trackNonFatalAnrs(false)
            .build()
        Rum.enable(rumConfiguration, stubSdkCore)

        GlobalRumMonitor.get(stubSdkCore).startView(fakeViewKey, fakeViewName)
    }

    @AfterEach
    fun `tear down`() {
        unregisterGlobalRumMonitor(stubSdkCore)
        Datadog.stopInstance(stubSdkCore.name)
        cache.close()
        mockServer.shutdown()
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W first load`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse())

        // When
        okHttpClient.get("/first-load")

        // Then
        val resource = getResourceEvents().single()
        assertThat(resource.has("delivery_type")).isFalse()
        assertThat(resource.has("transfer_size")).isFalse()
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report local cache hit W second load { fresh cache entry }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        okHttpClient.get("/fresh")

        // When
        okHttpClient.get("/fresh")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(1)
        val resources = getResourceEvents()
        assertThat(resources).hasSize(2)
        assertThat(resources[1].get("delivery_type").asString).isEqualTo("cache")
        assertThat(resources[1].get("transfer_size").asLong).isEqualTo(0L)
        assertThat(resources[1].get("status_code").asInt).isEqualTo(200)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report server validated cache hit W second load { ETag, 304 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(MockResponse().setResponseCode(304).addHeader("ETag", ETAG_V1))
        okHttpClient.get("/validated")

        // When
        okHttpClient.get("/validated")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val validationRequest = checkNotNull(mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected a validation request"
        }
        assertThat(validationRequest.getHeader("If-None-Match")).isEqualTo(ETAG_V1)
        val resources = getResourceEvents()
        assertThat(resources).hasSize(2)
        assertThat(resources[1].get("delivery_type").asString).isEqualTo("cache")
        assertThat(resources[1].get("transfer_size").asLong).isEqualTo(300L)
        assertThat(resources[1].get("status_code").asInt).isEqualTo(200)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W second load { ETag changed, 200 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V2))
        okHttpClient.get("/changed")

        // When
        okHttpClient.get("/changed")

        // Then
        val resources = getResourceEvents()
        assertThat(resources).hasSize(2)
        assertThat(resources[1].has("delivery_type")).isFalse()
        assertThat(resources[1].has("transfer_size")).isFalse()
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report server validated cache hit W second load { stale entry, ETag, 304 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=0").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(MockResponse().setResponseCode(304).addHeader("ETag", ETAG_V1))
        okHttpClient.get("/stale-etag")

        // When
        okHttpClient.get("/stale-etag")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val validationRequest = checkNotNull(mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected a validation request"
        }
        assertThat(validationRequest.getHeader("If-None-Match")).isEqualTo(ETAG_V1)
        val resource = getResourceEvents()[1]
        assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
        assertThat(resource.get("transfer_size").asLong).isEqualTo(300L)
        assertThat(resource.get("status_code").asInt).isEqualTo(200)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report server validated cache hit W second load { Last-Modified, 304 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(
            cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("Last-Modified", LAST_MODIFIED)
        )
        mockServer.enqueue(MockResponse().setResponseCode(304))
        okHttpClient.get("/last-modified")

        // When
        okHttpClient.get("/last-modified")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val validationRequest = checkNotNull(mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected a validation request"
        }
        assertThat(validationRequest.getHeader("If-Modified-Since")).isEqualTo(LAST_MODIFIED)
        val resource = getResourceEvents()[1]
        assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
        assertThat(resource.get("transfer_size").asLong).isEqualTo(300L)
        assertThat(resource.get("status_code").asInt).isEqualTo(200)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report local cache hit W request forces the cache { stale entry }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=0").addHeader("ETag", ETAG_V1))
        okHttpClient.get("/force-cache")

        // When
        okHttpClient.get("/force-cache", CacheControl.FORCE_CACHE)

        // Then
        assertThat(mockServer.requestCount).isEqualTo(1)
        val resource = getResourceEvents()[1]
        assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
        assertThat(resource.get("transfer_size").asLong).isEqualTo(0L)
        assertThat(resource.get("status_code").asInt).isEqualTo(200)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W request forces the network { fresh entry }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        okHttpClient.get("/force-network")

        // When
        okHttpClient.get("/force-network", CacheControl.FORCE_NETWORK)

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        assertNoCacheDelivery(getResourceEvents()[1])
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W only-if-cached request { no cache entry, 504 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)

        // When
        okHttpClient.get("/only-if-cached", CacheControl.FORCE_CACHE)

        // Then
        assertThat(mockServer.requestCount).isEqualTo(0)
        val resource = getResourceEvents().single()
        assertThat(resource.get("status_code").asInt).isEqualTo(504)
        assertNoCacheDelivery(resource)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W second load { no-store }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store"))
        okHttpClient.get("/no-store")

        // When
        okHttpClient.get("/no-store")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        assertNoCacheDelivery(getResourceEvents()[1])
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W second load { POST }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        okHttpClient.post("/post")

        // When
        okHttpClient.post("/post")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        val resource = getResourceEvents()[1]
        assertThat(resource.get("method").asString).isEqualTo("POST")
        assertNoCacheDelivery(resource)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report cache delivery W second load { client without cache }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path, withCache = false)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        okHttpClient.get("/no-cache-configured")

        // When
        okHttpClient.get("/no-cache-configured")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(2)
        assertNoCacheDelivery(getResourceEvents()[1])
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not leak the reserved attributes in the context W cache hits`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(MockResponse().setResponseCode(304).addHeader("ETag", ETAG_V1))
        okHttpClient.get("/fresh")
        okHttpClient.get("/validated")

        // When
        okHttpClient.get("/fresh")
        okHttpClient.get("/validated")

        // Then
        val events = getFullResourceEvents()
        assertThat(events).hasSize(4)
        events.forEach { event ->
            val context = event.getAsJsonObject("context")
            if (context != null) {
                assertThat(context.has(RESERVED_DELIVERY_TYPE_KEY)).isFalse()
                assertThat(context.has(RESERVED_TRANSFER_SIZE_KEY)).isFalse()
            }
        }
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M keep the cache evidence W attributes provider sets the reserved attributes { local hit }`(
        path: InstrumentationPath
    ) {
        // Given
        val okHttpClient = buildClient(path, attributesProvider = ReservedKeysAttributesProvider)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        okHttpClient.get("/provider")

        // When
        okHttpClient.get("/provider")

        // Then
        val resource = getResourceEvents()[1]
        assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
        assertThat(resource.get("transfer_size").asLong).isEqualTo(0L)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report cache delivery and CDN headers W local hit { trackResourceHeaders }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path, trackHeaders = true)
        mockServer.enqueue(
            cacheableResponse()
                .addHeader("Cache-Control", "max-age=60")
                .addHeader("Age", "12")
                .addHeader("X-Cache", "HIT")
                .addHeader("CF-Cache-Status", "HIT")
                .addHeader("X-Vercel-Cache", "HIT")
        )
        okHttpClient.get("/cdn")

        // When
        okHttpClient.get("/cdn")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(1)
        val resource = getResourceEvents()[1]
        assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
        assertThat(resource.get("transfer_size").asLong).isEqualTo(0L)
        val headers = resource.getAsJsonObject("response").getAsJsonObject("headers")
        assertThat(headers.get("cache-control").asString).isEqualTo("max-age=60")
        assertThat(headers.get("age").asString).isEqualTo("12")
        assertThat(headers.get("x-cache").asString).isEqualTo("HIT")
        assertThat(headers.get("cf-cache-status").asString).isEqualTo("HIT")
        assertThat(headers.get("x-vercel-cache").asString).isEqualTo("HIT")
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report CDN headers only W first load { trackResourceHeaders }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path, trackHeaders = true)
        mockServer.enqueue(
            cacheableResponse()
                .addHeader("Cache-Control", "max-age=60")
                .addHeader("X-Cache", "MISS")
                .addHeader("CF-Cache-Status", "MISS")
        )

        // When
        okHttpClient.get("/cdn-miss")

        // Then
        val resource = getResourceEvents().single()
        assertNoCacheDelivery(resource)
        val headers = resource.getAsJsonObject("response").getAsJsonObject("headers")
        assertThat(headers.get("x-cache").asString).isEqualTo("MISS")
        assertThat(headers.get("cf-cache-status").asString).isEqualTo("MISS")
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report cache without transfer size W network redirect to a cached resource`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path, attributesProvider = ReservedKeysAttributesProvider)
        mockServer.enqueue(redirectResponse("/redirect-target"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(redirectResponse("/redirect-target"))
        okHttpClient.get("/redirect-source")

        // When
        okHttpClient.get("/redirect-source")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(3)
        val resource = getResourceEvents()[1]
        assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
        assertThat(resource.has("transfer_size")).isFalse()
        assertThat(resource.get("status_code").asInt).isEqualTo(200)
    }

    // region Helpers

    private fun buildClient(
        path: InstrumentationPath,
        withCache: Boolean = true,
        trackHeaders: Boolean = false,
        attributesProvider: RumResourceAttributesProvider? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (withCache) builder.cache(cache)
        return when (path) {
            InstrumentationPath.DATADOG_INTERCEPTOR -> {
                val interceptorBuilder = DatadogInterceptor.Builder(tracedHosts = emptyList())
                    .setSdkInstanceName(stubSdkCore.name)
                if (trackHeaders) interceptorBuilder.trackResourceHeaders()
                attributesProvider?.let { interceptorBuilder.setRumResourceAttributesProvider(it) }
                builder
                    .addInterceptor(interceptorBuilder.build())
                    .eventListenerFactory(DatadogEventListener.Factory(stubSdkCore.name))
                    .build()
            }

            InstrumentationPath.RUM_NETWORK_INSTRUMENTATION -> {
                val rumConfiguration = RumNetworkInstrumentationConfiguration()
                    .setSdkInstanceName(stubSdkCore.name)
                if (trackHeaders) rumConfiguration.trackResourceHeaders()
                attributesProvider?.let { rumConfiguration.setRumResourceAttributesProvider(it) }
                builder
                    .configureDatadogInstrumentation(
                        rumInstrumentationConfiguration = rumConfiguration,
                        apmInstrumentationConfiguration = null
                    )
                    .build()
            }
        }
    }

    private fun OkHttpClient.get(path: String, cacheControl: CacheControl? = null) {
        val requestBuilder = Request.Builder().url(mockServer.url(path))
        cacheControl?.let { requestBuilder.cacheControl(it) }
        newCall(requestBuilder.build()).execute().use { it.body?.string() }
    }

    private fun OkHttpClient.post(path: String) {
        val body = fakeResponseBody.toRequestBody("text/plain".toMediaType())
        newCall(Request.Builder().url(mockServer.url(path)).post(body).build()).execute().use { it.body?.string() }
    }

    private fun redirectResponse(location: String) = MockResponse()
        .setResponseCode(302)
        .addHeader("Location", location)
        .addHeader("Cache-Control", "no-store")

    private fun assertNoCacheDelivery(resource: JsonObject) {
        assertThat(resource.has("delivery_type")).isFalse()
        assertThat(resource.has("transfer_size")).isFalse()
    }

    private fun cacheableResponse() = MockResponse()
        .setResponseCode(200)
        .setBody(fakeResponseBody)

    private fun getResourceEvents(): List<JsonObject> {
        return getFullResourceEvents().map { it.getAsJsonObject("resource") }
    }

    private fun getFullResourceEvents(): List<JsonObject> {
        return stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
            .map { JsonParser.parseString(it.eventData).asJsonObject }
            .filter { it.get("type")?.asString == "resource" }
    }

    // endregion

    /**
     * Tries to override the reserved cache attributes, as a customer provider could by accident.
     */
    private object ReservedKeysAttributesProvider : RumResourceAttributesProvider {
        private val attributes = mapOf(
            RESERVED_DELIVERY_TYPE_KEY to "other",
            RESERVED_TRANSFER_SIZE_KEY to PROVIDER_TRANSFER_SIZE
        )

        @Deprecated("Use the variant with HttpRequestInfo/HttpResponseInfo instead")
        override fun onProvideAttributes(
            request: Request,
            response: Response?,
            throwable: Throwable?
        ): Map<String, Any?> = attributes

        override fun onProvideAttributes(
            request: HttpRequestInfo,
            response: HttpResponseInfo?,
            throwable: Throwable?
        ): Map<String, Any?> = attributes
    }

    enum class InstrumentationPath {
        DATADOG_INTERCEPTOR,
        RUM_NETWORK_INSTRUMENTATION
    }

    companion object {
        private const val CACHE_SIZE_BYTES = 1024L * 1024L
        private const val TAKE_REQUEST_TIMEOUT_SECONDS = 5L
        private const val ETAG_V1 = "\"v1\""
        private const val ETAG_V2 = "\"v2\""
        private const val LAST_MODIFIED = "Mon, 16 Nov 2026 10:00:00 GMT"
        private const val RESERVED_DELIVERY_TYPE_KEY = "_dd.resource.delivery_type"
        private const val RESERVED_TRANSFER_SIZE_KEY = "_dd.resource.transfer_size"
        private const val PROVIDER_TRANSFER_SIZE = 999L

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("Unused")
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
