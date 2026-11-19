/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.tests

import android.content.Context
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
import com.datadog.android.trace.ExperimentalTraceApi
import com.google.android.gms.net.CronetProviderInstaller
import com.google.android.gms.tasks.Tasks
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val writtenResources = ConcurrentLinkedQueue<ResourceEvent>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

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
                writtenResources.add(event)
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
        // setUp can fail half way, and a failing engine shutdown must not skip the rest of the cleanup
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
    fun mustNotReportCacheDelivery_whenFirstLoad() {
        // Given
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))

        // When
        val response = execute("/first-load")

        // Then
        val resource = awaitResources(1).single()
        assertNoCacheDelivery(resource, response)
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
        val resource = awaitResources(2)[1]
        assertThat(resource.resource.deliveryType).describedAs(response.toString())
            .isEqualTo(ResourceEvent.DeliveryType.CACHE)
        assertThat(resource.resource.transferSize).describedAs(response.toString()).isEqualTo(0L)
        assertThat(resource.resource.statusCode).describedAs(response.toString()).isEqualTo(200L)
    }

    @Test
    fun mustReportServerValidatedCacheHit_whenSecondLoad_eTag304() {
        // Given
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(MockResponse().setResponseCode(304).addHeader("ETag", ETAG_V1))
        execute("/validated")

        // When
        val response = execute("/validated")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val validationRequest = checkNotNull(mockServer.takeRequest(TAKE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Expected a validation request"
        }
        assertThat(validationRequest.getHeader("If-None-Match")).isEqualTo(ETAG_V1)
        val resource = awaitResources(2)[1]
        assertThat(resource.resource.deliveryType).describedAs(response.toString())
            .isEqualTo(ResourceEvent.DeliveryType.CACHE)
        assertThat(resource.resource.transferSize).describedAs(response.toString()).isNotNull().isPositive()
    }

    @Test
    fun mustReportServerValidatedCacheHit_whenSecondLoad_staleEntryLastModified304() {
        // Given
        mockServer.enqueue(
            cacheableResponse().addHeader("Cache-Control", "max-age=0").addHeader("Last-Modified", LAST_MODIFIED)
        )
        mockServer.enqueue(MockResponse().setResponseCode(304))
        execute("/last-modified")

        // When
        val response = execute("/last-modified")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        val resource = awaitResources(2)[1]
        assertThat(resource.resource.deliveryType).describedAs(response.toString())
            .isEqualTo(ResourceEvent.DeliveryType.CACHE)
        assertThat(resource.resource.transferSize).describedAs(response.toString()).isNotNull().isPositive()
    }

    @Test
    fun mustNotReportCacheDelivery_whenSecondLoad_eTagChanged200() {
        // Given
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V1))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-cache").addHeader("ETag", ETAG_V2))
        execute("/changed")

        // When
        val response = execute("/changed")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        assertNoCacheDelivery(awaitResources(2)[1], response)
    }

    @Test
    fun mustNotReportCacheDelivery_whenSecondLoad_noStore() {
        // Given
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "no-store"))
        execute("/no-store")

        // When
        val response = execute("/no-store")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        assertNoCacheDelivery(awaitResources(2)[1], response)
    }

    @Test
    fun mustNotReportCacheDelivery_whenSecondLoad_post() {
        // Given
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        mockServer.enqueue(cacheableResponse().addHeader("Cache-Control", "max-age=60"))
        execute("/post", method = "POST")

        // When
        val response = execute("/post", method = "POST")

        // Then
        assertThat(mockServer.requestCount).describedAs(response.toString()).isEqualTo(2)
        val resource = awaitResources(2)[1]
        assertThat(resource.resource.method).isEqualTo(ResourceEvent.Method.POST)
        assertNoCacheDelivery(resource, response)
    }

    // region Helpers

    private fun cacheableResponse() = MockResponse()
        .setResponseCode(200)
        .setBody(RESPONSE_BODY)

    private fun execute(path: String, method: String = "GET"): ObservedResponse {
        val resourcesBefore = writtenResources.size
        val latch = CountDownLatch(1)
        var observed: ObservedResponse? = null
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
                observed = ObservedResponse(
                    path = path,
                    statusCode = info.httpStatusCode,
                    wasCached = info.wasCached(),
                    receivedByteCount = info.receivedByteCount
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
        if (method == "POST") {
            requestBuilder.addHeader("Content-Type", "text/plain")
            requestBuilder.setUploadDataProvider(ByteArrayUploadDataProvider(REQUEST_BODY.toByteArray()), executor)
        }
        val urlRequest = requestBuilder.build()
        urlRequest.start()

        if (!latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            // an active request makes engine.shutdown() fail: cancel it and wait for onCanceled
            urlRequest.cancel()
            latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            throw AssertionError("Request to $path timed out")
        }
        failure?.let { throw AssertionError("Request to $path failed", it) }
        val response = checkNotNull(observed) { "Request to $path was cancelled" }
        // the RUM resource is reported from a separate request-finished listener: wait for it, so
        // that the events are written in the order of the requests
        awaitResources(resourcesBefore + 1)
        return response
    }

    private fun awaitResources(count: Int): List<ResourceEvent> {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(EVENTS_TIMEOUT_SECONDS)
        while (writtenResources.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
        val resources = writtenResources.toList()
        assertThat(resources).hasSize(count)
        return resources
    }

    private fun assertNoCacheDelivery(resource: ResourceEvent, response: ObservedResponse) {
        assertThat(resource.resource.deliveryType).describedAs(response.toString()).isNull()
        assertThat(resource.resource.transferSize).describedAs(response.toString()).isNull()
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

    private data class ObservedResponse(
        val path: String,
        val statusCode: Int,
        val wasCached: Boolean,
        val receivedByteCount: Long
    )

    companion object {
        private const val FAKE_CLIENT_TOKEN = "fake-token"
        private const val FAKE_ENV = "integration-test"
        private const val FAKE_APPLICATION_ID = "00000000-0000-0000-0000-000000000000"
        private const val VIEW_KEY = "cronet-cache-delivery"
        private const val VIEW_NAME = "CronetCacheDelivery"
        private const val RESPONSE_BODY = "cached body"
        private const val REQUEST_BODY = "request body"
        private const val ETAG_V1 = "\"v1\""
        private const val ETAG_V2 = "\"v2\""
        private const val LAST_MODIFIED = "Mon, 16 Nov 2026 10:00:00 GMT"
        private const val CACHE_SIZE_BYTES = 1024L * 1024L
        private const val BUFFER_SIZE = 32 * 1024
        private const val PROVIDER_INSTALL_TIMEOUT_SECONDS = 60L
        private const val REQUEST_TIMEOUT_SECONDS = 10L
        private const val TAKE_REQUEST_TIMEOUT_SECONDS = 5L
        private const val EVENTS_TIMEOUT_SECONDS = 10L
        private const val POLL_INTERVAL_MS = 50L
    }
}
