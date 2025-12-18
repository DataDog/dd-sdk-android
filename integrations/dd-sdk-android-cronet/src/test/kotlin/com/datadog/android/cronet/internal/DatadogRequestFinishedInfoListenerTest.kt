/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.cronet.internal.DatadogRequestFinishedInfoListener.Companion.minus
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.StubRequestFinishedInfoMetrics
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.chromium.net.CronetException
import org.chromium.net.RequestFinishedInfo
import org.chromium.net.UrlResponseInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogRequestFinishedInfoListenerTest {

    @Mock
    private lateinit var mockRumResourceInstrumentation: RumResourceInstrumentation

    @Mock
    private lateinit var mockRequestFinishedInfo: RequestFinishedInfo

    @Mock
    private lateinit var mockResponseInfo: UrlResponseInfo

    @Forgery
    private lateinit var fakeRequestInfo: HttpRequestInfo

    private lateinit var testedListener: DatadogRequestFinishedInfoListener

    @BeforeEach
    fun `set up`() {
        testedListener = DatadogRequestFinishedInfoListener(
            executor = mock(),
            rumResourceInstrumentation = mockRumResourceInstrumentation
        )

        whenever(mockRequestFinishedInfo.responseInfo).thenReturn(mockResponseInfo)
        whenever(mockRequestFinishedInfo.annotations).thenReturn(listOf(fakeRequestInfo))
        whenever(mockRequestFinishedInfo.metrics).thenReturn(mock())
    }

    @Test
    fun `M call stopResourceWithError W onRequestFinished() { FAILED }`() {
        // Given
        val fakeException = mock<CronetException>()
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.FAILED)
        whenever(mockRequestFinishedInfo.exception).thenReturn(fakeException)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        verify(mockRumResourceInstrumentation).stopResourceWithError(
            requestInfo = eq(fakeRequestInfo),
            throwable = eq(fakeException)
        )
    }

    @Test
    fun `M call stopResourceWithError with default IOException W onRequestFinished() { FAILED, null exception }`() {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.FAILED)
        whenever(mockRequestFinishedInfo.exception).thenReturn(null)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        argumentCaptor<IOException> {
            verify(mockRumResourceInstrumentation).stopResourceWithError(
                requestInfo = eq(fakeRequestInfo),
                throwable = capture()
            )
            assertThat(firstValue).hasMessage("Request failed")
        }
    }

    @Test
    fun `M call stopResourceWithError W onRequestFinished() { CANCELED }`() {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.CANCELED)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        argumentCaptor<IOException> {
            verify(mockRumResourceInstrumentation).stopResourceWithError(
                requestInfo = eq(fakeRequestInfo),
                throwable = capture()
            )
            assertThat(firstValue).hasMessage("Request was cancelled")
        }
    }

    @Test
    fun `M call stopResource W onRequestFinished() { SUCCEEDED }`() {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.SUCCEEDED)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        verify(mockRumResourceInstrumentation).stopResource(
            requestInfo = fakeRequestInfo,
            responseInfo = CronetHttpResponseInfo(mockResponseInfo)
        )
    }

    @Test
    fun `M call stopResourceWithError W onRequestFinished() { SUCCEEDED, null responseInfo }`() {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.SUCCEEDED)
        whenever(mockRequestFinishedInfo.responseInfo).thenReturn(null)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        argumentCaptor<IllegalStateException> {
            verify(mockRumResourceInstrumentation).stopResourceWithError(
                requestInfo = eq(fakeRequestInfo),
                throwable = capture()
            )

            assertThat(firstValue).hasMessage("Received null response")
        }
    }

    @Test
    fun `M call sendTiming W onRequestFinished()`() {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.SUCCEEDED)
        whenever(mockRequestFinishedInfo.responseInfo).thenReturn(mockResponseInfo)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        verify(mockRumResourceInstrumentation).sendTiming(
            eq(fakeRequestInfo),
            any<ResourceTiming>()
        )
    }

    @Test
    fun `M call reportInstrumentationError W onRequestFinished() { empty annotations }`(
        forge: Forge
    ) {
        // Given
        whenever(mockRequestFinishedInfo.annotations).thenReturn(forge.aNullable { emptyList() })

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        verify(mockRumResourceInstrumentation).reportInstrumentationError(NO_REQUEST_INFO_MESSAGE)
    }

    @Test
    fun `M build correct ResourceTiming W onRequestFinished() { all metrics present }`(
        @Forgery fakeMetrics: RequestFinishedInfo.Metrics
    ) {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.SUCCEEDED)
        whenever(mockRequestFinishedInfo.responseInfo).thenReturn(mockResponseInfo)
        whenever(mockRequestFinishedInfo.metrics).thenReturn(fakeMetrics)

        val expectedConnectStart = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.connectStart - fakeMetrics.requestStart)
        val expectedConnectDuration = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.connectEnd - fakeMetrics.connectStart)
        val expectedDnsStart = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.dnsStart - fakeMetrics.requestStart)
        val expectedDnsDuration = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.dnsEnd - fakeMetrics.dnsStart)
        val expectedSslStart = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.sslStart - fakeMetrics.requestStart)
        val expectedSslDuration = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.sslEnd - fakeMetrics.sslStart)

        val timeToFirstHeaderByteMs = fakeMetrics.ttfbMs ?: 0L
        val responseStartMs = fakeMetrics.responseStart?.time ?: 0L
        val requestStartMs = fakeMetrics.requestStart?.time ?: 0L
        val headersFetchingStartedMs = if (timeToFirstHeaderByteMs > 0) timeToFirstHeaderByteMs else 0
        val headersFetchDurationMs = if (headersFetchingStartedMs > 0 && requestStartMs > 0 && responseStartMs > 0) {
            responseStartMs - (requestStartMs + timeToFirstHeaderByteMs)
        } else {
            0
        }
        val expectedFirstByteStart = TimeUnit.MILLISECONDS.toNanos(headersFetchingStartedMs)
        val expectedFirstByteDuration = TimeUnit.MILLISECONDS.toNanos(headersFetchDurationMs)

        val expectedDownloadStart = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.responseStart - fakeMetrics.requestStart)
        val expectedDownloadDuration = TimeUnit.MILLISECONDS.toNanos(fakeMetrics.requestEnd - fakeMetrics.responseStart)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        argumentCaptor<ResourceTiming> {
            verify(mockRumResourceInstrumentation).sendTiming(eq(fakeRequestInfo), capture())
            assertThat(firstValue.connectStart).isEqualTo(expectedConnectStart)
            assertThat(firstValue.connectDuration).isEqualTo(expectedConnectDuration)
            assertThat(firstValue.dnsStart).isEqualTo(expectedDnsStart)
            assertThat(firstValue.dnsDuration).isEqualTo(expectedDnsDuration)
            assertThat(firstValue.sslStart).isEqualTo(expectedSslStart)
            assertThat(firstValue.sslDuration).isEqualTo(expectedSslDuration)
            assertThat(firstValue.firstByteStart).isEqualTo(expectedFirstByteStart)
            assertThat(firstValue.firstByteDuration).isEqualTo(expectedFirstByteDuration)
            assertThat(firstValue.downloadStart).isEqualTo(expectedDownloadStart)
            assertThat(firstValue.downloadDuration).isEqualTo(expectedDownloadDuration)
        }
    }

    @Test
    fun `M build ResourceTiming with zero values W onRequestFinished() { null metrics }`() {
        // Given
        whenever(mockRequestFinishedInfo.finishedReason).thenReturn(RequestFinishedInfo.SUCCEEDED)
        whenever(mockRequestFinishedInfo.responseInfo).thenReturn(mockResponseInfo)
        whenever(mockRequestFinishedInfo.metrics).thenReturn(StubRequestFinishedInfoMetrics.NULL_METRICS)

        // When
        testedListener.onRequestFinished(mockRequestFinishedInfo)

        // Then
        argumentCaptor<ResourceTiming> {
            verify(mockRumResourceInstrumentation).sendTiming(eq(fakeRequestInfo), capture())

            assertThat(firstValue.connectStart).isEqualTo(0L)
            assertThat(firstValue.connectDuration).isEqualTo(0L)
            assertThat(firstValue.dnsStart).isEqualTo(0L)
            assertThat(firstValue.dnsDuration).isEqualTo(0L)
            assertThat(firstValue.sslStart).isEqualTo(0L)
            assertThat(firstValue.sslDuration).isEqualTo(0L)
            assertThat(firstValue.firstByteStart).isEqualTo(0L)
            assertThat(firstValue.firstByteDuration).isEqualTo(0L)
            assertThat(firstValue.downloadStart).isEqualTo(0L)
            assertThat(firstValue.downloadDuration).isEqualTo(0L)
        }
    }

    companion object {
        private const val NO_REQUEST_INFO_MESSAGE = "Unable to instrument RUM resource without the request info"
    }
}
