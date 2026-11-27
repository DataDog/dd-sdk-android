/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.tests

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android._InternalProxy
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.cronet.configureDatadogInstrumentation
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.sdk.assertj.ObservedCronetResponseAssert.Companion.assertThat
import com.datadog.android.sdk.assertj.ResourceEventAssert.Companion.assertThat
import com.datadog.android.sdk.integration.network.models.ObservedCronetResponse
import com.datadog.android.trace.ExperimentalTraceApi
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.tasks.Tasks
import fr.xgouchet.elmyr.Forge
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * End-to-end test for the cache evidence reported on RUM resources by the Cronet integration.
 *
 * Runs a real Cronet engine (Play Services provider) with an in-memory HTTP cache against a
 * [MockWebServer] over plain HTTP/1.1 (QUIC and HTTP/2 disabled), and asserts the
 * `resource.delivery_type` and `resource.transfer_size` of the written RUM resource events.
 */
@OptIn(ExperimentalRumApi::class, ExperimentalTraceApi::class)
@RunWith(AndroidJUnit4::class)
internal class CronetCacheDeliveryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resourceQueue = LinkedBlockingQueue<ResourceEvent>()

    private val writtenResources = mutableListOf<ResourceEvent>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val forge = Forge()

    private lateinit var mockServer: MockWebServer
    private lateinit var engine: CronetEngine

    @Before
    fun setUp() {
        Tasks.await(
            CronetProviderInstaller.installProvider(context),
            PROVIDER_INSTALL_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )

        mockServer = MockWebServer()
        mockServer.start()

        val sdkConfiguration = Configuration.Builder(clientToken = FAKE_CLIENT_TOKEN, env = FAKE_ENV)
            .apply { _InternalProxy.allowClearTextHttp(this) }
            .build()
        val sdkCore = checkNotNull(Datadog.initialize(context, sdkConfiguration, TrackingConsent.GRANTED)) {
            "Failed to initialize Datadog SDK"
        }
        val rumConfiguration = RumConfiguration.Builder(FAKE_APPLICATION_ID)
            .trackNonFatalAnrs(false)
            .setResourceEventMapper { event ->
                resourceQueue.add(event)
                event
            }
            .build()
        Rum.enable(rumConfiguration, sdkCore)
        GlobalRumMonitor.get(sdkCore).startView(VIEW_KEY, VIEW_NAME)

        engine = CronetEngine.Builder(context)
            .enableQuic(false)
            .enableHttp2(false)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, CACHE_SIZE_BYTES)
            .configureDatadogInstrumentation(
                rumInstrumentationConfiguration = RumNetworkInstrumentationConfiguration(),
                apmInstrumentationConfiguration = null
            )
            .build()
    }

    @After
    fun tearDown() {
        try {
            if (::engine.isInitialized) engine.shutdown()
        } finally {
            executor.shutdown()
            if (::mockServer.isInitialized) mockServer.shutdown()
            Datadog.stopInstance()
            context.cacheDir.deleteRecursively()
        }
    }

    @Test
    fun mustReportOtherWithTransferSize_whenDownloadedFromTheNetwork() {
        // Given
        val fakeScenario = forge.aValueFrom(NetworkLoadScenario::class.java)
        enqueueNetworkLoad(mockServer, fakeScenario)

        // When
        val response = List(fakeScenario.loadCount) { execute(fakeScenario.path, fakeScenario.method) }.last()

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(fakeScenario.loadCount)
        assertThat(awaitResources(expectedCount = fakeScenario.loadCount).last())
            .describedAs(response.toString())
            .hasDeliveryType(ResourceEvent.DeliveryType.OTHER)
            .hasPositiveTransferSize()
    }

    @Test
    fun mustReportLocalCacheHit_whenSecondLoad_freshCacheEntry() {
        // Given
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        execute("/fresh")

        // When
        val response = execute("/fresh")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(1)
        assertThat(awaitResources(expectedCount = 2).last())
            .describedAs(response.toString())
            .hasStatusCode(200L)
            .hasDeliveryType(ResourceEvent.DeliveryType.CACHE)
            .hasTransferSize(0L)
    }

    @Test
    fun mustReportServerValidatedCacheHit_whenSecondLoad_304_eTagValidator() {
        verifyServerValidatedCacheHit(CacheValidator.ETAG)
    }

    @Test
    fun mustReportServerValidatedCacheHit_whenSecondLoad_304_lastModifiedValidator() {
        verifyServerValidatedCacheHit(CacheValidator.LAST_MODIFIED)
    }

    @Test
    fun mustReportCacheWithoutTransferSize_whenNetworkRedirectToCachedResource() {
        // Given
        val redirect = MockResponse()
            .setResponseCode(302)
            .addHeader("Location", "/redirect-target")
            .addHeader("Cache-Control", "no-store")
        mockServer.enqueue(redirect)
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(redirect)
        execute("/redirect-source")

        // When
        val response = execute("/redirect-source")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(3)
        assertThat(awaitResources(expectedCount = 2).last())
            .describedAs(response.toString())
            .hasStatusCode(200L)
            .hasDeliveryType(ResourceEvent.DeliveryType.CACHE)
            .hasTransferSize(null)
    }

    @Test
    fun mustCountRedirectBytes_inUrlResponseInfoReceivedByteCount() {
        // Given
        enqueueDirectAndRedirectedLoads(mockServer)

        // When
        val direct = execute("/target")
        val redirected = execute("/source")

        // Then
        assertThat(redirected).hasUrlChainSize(2)
        assertThat(redirected.receivedByteCount)
            .describedAs("direct: $direct, redirected: $redirected")
            .isGreaterThan(direct.receivedByteCount)
    }

    @Test
    fun mustCountRedirectBytes_inMetricsReceivedByteCount() {
        // Given
        enqueueDirectAndRedirectedLoads(mockServer)

        // When
        val direct = execute("/target")
        val redirected = execute("/source")

        // Then
        val directMetrics = checkNotNull(direct.metricsReceivedByteCount) { "Expected metrics: $direct" }
        val redirectedMetrics = checkNotNull(redirected.metricsReceivedByteCount) { "Expected metrics: $redirected" }
        val redirectBytes = redirected.receivedByteCount - direct.receivedByteCount
        assertThat(redirectedMetrics - directMetrics)
            .describedAs("direct: $direct, redirected: $redirected")
            .isGreaterThanOrEqualTo(redirectBytes / 2)
    }

    @Test
    fun mustReportNoNetworkBytes_whenRedirectAndTargetServedFromCache() {
        // Given
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(301)
                .addHeader("Location", "/cached-target")
                .addHeader("Cache-Control", "max-age=60")
        )
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        execute("/cached-source")

        // When
        val response = execute("/cached-source")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        assertThat(response)
            .isCached()
            .hasUrlChainSize(2)
            .hasReceivedByteCount(0L)
        assertThat(awaitResources(expectedCount = 2).last())
            .describedAs(response.toString())
            .hasStatusCode(200L)
            .hasDeliveryType(ResourceEvent.DeliveryType.CACHE)
            .hasTransferSize(0L)
    }

    // region Helpers

    private fun verifyServerValidatedCacheHit(validator: CacheValidator) {
        // Given
        val cacheControl = forge.anElementFrom("no-cache", "max-age=0")
        mockServer.enqueue(
            cacheableResponse()
                .addHeader("Cache-Control", cacheControl)
                .addHeader(validator.responseHeader, validator.value)
        )
        mockServer.enqueue(MockResponse().setResponseCode(304))
        execute("/validated")

        // When
        val response = execute("/validated")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val validationRequest = checkNotNull(mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected a validation request"
        }
        assertThat(validationRequest.getHeader(validator.conditionalRequestHeader)).isEqualTo(validator.value)
        assertThat(awaitResources(expectedCount = 2).last())
            .describedAs(response.toString())
            .hasStatusCode(200L)
            .hasDeliveryType(ResourceEvent.DeliveryType.CACHE)
            .hasPositiveTransferSize()
    }

    private fun execute(path: String, method: String = "GET"): ObservedCronetResponse {
        val resourcesBefore = writtenResources.size
        val latch = CountDownLatch(1)
        val finishedLatch = CountDownLatch(1)
        var observed: ObservedCronetResponse? = null
        var metricsReceivedByteCount: Long? = null
        var failure: CronetException? = null
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                observed = ObservedCronetResponse(
                    path = path,
                    statusCode = info.httpStatusCode,
                    wasCached = info.wasCached(),
                    receivedByteCount = info.receivedByteCount,
                    urlChainSize = info.urlChain.size
                )
                latch.countDown()
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                failure = error
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                latch.countDown()
            }
        }
        val requestBuilder = engine.newUrlRequestBuilder(mockServer.url(path).toString(), callback, executor)
            .setHttpMethod(method)
            .setRequestFinishedListener(
                object : RequestFinishedInfo.Listener(executor) {
                    override fun onRequestFinished(requestInfo: RequestFinishedInfo) {
                        metricsReceivedByteCount = requestInfo.metrics.receivedByteCount
                        finishedLatch.countDown()
                    }
                }
            )
        if (method == "POST") {
            requestBuilder.addHeader("Content-Type", "text/plain")
            requestBuilder.setUploadDataProvider(ByteArrayUploadDataProvider(REQUEST_BODY.toByteArray()), executor)
        }
        val urlRequest = requestBuilder.build()
        urlRequest.start()

        if (!latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            urlRequest.cancel()
            latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            throw AssertionError("Request to $path timed out")
        }
        failure?.let { throw AssertionError("Request to $path failed", it) }
        finishedLatch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val response = checkNotNull(observed) { "Request to $path was cancelled" }
            .copy(metricsReceivedByteCount = metricsReceivedByteCount)
        Log.i(TAG, response.toString())
        awaitResources(expectedCount = resourcesBefore + 1)
        return response
    }

    private fun awaitResources(expectedCount: Int): List<ResourceEvent> {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(EVENTS_TIMEOUT_SECONDS)
        while (writtenResources.size < expectedCount) {
            val resource = resourceQueue.poll(deadlineNs - System.nanoTime(), TimeUnit.NANOSECONDS) ?: break
            writtenResources.add(resource)
        }
        resourceQueue.drainTo(writtenResources)
        assertThat(writtenResources).hasSize(expectedCount)
        return writtenResources.toList()
    }

    // endregion

    private class ByteArrayUploadDataProvider(private val bytes: ByteArray) : UploadDataProvider() {
        private var offset = 0

        override fun getLength(): Long = bytes.size.toLong()

        override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
            val count = minOf(bytes.size - offset, byteBuffer.remaining())
            byteBuffer.put(bytes, offset, count)
            offset += count
            uploadDataSink.onReadSucceeded(false)
        }

        override fun rewind(uploadDataSink: UploadDataSink) {
            offset = 0
            uploadDataSink.onRewindSucceeded()
        }
    }

    private enum class NetworkLoadScenario(val path: String, val method: String, val loadCount: Int) {
        FIRST_LOAD(path = "/first-load", method = "GET", loadCount = 1),
        ETAG_CHANGED(path = "/changed", method = "GET", loadCount = 2),
        NO_STORE(path = "/no-store", method = "GET", loadCount = 2),
        POST(path = "/post", method = "POST", loadCount = 2)
    }

    private enum class CacheValidator(
        val responseHeader: String,
        val conditionalRequestHeader: String,
        val value: String
    ) {
        ETAG(responseHeader = "ETag", conditionalRequestHeader = "If-None-Match", value = ETAG_V1),
        LAST_MODIFIED(
            responseHeader = "Last-Modified",
            conditionalRequestHeader = "If-Modified-Since",
            value = LAST_MODIFIED_DATE
        )
    }

    companion object {
        private const val TAG = "CronetCacheDeliveryTest"
        private const val FAKE_CLIENT_TOKEN = "fake-token"
        private const val FAKE_ENV = "integration-test"
        private const val FAKE_APPLICATION_ID = "00000000-0000-0000-0000-000000000000"
        private const val VIEW_KEY = "cronet-cache-delivery"
        private const val VIEW_NAME = "CronetCacheDelivery"
        private const val RESPONSE_BODY = "cached body"
        private const val REQUEST_BODY = "request body"
        private const val ETAG_V1 = "\"v1\""
        private const val ETAG_V2 = "\"v2\""
        private const val LAST_MODIFIED_DATE = "Mon, 16 Nov 2026 10:00:00 GMT"
        private const val CACHE_SIZE_BYTES = 1024L * 1024L
        private const val BUFFER_SIZE = 32 * 1024
        private const val PROVIDER_INSTALL_TIMEOUT_SECONDS = 60L
        private const val REQUEST_TIMEOUT_SECONDS = 10L
        private const val TAKE_REQUEST_TIMEOUT_SECONDS = 5L
        private const val EVENTS_TIMEOUT_SECONDS = 10L

        private fun cacheableResponse() = MockResponse()
            .setResponseCode(200)
            .setBody(RESPONSE_BODY)

        private fun enqueueNetworkLoad(mockServer: MockWebServer, scenario: NetworkLoadScenario) {
            when (scenario) {
                NetworkLoadScenario.FIRST_LOAD -> {
                    mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
                }
                NetworkLoadScenario.ETAG_CHANGED -> {
                    listOf(ETAG_V1, ETAG_V2).forEach { eTag ->
                        mockServer.enqueue(
                            cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", eTag)
                        )
                    }
                }
                NetworkLoadScenario.NO_STORE -> {
                    repeat(2) { mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store")) }
                }
                NetworkLoadScenario.POST -> {
                    repeat(2) { mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60")) }
                }
            }
        }

        private fun enqueueDirectAndRedirectedLoads(mockServer: MockWebServer) {
            mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store"))
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/target")
                    .addHeader("Cache-Control", "no-store")
            )
            mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store"))
        }
    }
}
