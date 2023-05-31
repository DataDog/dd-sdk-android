/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.okhttp.utils.reset
import com.datadog.android.rum.GlobalRum
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
        Thread.sleep(10)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(10)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(10)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(10)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(10)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(10)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(10)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(10)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(5)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            assertThat(timing.dnsStart).isGreaterThan(0L).isLessThan(TWENTY_MILLIS_NS)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
            assertThat(timing.connectStart).isGreaterThan(0L)
            assertThat(timing.connectDuration).isGreaterThan(0L)
            assertThat(timing.sslStart).isGreaterThan(0L)
            assertThat(timing.sslDuration).isGreaterThan(0L)
            assertThat(timing.firstByteStart).isGreaterThan(0L)
            assertThat(timing.firstByteDuration).isGreaterThan(0L)
            assertThat(timing.downloadStart).isEqualTo(0L)
            assertThat(timing.downloadDuration).isEqualTo(0L)

            assertThat(timing.connectStart)
                .isGreaterThan(timing.dnsStart + timing.dnsDuration)
            assertThat(timing.sslStart).isGreaterThan(timing.connectStart)
            assertThat(timing.sslDuration).isLessThan(timing.connectDuration)
            assertThat(timing.firstByteStart)
                .isGreaterThan(timing.connectStart + timing.connectDuration)
        }
    }

    @Test
    fun `𝕄 send timing info 𝕎 callEnd() for successful request`() {
        // When
        testedListener.callStart(mockCall)
        Thread.sleep(10)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(10)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(10)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(10)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(10)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(10)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(10)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(10)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        Thread.sleep(10)
        testedListener.responseBodyStart(mockCall)
        Thread.sleep(10)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        Thread.sleep(10)
        testedListener.callEnd(mockCall)

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(6)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            assertThat(timing.dnsStart).isGreaterThan(0L).isLessThan(TWENTY_MILLIS_NS)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
            assertThat(timing.connectStart).isGreaterThan(0L)
            assertThat(timing.connectDuration).isGreaterThan(0L)
            assertThat(timing.sslStart).isGreaterThan(0L)
            assertThat(timing.sslDuration).isGreaterThan(0L)
            assertThat(timing.firstByteStart).isGreaterThan(0L)
            assertThat(timing.firstByteDuration).isGreaterThan(0L)
            assertThat(timing.downloadStart).isGreaterThan(0L)
            assertThat(timing.downloadDuration).isGreaterThan(0L)

            assertThat(timing.connectStart)
                .isGreaterThan(timing.dnsStart + timing.dnsDuration)
            assertThat(timing.sslStart).isGreaterThan(timing.connectStart)
            assertThat(timing.sslDuration).isLessThan(timing.connectDuration)
            assertThat(timing.firstByteStart)
                .isGreaterThan(timing.connectStart + timing.connectDuration)
            assertThat(timing.downloadStart)
                .isGreaterThan(timing.firstByteStart + timing.firstByteDuration)
        }
    }

    @Test
    fun `𝕄 send timing info 𝕎 callEnd() for successful request with reused pool`() {
        // When
        testedListener.callStart(mockCall)
        Thread.sleep(10)
        testedListener.responseBodyStart(mockCall)
        Thread.sleep(10)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        Thread.sleep(10)
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
            assertThat(timing.downloadStart).isGreaterThan(TimeUnit.NANOSECONDS.toMillis(10L))
            assertThat(timing.downloadDuration).isGreaterThan(TimeUnit.NANOSECONDS.toMillis(10L))
        }
    }

    @Test
    fun `𝕄 send timing info 𝕎 callFailed() for throwing request`(
        @StringForgery error: String
    ) {
        // When
        testedListener.callStart(mockCall)
        Thread.sleep(10)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(10)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(10)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(10)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(10)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(10)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(10)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(10)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        Thread.sleep(10)
        testedListener.responseBodyStart(mockCall)
        Thread.sleep(10)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        Thread.sleep(10)
        testedListener.callFailed(mockCall, IOException(error))

        // Then
        argumentCaptor<ResourceTiming> {
            inOrder(rumMonitor.mockInstance, mockCall) {
                verify(rumMonitor.mockInstance, times(6)).waitForResourceTiming(fakeKey)
                verify(rumMonitor.mockInstance).addResourceTiming(eq(fakeKey), capture())
                verifyNoMoreInteractions()
            }

            val timing = firstValue
            assertThat(timing.dnsStart).isGreaterThan(0L).isLessThan(TWENTY_MILLIS_NS)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
            assertThat(timing.connectStart).isGreaterThan(0L)
            assertThat(timing.connectDuration).isGreaterThan(0L)
            assertThat(timing.sslStart).isGreaterThan(0L)
            assertThat(timing.sslDuration).isGreaterThan(0L)
            assertThat(timing.firstByteStart).isGreaterThan(0L)
            assertThat(timing.firstByteDuration).isGreaterThan(0L)
            assertThat(timing.downloadStart).isGreaterThan(0L)
            assertThat(timing.downloadDuration).isGreaterThan(0L)

            assertThat(timing.connectStart)
                .isGreaterThan(timing.dnsStart + timing.dnsDuration)
            assertThat(timing.sslStart).isGreaterThan(timing.connectStart)
            assertThat(timing.sslDuration).isLessThan(timing.connectDuration)
            assertThat(timing.firstByteStart)
                .isGreaterThan(timing.connectStart + timing.connectDuration)
            assertThat(timing.downloadStart)
                .isGreaterThan(timing.firstByteStart + timing.firstByteDuration)
        }
    }

    @Test
    fun `𝕄 doNothing 𝕎 call without RumMonitor`() {
        // Given
        GlobalRum.reset()

        // When
        testedListener.callStart(mockCall)
        Thread.sleep(10)
        testedListener.dnsStart(mockCall, fakeDomain)
        Thread.sleep(10)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        Thread.sleep(10)
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)
        Thread.sleep(10)
        testedListener.secureConnectStart(mockCall)
        Thread.sleep(10)
        testedListener.secureConnectEnd(mockCall, null)
        Thread.sleep(10)
        testedListener.connectEnd(mockCall, InetSocketAddress(0), Proxy.NO_PROXY, Protocol.HTTP_2)
        Thread.sleep(10)
        testedListener.responseHeadersStart(mockCall)
        Thread.sleep(10)
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
        val TWENTY_MILLIS_NS = TimeUnit.MILLISECONDS.toNanos(20)

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
