/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
internal class PrecomputedAssignmentsDownloaderNetworkTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRequestFactory: PrecomputedAssignmentsRequestFactory

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    private lateinit var mockWebServer: MockWebServer
    private val evaluationContext = EvaluationContext("target")

    @BeforeEach
    fun `set up`() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val request = Request.Builder()
            .url(mockWebServer.url("/precompute-assignments"))
            .build()
        whenever(mockRequestFactory.create(evaluationContext, mockDatadogContext)).thenReturn(request)
    }

    @AfterEach
    fun `tear down`() {
        mockWebServer.shutdown()
    }

    @Test
    fun `M time out while reading response body W explicit SDK timeout`() {
        // Given
        mockWebServer.enqueue(delayedBodyResponse())
        val callFactory = OkHttpClient.Builder()
            .callTimeout(LONG_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val downloader = createDownloader(callFactory, EXPLICIT_SDK_TIMEOUT_MS)

        // When
        val result = downloader.readPrecomputedFlags(evaluationContext, mockDatadogContext)

        // Then
        assertThat(result).isNull()
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `M preserve client timeout while reading response body W SDK timeout disabled`() {
        // Given
        mockWebServer.enqueue(delayedBodyResponse())
        val callFactory = OkHttpClient.Builder()
            .callTimeout(CUSTOM_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val downloader = createDownloader(callFactory, requestTimeoutMs = 0L)

        // When
        val result = downloader.readPrecomputedFlags(evaluationContext, mockDatadogContext)

        // Then
        assertThat(result).isNull()
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `M allow delayed response body W SDK timeout disabled and client timeout permits it`() {
        // Given
        mockWebServer.enqueue(delayedBodyResponse(NO_SDK_TIMEOUT_BODY_DELAY_MS))
        val callFactory = OkHttpClient.Builder()
            .callTimeout(LONG_CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val downloader = createDownloader(callFactory, requestTimeoutMs = 0L)

        // When
        val result = downloader.readPrecomputedFlags(evaluationContext, mockDatadogContext)

        // Then
        assertThat(result).isEqualTo(RESPONSE_BODY)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    private fun delayedBodyResponse(bodyDelayMs: Long = BODY_DELAY_MS): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(RESPONSE_BODY)
        .setBodyDelay(bodyDelayMs, TimeUnit.MILLISECONDS)

    private fun createDownloader(
        callFactory: Call.Factory,
        requestTimeoutMs: Long
    ) = PrecomputedAssignmentsDownloader(
        callFactory = callFactory,
        internalLogger = mockInternalLogger,
        requestFactory = mockRequestFactory,
        requestTimeoutMs = requestTimeoutMs,
        requestRetryCount = 0
    )

    private companion object {
        const val RESPONSE_BODY = "{\"flags\":{}}"
        const val EXPLICIT_SDK_TIMEOUT_MS = 200L
        const val CUSTOM_CLIENT_TIMEOUT_MS = 200L
        const val LONG_CLIENT_TIMEOUT_MS = 5_000L
        const val BODY_DELAY_MS = 2_000L
        const val NO_SDK_TIMEOUT_BODY_DELAY_MS = 500L
    }
}
