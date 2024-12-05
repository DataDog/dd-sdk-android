/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.context.TimeInfo
import com.datadog.android.okhttp.test.elmyr.OkHttpIntegrationForgeConfigurator
import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.okhttp.utils.reset
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.resource.ResourceId
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(OkHttpIntegrationForgeConfigurator::class)
internal class DatadogEventListenerTest {

    private lateinit var testedListener: EventListener

    @Mock
    lateinit var mockCall: Call

    @Forgery
    lateinit var fakeKey: ResourceId

    @StringForgery(regex = "[a-z]+\\.[a-z]{3}")
    lateinit var fakeDomain: String

    @LongForgery(min = 1L)
    var fakeByteCount: Long = 1L

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @BeforeEach
    fun `set up`() {
        fakeRequest = Request.Builder().get().url("https://$fakeDomain/").build()
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("lorem ipsum dolor sit amet…")
            .build()

        whenever(rumMonitor.mockSdkCore.time).thenReturn(
            stubTimeInfo(deviceTimeNs = 0),
            // 30 here is just a big enough value - the amount of
            // mocked method values that should be enough for all invocations
            // of [SdkCore.time] property
            *Array(30) {
                stubTimeInfo(deviceTimeNs = (it + 1) * SHORT_SLEEP_NS)
            }
        )

        testedListener = DatadogEventListener(rumMonitor.mockSdkCore, fakeKey)
    }

    @Test
    fun `M call waitForTiming() W callStart()`() {
        // When
        testedListener.callStart(mockCall)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
        verifyNoMoreInteractions(rumMonitor.mockInstance, mockCall)
    }

