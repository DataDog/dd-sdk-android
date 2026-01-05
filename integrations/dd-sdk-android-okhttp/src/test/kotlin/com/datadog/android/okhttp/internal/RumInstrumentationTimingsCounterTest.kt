/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.api.SdkCore
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpRequestInfoBuilder
import com.datadog.android.core.SdkReference
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RumInstrumentationTimingsCounterTest {

    private lateinit var testedListener: RumInstrumentationTimingsCounter

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockRumNetworkInstrumentation: RumNetworkInstrumentation

    @Mock
    lateinit var mockRequestInfoRegistry: RequestTracingStateRegistry

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockRequestInfo: HttpRequestInfo

    @Mock
    lateinit var mockRequestBuilder: HttpRequestInfoBuilder

    @StringForgery(regex = "[a-z]+\\.[a-z]{3}")
    lateinit var fakeDomain: String

    @LongForgery(min = 1L)
    var fakeByteCount: Long = 1L

    private lateinit var fakeRequest: Request
    private lateinit var fakeResponse: Response

    @BeforeEach
    fun `set up`() {
        fakeRequest = Request.Builder().get().url("https://$fakeDomain/").build()
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .build()

        whenever(mockRequestBuilder.build()) doReturn mockRequestInfo
        val fakeTracingState = RequestTracingState(tracedRequestInfoBuilder = mockRequestBuilder)
        whenever(mockRequestInfoRegistry.get(mockCall)) doReturn fakeTracingState

        whenever(mockSdkCore.time).thenReturn(
            stubTimeInfo(deviceTimeNs = 0),
            *Array(30) {
                stubTimeInfo(deviceTimeNs = (it + 1) * STEP_NS)
            }
        )

        testedListener = RumInstrumentationTimingsCounter(
            mockSdkCore,
            mockRumNetworkInstrumentation,
            mockRequestInfoRegistry
        )
    }

    @Test
    fun `M send waitForResourceTiming W callStart()`() {
        // When
        testedListener.callStart(mockCall)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
    }

    @Test
    fun `M send waitForResourceTiming W dnsStart()`() {
        // When
        testedListener.dnsStart(mockCall, fakeDomain)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
    }

    @Test
    fun `M send waitForResourceTiming W connectStart()`() {
        // When
        testedListener.connectStart(mockCall, InetSocketAddress(0), Proxy.NO_PROXY)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
    }

    @Test
    fun `M send waitForResourceTiming W secureConnectStart()`() {
        // When
        testedListener.secureConnectStart(mockCall)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
    }

    @Test
    fun `M send waitForResourceTiming W responseHeadersStart()`() {
        // When
        testedListener.responseHeadersStart(mockCall)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
    }

    @Test
    fun `M send waitForResourceTiming W responseBodyStart()`() {
        // When
        testedListener.responseBodyStart(mockCall)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
    }

    @Test
    fun `M not send waitForResourceTiming W callStart() { no request info }`() {
        // Given
        whenever(mockRequestInfoRegistry.get(mockCall)) doReturn null

        // When
        testedListener.callStart(mockCall)

        // Then
        verifyNoInteractions(mockRumNetworkInstrumentation)
    }

    @Test
    fun `M send timing W callEnd() { full request lifecycle }`() {
        // Given
        whenever(mockRequestInfoRegistry.remove(mockCall)) doReturn
            RequestTracingState(tracedRequestInfoBuilder = mockRequestBuilder)

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
            verify(mockRumNetworkInstrumentation).sendTiming(eq(mockRequestInfo), capture())

            val timing = firstValue
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
        }
    }

    @Test
    fun `M send timing W callFailed()`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        whenever(mockRequestInfoRegistry.remove(mockCall)) doReturn
            RequestTracingState(tracedRequestInfoBuilder = mockRequestBuilder)

        // When
        testedListener.callStart(mockCall)
        testedListener.dnsStart(mockCall, fakeDomain)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        testedListener.callFailed(mockCall, IOException(fakeErrorMessage))

        // Then
        argumentCaptor<ResourceTiming> {
            verify(mockRumNetworkInstrumentation).sendTiming(eq(mockRequestInfo), capture())

            val timing = firstValue
            assertThat(timing.dnsStart).isGreaterThan(0L)
            assertThat(timing.dnsDuration).isGreaterThan(0L)
        }
    }

    @Test
    fun `M send timing W responseHeadersEnd() { error status code }`(
        @IntForgery(min = 400, max = 600) statusCode: Int
    ) {
        // Given
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(statusCode)
            .message("Error")
            .build()

        // When
        testedListener.callStart(mockCall)
        testedListener.responseHeadersStart(mockCall)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)

        // Then
        argumentCaptor<ResourceTiming> {
            verify(mockRumNetworkInstrumentation).sendTiming(eq(mockRequestInfo), capture())
        }
    }

    @Test
    fun `M send timing with zeroes for missing phases W callEnd() { reused connection }`() {
        // Given
        whenever(mockRequestInfoRegistry.remove(mockCall)) doReturn
                RequestTracingState(tracedRequestInfoBuilder = mockRequestBuilder)

        // When
        testedListener.callStart(mockCall)
        testedListener.responseBodyStart(mockCall)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        testedListener.callEnd(mockCall)

        // Then
        argumentCaptor<ResourceTiming> {
            verify(mockRumNetworkInstrumentation).sendTiming(eq(mockRequestInfo), capture())

            val timing = firstValue
            assertThat(timing.dnsStart).isEqualTo(0L)
            assertThat(timing.dnsDuration).isEqualTo(0L)
            assertThat(timing.connectStart).isEqualTo(0L)
            assertThat(timing.connectDuration).isEqualTo(0L)
            assertThat(timing.sslStart).isEqualTo(0L)
            assertThat(timing.sslDuration).isEqualTo(0L)
            assertThat(timing.firstByteStart).isEqualTo(0L)
            assertThat(timing.firstByteDuration).isEqualTo(0L)
            assertThat(timing.downloadStart).isGreaterThan(0L)
            assertThat(timing.downloadDuration).isGreaterThan(0L)
        }
    }

    @Test
    fun `M not send timing W callEnd() { no request info in registry }`() {
        // Given
        whenever(mockRequestInfoRegistry.remove(mockCall)) doReturn null

        // When
        testedListener.callStart(mockCall)
        testedListener.callEnd(mockCall)

        // Then
        verify(mockRumNetworkInstrumentation).sendWaitForResourceTimingEvent(mockRequestInfo)
        // sendTiming should NOT be called since unregister returned null
    }

    @Test
    fun `M register call and create listener W Factory create() { sdkCore available }`() {
        // Given
        val mockSdkReference = mock<SdkReference> {
            on { get() } doReturn mockSdkCore
        }
        whenever(mockRumNetworkInstrumentation.sdkCoreReference) doReturn mockSdkReference
        val factory = RumInstrumentationTimingsCounter.Factory(
            mockRumNetworkInstrumentation,
            mockRequestInfoRegistry
        )

        // When
        val listener = factory.create(mockCall)

        // Then
        assertThat(listener).isInstanceOf(RumInstrumentationTimingsCounter::class.java)
    }

    @Test
    fun `M return no-op listener W Factory create() { sdkCore not available }`() {
        // Given
        val mockSdkReference = mock<SdkReference> {
            on { get() } doReturn null
        }
        whenever(mockRumNetworkInstrumentation.sdkCoreReference) doReturn mockSdkReference
        val factory = RumInstrumentationTimingsCounter.Factory(
            mockRumNetworkInstrumentation,
            mockRequestInfoRegistry
        )
        whenever(mockCall.request()) doReturn fakeRequest

        // When
        val listener = factory.create(mockCall)

        // Then
        assertThat(listener).isSameAs(RumInstrumentationTimingsCounter.Factory.NO_OP_EVENT_LISTENER)
    }

    companion object {
        private const val STEP_NS = 5_000_000L

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
