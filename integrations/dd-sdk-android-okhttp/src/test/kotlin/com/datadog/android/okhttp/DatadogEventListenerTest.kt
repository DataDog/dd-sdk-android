/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.okhttp.utils.reset
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class DatadogEventListenerTest {

    lateinit var testedListener: EventListener

    @Mock
    lateinit var mockCall: Call

    @StringForgery(type = StringForgeryType.ASCII)
    lateinit var fakeKey: String

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

        testedListener = DatadogEventListener(rumMonitor.mockSdkCore, fakeKey)
    }

    @Test
    fun `𝕄 call waitForTiming() 𝕎 callStart()`() {
        // When
        testedListener.callStart(mockCall)

        // Then
        verify(rumMonitor.mockInstance).waitForResourceTiming(fakeKey)
        verifyNoMoreInteractions(rumMonitor.mockInstance, mockCall)
    }

    @Test
    fun `𝕄 send timing info 𝕎 responseHeadersEnd() for failing request`(
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
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
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
            assertThat(timing.dnsDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
            assertThat(timing.connectDuration).isLessThan((SHORT_SLEEP_NS * 3) + MARGIN_NS)
            assertThat(timing.firstByteDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
        }
    }

    @Test
    fun `𝕄 send timing info 𝕎 callEnd() for successful request`() {
        // When
        testedListener.callStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseBodyStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        Thread.sleep(SHORT_SLEEP_MS)
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
            assertThat(timing.dnsDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
            assertThat(timing.connectDuration).isLessThan((SHORT_SLEEP_NS * 3) + MARGIN_NS)
            assertThat(timing.firstByteDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
            assertThat(timing.downloadDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
        }
    }

    @Test
    fun `𝕄 send timing info 𝕎 callEnd() for successful request with reused pool`() {
        // When
        testedListener.callStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseBodyStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        Thread.sleep(SHORT_SLEEP_MS)
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

            assertThat(timing.downloadStart).isGreaterThan(SHORT_SLEEP_NS)
            assertThat(timing.downloadDuration).isGreaterThan(SHORT_SLEEP_NS)
                .isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
        }
    }

    @Test
    fun `𝕄 send timing info 𝕎 callFailed() for throwing request`(
        @StringForgery error: String
    ) {
        // When
        testedListener.callStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseBodyStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        Thread.sleep(SHORT_SLEEP_MS)
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
            assertThat(timing.dnsDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
            assertThat(timing.connectDuration).isLessThan((SHORT_SLEEP_NS * 3) + MARGIN_NS)
            assertThat(timing.firstByteDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
            assertThat(timing.downloadDuration).isLessThan(SHORT_SLEEP_NS + MARGIN_NS)
        }
    }

    @Test
    fun `𝕄 doNothing 𝕎 call without RumMonitor`() {
        // Given
        GlobalRumMonitor.reset()

        // When
        testedListener.callStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(SHORT_SLEEP_MS)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(SHORT_SLEEP_MS)
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
        private const val SHORT_SLEEP_MS = 5L

        private val SHORT_SLEEP_NS = TimeUnit.MILLISECONDS.toNanos(SHORT_SLEEP_MS)

        // Because the threading can be random sometimes, we need a margin for our timing assertions
        // If the tests turn flaky again, we can increase this value
        // TODO RUM-3845 let the DatdogEventListener use the SDKCore's time info to have more reliable assertions
        private val MARGIN_NS = TimeUnit.MILLISECONDS.toNanos(60)

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