    @Test
    fun `M send timing info W responseHeadersEnd() for failing request`(
        @IntForgery(400, 600) statusCode: Int
    ) {
        // Given
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("lorem ipsum dolor sit amet…")
            .build()

        // When
        testedListener.callStart(mockCall)
        testedListener.dnsStart(mockCall, fakeDomain)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        testedListener.secureConnectStart(mockCall)
        testedListener.secureConnectEnd(mockCall, null)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        testedListener.responseHeadersStart(mockCall)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(5)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            // All timestamp and durations are positive
            assertThat(timing.dnsStart).isGreaterThan(0L)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
            assertThat(timing.connectStart).isGreaterThan(0L)
            assertThat(timing.connectDuration).isGreaterThan(0L)
            assertThat(timing.sslStart).isGreaterThan(0L)
            assertThat(timing.sslDuration).isGreaterThan(0L)
            assertThat(timing.firstByteStart).isGreaterThan(0L)
            assertThat(timing.firstByteDuration).isGreaterThan(0L)
            // Header shows failure, there's no download
            assertThat(timing.downloadStart).isEqualTo(0L)
            assertThat(timing.downloadDuration).isEqualTo(0L)

            // All start timings are consistent
            assertThat(timing.connectStart).isGreaterThan(timing.dnsStart + timing.dnsDuration)
            assertThat(timing.sslStart).isGreaterThan(timing.connectStart)
            assertThat(timing.firstByteStart).isGreaterThan(timing.connectStart + timing.connectDuration)
            assertThat(timing.sslDuration).isLessThan(timing.connectDuration)

            // All durations are consistent
            assertThat(timing.dnsDuration).isEqualTo(SHORT_SLEEP_NS)
            assertThat(timing.connectDuration).isEqualTo(SHORT_SLEEP_NS * 3)
            assertThat(timing.firstByteDuration).isEqualTo(SHORT_SLEEP_NS)
        }
    }

    @Test
    fun `M send timing info W callEnd() for successful request`() {
        // When
        testedListener.callStart(mockCall)
        testedListener.dnsStart(mockCall, fakeDomain)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        testedListener.secureConnectStart(mockCall)
        testedListener.secureConnectEnd(mockCall, null)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        testedListener.responseHeadersStart(mockCall)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        testedListener.responseBodyStart(mockCall)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        testedListener.callEnd(mockCall)

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(6)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            // All timestamp and durations are positive
            assertThat(timing.dnsStart).isGreaterThan(0L)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
            assertThat(timing.connectStart).isGreaterThan(0L)
            assertThat(timing.connectDuration).isGreaterThan(0L)
            assertThat(timing.sslStart).isGreaterThan(0L)
            assertThat(timing.sslDuration).isGreaterThan(0L)
            assertThat(timing.firstByteStart).isGreaterThan(0L)
            assertThat(timing.firstByteDuration).isGreaterThan(0L)
            assertThat(timing.downloadStart).isGreaterThan(0L)
            assertThat(timing.downloadDuration).isGreaterThan(0L)

            // All start timings and durations are consistent
            assertThat(timing.connectStart).isGreaterThan(timing.dnsStart + timing.dnsDuration)
            assertThat(timing.sslStart).isGreaterThan(timing.connectStart)
            assertThat(timing.firstByteStart).isGreaterThan(timing.connectStart + timing.connectDuration)
            assertThat(timing.downloadStart).isGreaterThan(timing.firstByteStart + timing.firstByteDuration)
            assertThat(timing.sslDuration).isLessThan(timing.connectDuration)

            // All durations are consistent
            assertThat(timing.dnsDuration).isEqualTo(SHORT_SLEEP_NS)
            assertThat(timing.connectDuration).isEqualTo(SHORT_SLEEP_NS * 3)
            assertThat(timing.firstByteDuration).isEqualTo(SHORT_SLEEP_NS)
            assertThat(timing.downloadDuration).isEqualTo(SHORT_SLEEP_NS)
        }
    }

    @Test
    fun `M send timing info W callEnd() for successful request with reused pool`() {
        // When
        testedListener.callStart(mockCall)
        testedListener.responseBodyStart(mockCall)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        testedListener.callEnd(mockCall)

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(2)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            assertThat(timing.dnsStart).isEqualTo(0L)
            assertThat(timing.dnsDuration).isEqualTo(0L)
            assertThat(timing.connectStart).isEqualTo(0L)
            assertThat(timing.connectDuration).isEqualTo(0L)
            assertThat(timing.sslStart).isEqualTo(0L)
            assertThat(timing.sslDuration).isEqualTo(0L)
            assertThat(timing.firstByteStart).isEqualTo(0L)
            assertThat(timing.firstByteDuration).isEqualTo(0L)

            assertThat(timing.downloadStart).isEqualTo(SHORT_SLEEP_NS)
            assertThat(timing.downloadDuration).isEqualTo(SHORT_SLEEP_NS)
        }
    }

    @Test
    fun `M send timing info W callFailed() for throwing request`(
        @StringForgery error: String
    ) {
        // When
        testedListener.callStart(mockCall)
        testedListener.dnsStart(mockCall, fakeDomain)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        testedListener.secureConnectStart(mockCall)
        testedListener.secureConnectEnd(mockCall, null)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        testedListener.responseHeadersStart(mockCall)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        testedListener.responseBodyStart(mockCall)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        testedListener.callFailed(mockCall, IOException(error))

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(6)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            // All timestamp and durations are positive
            assertThat(timing.dnsStart).isGreaterThan(0L)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
            assertThat(timing.connectStart).isGreaterThan(0L)
            assertThat(timing.connectDuration).isGreaterThan(0L)
            assertThat(timing.sslStart).isGreaterThan(0L)
            assertThat(timing.sslDuration).isGreaterThan(0L)
            assertThat(timing.firstByteStart).isGreaterThan(0L)
            assertThat(timing.firstByteDuration).isGreaterThan(0L)
            assertThat(timing.downloadStart).isGreaterThan(0L)
            assertThat(timing.downloadDuration).isGreaterThan(0L)

            // All start timings and durations are consistent
            assertThat(timing.connectStart).isGreaterThan(timing.dnsStart + timing.dnsDuration)
            assertThat(timing.sslStart).isGreaterThan(timing.connectStart)
            assertThat(timing.firstByteStart).isGreaterThan(timing.connectStart + timing.connectDuration)
            assertThat(timing.downloadStart).isGreaterThan(timing.firstByteStart + timing.firstByteDuration)
            assertThat(timing.sslDuration).isLessThan(timing.connectDuration)

            // All durations are consistent
            assertThat(timing.dnsDuration).isEqualTo(SHORT_SLEEP_NS)
            assertThat(timing.connectDuration).isEqualTo(SHORT_SLEEP_NS * 3)
            assertThat(timing.firstByteDuration).isEqualTo(SHORT_SLEEP_NS)
            assertThat(timing.downloadDuration).isEqualTo(SHORT_SLEEP_NS)
        }
    }

    @Test
    fun `M doNothing W call without RumMonitor`() {
        // Given
        GlobalRumMonitor.reset()

        // When
        testedListener.callStart(mockCall)
        testedListener.dnsStart(mockCall, fakeDomain)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        testedListener.secureConnectStart(mockCall)
        testedListener.secureConnectEnd(mockCall, null)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        testedListener.responseHeadersStart(mockCall)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M send WaitForResourceTiming event W callStart`() {
        // When
        testedListener.callStart(mockCall)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
    }

    @Test
    fun `M send WaitForResourceTiming event W dnsStart`() {
        // When
        testedListener.dnsStart(mockCall, fakeDomain)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
    }

    @Test
    fun `M send WaitForResourceTiming event W connectStart`() {
        // When
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
    }

    @Test
    fun `M send WaitForResourceTiming W secureConnectionStart`() {
        // When
        testedListener.secureConnectStart(mockCall)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
    }

    @Test
    fun `M send WaitForResourceTiming event W responseHeadersStart`() {
        // When
        testedListener.responseHeadersStart(mockCall)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
    }

    @Test
    fun `M send WaitForResourceTiming event W responseBodyStart`() {
        // When
        testedListener.responseBodyStart(mockCall)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
    }

    companion object {
        private const val SHORT_SLEEP_NS = 5000000L

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }

        private fun stubTimeInfo(
            deviceTimeNs: Long = 0L,
            serverTimeNs: Long = 0L,
            serverTimeOffsetNs: Long = 0L,
            serverTimeOffsetMs: Long = 0L
        ) = TimeInfo(
            deviceTimeNs = deviceTimeNs,
            serverTimeNs = serverTimeNs,
            serverTimeOffsetNs = serverTimeOffsetNs,
            serverTimeOffsetMs = serverTimeOffsetMs
        )
    }
}
