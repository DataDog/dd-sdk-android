/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.Datadog
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
import com.datadog.android.utils.registerInstance
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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

        Datadog.registerInstance(stubSdkCore.name, stubSdkCore)

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
    fun `M report other without transfer size W response downloaded from the network`(
        path: InstrumentationPath,
        forge: Forge
    ) {
        // Given
        val fakeScenario = forge.aValueFrom(NetworkLoadScenario::class.java)

        // When
        loadFromNetwork(path, fakeScenario)

        // Then
        assertThat(mockServer.requestCount).describedAs(fakeScenario.name).isEqualTo(fakeScenario.requestCount)
        val resource = getResourceEvents().last()
        assertThat(resource.get("status_code").asInt).describedAs(fakeScenario.name).isEqualTo(fakeScenario.statusCode)
        assertNetworkLoad(resource)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M not report delivery W only-if-cached request { cache miss, 504 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)

        // When
        okHttpClient.get("/only-if-cached", CacheControl.FORCE_CACHE)

        // Then
        assertThat(mockServer.requestCount).isZero()
        val resource = getResourceEvents().single()
        assertThat(resource.get("status_code").asInt).isEqualTo(504)
        assertNoDelivery(resource)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report local cache hit W second load { fresh entry, or stale entry with forced cache }`(
        path: InstrumentationPath,
        forge: Forge
    ) {
        // Given
        val okHttpClient = buildClient(path)
        val forceCache = forge.aBool()
        val cacheControl = if (forceCache) "max-age=0" else "max-age=60"
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", cacheControl).addHeader("ETag", ETAG_V1))
        okHttpClient.get("/local-hit")

        // When
        okHttpClient.get("/local-hit", CacheControl.FORCE_CACHE.takeIf { forceCache })

        // Then
        assertThat(mockServer.requestCount).isEqualTo(1)
        assertCacheDelivery(getResourceEvents()[1], transferSize = 0L)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report server validated cache hit W second load { 304 }`(path: InstrumentationPath, forge: Forge) {
        // Given
        val okHttpClient = buildClient(path)
        val cacheControl = forge.anElementFrom("no-cache", "max-age=0")
        val (validatorHeader, conditionalHeader, validator) = forge.anElementFrom(
            Triple("ETag", "If-None-Match", ETAG_V1),
            Triple("Last-Modified", "If-Modified-Since", LAST_MODIFIED)
        )
        mockServer.enqueue(
            cacheableResponse().addHeader("Cache-Control", cacheControl).addHeader(validatorHeader, validator)
        )
        mockServer.enqueue(MockResponse().setResponseCode(304))
        okHttpClient.get("/validated")

        // When
        okHttpClient.get("/validated")

        // Then
        assertValidationRequest(conditionalHeader, validator)
        assertCacheDelivery(getResourceEvents()[1], transferSize = 300L)
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report other W second load { ETag changed, 200 }`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V2))
        okHttpClient.get("/changed")

        // When
        okHttpClient.get("/changed")

        // Then
        assertValidationRequest("If-None-Match", ETAG_V1)
        assertNetworkLoad(getResourceEvents()[1])
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M only report the delivery evidence W cache hits and network loads`(path: InstrumentationPath) {
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
        assertThat(events.map { it.getAsJsonObject("resource").get("delivery_type")?.asString })
            .containsExactly("other", "other", "cache", "cache")
        events.forEach { event ->
            assertThat(
                event.getAsJsonObject("resource").keySet()
            ).doesNotContainAnyElementsOf(FORBIDDEN_RESOURCE_FIELDS)
            event.getAsJsonObject("context")?.let { context ->
                assertThat(context.keySet()).doesNotContain(RESERVED_DELIVERY_TYPE_KEY, RESERVED_TRANSFER_SIZE_KEY)
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
        assertCacheDelivery(getResourceEvents()[1], transferSize = 0L)
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
        assertCacheDelivery(resource, transferSize = 0L)
        assertThat(resource.responseHeaders()).containsAllEntriesOf(
            mapOf(
                "cache-control" to "max-age=60",
                "age" to "12",
                "x-cache" to "HIT",
                "cf-cache-status" to "HIT",
                "x-vercel-cache" to "HIT"
            )
        )
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report other and CDN headers W first load { trackResourceHeaders }`(path: InstrumentationPath) {
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
        assertNetworkLoad(resource)
        assertThat(resource.responseHeaders())
            .containsEntry("x-cache", "MISS")
            .containsEntry("cf-cache-status", "MISS")
    }

    @ParameterizedTest
    @EnumSource(InstrumentationPath::class)
    fun `M report local cache hit W network redirect to a cached resource`(path: InstrumentationPath) {
        // Given
        val okHttpClient = buildClient(path)
        mockServer.enqueue(redirectResponse("/redirect-target"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(redirectResponse("/redirect-target"))
        okHttpClient.get("/redirect-source")

        // When
        okHttpClient.get("/redirect-source")

        // Then
        assertThat(mockServer.requestCount).isEqualTo(3)
        assertCacheDelivery(getResourceEvents()[1], transferSize = 0L)
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

    private fun loadFromNetwork(path: InstrumentationPath, scenario: NetworkLoadScenario) {
        val okHttpClient = buildClient(path, withCache = scenario != NetworkLoadScenario.CLIENT_WITHOUT_CACHE)
        when (scenario) {
            NetworkLoadScenario.FIRST_LOAD -> {
                mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
                okHttpClient.get("/first-load")
            }
            NetworkLoadScenario.NO_STORE -> {
                repeat(2) { mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store")) }
                repeat(2) { okHttpClient.get("/no-store") }
            }
            NetworkLoadScenario.POST -> {
                repeat(2) { mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60")) }
                repeat(2) { okHttpClient.post("/post") }
            }
            NetworkLoadScenario.CLIENT_WITHOUT_CACHE -> {
                repeat(2) { mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60")) }
                repeat(2) { okHttpClient.get("/no-cache-configured") }
            }
            NetworkLoadScenario.FORCE_NETWORK -> {
                repeat(2) { mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60")) }
                okHttpClient.get("/force-network")
                okHttpClient.get("/force-network", CacheControl.FORCE_NETWORK)
            }
        }
    }

    private fun assertValidationRequest(conditionalHeader: String, validator: String) {
        assertThat(mockServer.requestCount).isEqualTo(2)
        mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val validationRequest = checkNotNull(mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected a validation request"
        }
        assertThat(validationRequest.getHeader(conditionalHeader)).isEqualTo(validator)
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

    enum class NetworkLoadScenario(val requestCount: Int, val statusCode: Int) {
        FIRST_LOAD(requestCount = 1, statusCode = 200),
        NO_STORE(requestCount = 2, statusCode = 200),
        POST(requestCount = 2, statusCode = 200),
        CLIENT_WITHOUT_CACHE(requestCount = 2, statusCode = 200),
        FORCE_NETWORK(requestCount = 2, statusCode = 200)
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

        // derived by the backend, deprecated, or not reliably known on Android
        private val FORBIDDEN_RESOURCE_FIELDS = listOf(
            "cache_status",
            "cache_reason",
            "local_cache_hit",
            "encoded_body_size",
            "decoded_body_size"
        )

        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        @Suppress("Unused")
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }

        private fun redirectResponse(location: String) = MockResponse()
            .setResponseCode(302)
            .addHeader("Location", location)
            .addHeader("Cache-Control", "no-store")

        private fun assertNoDelivery(resource: JsonObject) {
            assertThat(resource.has("delivery_type")).isFalse()
            assertThat(resource.has("transfer_size")).isFalse()
        }

        // OkHttp exposes no byte count for the headers, so the transfer size of a network load is unknown
        private fun assertNetworkLoad(resource: JsonObject) {
            assertThat(resource.get("delivery_type").asString).isEqualTo("other")
            assertThat(resource.has("transfer_size")).isFalse()
        }

        private fun assertCacheDelivery(resource: JsonObject, transferSize: Long?) {
            assertThat(resource.get("delivery_type").asString).isEqualTo("cache")
            if (transferSize == null) {
                assertThat(resource.has("transfer_size")).isFalse()
            } else {
                assertThat(resource.get("transfer_size").asLong).isEqualTo(transferSize)
            }
            assertThat(resource.get("status_code").asInt).isEqualTo(200)
        }

        private fun JsonObject.responseHeaders(): Map<String, String> {
            return getAsJsonObject("response").getAsJsonObject("headers").entrySet()
                .associate { it.key to it.value.asString }
        }
    }
}
