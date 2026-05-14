/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RegistryTrackingEventListenerTest {

    @Mock
    lateinit var mockDelegateFactory: EventListener.Factory

    @Mock
    lateinit var mockDelegate: EventListener

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockConnection: Connection

    @Mock
    lateinit var mockHandshake: Handshake

    @Mock
    lateinit var mockRequest: Request

    @Mock
    lateinit var mockResponse: Response

    @Mock
    lateinit var mockHttpUrl: HttpUrl

    @Mock
    lateinit var mockRegistry: RequestTracingStateRegistry

    private lateinit var testedFactory: RegistryTrackingEventListener.Factory

    @BeforeEach
    fun `set up`() {
        whenever(mockDelegateFactory.create(mockCall)) doReturn mockDelegate
        testedFactory = RegistryTrackingEventListener.Factory(mockRegistry, mockDelegateFactory)
    }

    @Test
    fun `M register call in registry W create()`() {
        // When
        testedFactory.create(mockCall)

        // Then
        verify(mockRegistry).register(mockCall)
    }

    @Test
    fun `M delegate to inner factory W create()`() {
        // When
        testedFactory.create(mockCall)

        // Then
        verify(mockDelegateFactory).create(mockCall)
    }

    @Test
    fun `M clean up registry W create() { delegate factory throws }`() {
        // Given
        val fakeException = IllegalStateException("test")
        whenever(mockDelegateFactory.create(mockCall)).thenThrow(fakeException)

        // When + Then
        assertThatThrownBy { testedFactory.create(mockCall) }
            .isSameAs(fakeException)
        verify(mockRegistry).remove(mockCall)
    }

    @Test
    fun `M clean up registry W callEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.callEnd(mockCall)

        // Then
        verify(mockRegistry).remove(mockCall)
    }

    @Test
    fun `M clean up registry W callEnd() { delegate throws }`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val fakeException = IllegalStateException("test")
        whenever(mockDelegate.callEnd(mockCall)).thenThrow(fakeException)

        // When + Then
        assertThatThrownBy { listener.callEnd(mockCall) }
            .isSameAs(fakeException)
        verify(mockRegistry).remove(mockCall)
    }

    @Test
    fun `M clean up registry W callFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.callFailed(mockCall, ioe)

        // Then
        verify(mockRegistry).remove(mockCall)
    }

    @Test
    fun `M clean up registry W callFailed() { delegate throws }`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")
        val fakeException = IllegalStateException("test")
        whenever(mockDelegate.callFailed(mockCall, ioe)).thenThrow(fakeException)

        // When + Then
        assertThatThrownBy { listener.callFailed(mockCall, ioe) }
            .isSameAs(fakeException)
        verify(mockRegistry).remove(mockCall)
    }

    @Test
    fun `M delegate W callStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.callStart(mockCall)

        // Then
        verify(mockDelegate).callStart(mockCall)
    }

    @Test
    fun `M delegate W proxySelectStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.proxySelectStart(mockCall, mockHttpUrl)

        // Then
        verify(mockDelegate).proxySelectStart(mockCall, mockHttpUrl)
    }

    @Test
    fun `M delegate W proxySelectEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val proxies = listOf(Proxy.NO_PROXY)

        // When
        listener.proxySelectEnd(mockCall, mockHttpUrl, proxies)

        // Then
        verify(mockDelegate).proxySelectEnd(mockCall, mockHttpUrl, proxies)
    }

    @Test
    fun `M delegate W dnsStart()`(
        @StringForgery fakeDomainName: String
    ) {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.dnsStart(mockCall, fakeDomainName)

        // Then
        verify(mockDelegate).dnsStart(mockCall, fakeDomainName)
    }

    @Test
    fun `M delegate W dnsEnd()`(
        @StringForgery fakeDomainName: String
    ) {
        // Given
        val listener = testedFactory.create(mockCall)
        val inetAddressList = emptyList<InetAddress>()

        // When
        listener.dnsEnd(mockCall, fakeDomainName, inetAddressList)

        // Then
        verify(mockDelegate).dnsEnd(mockCall, fakeDomainName, inetAddressList)
    }

    @Test
    fun `M delegate W connectStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val socketAddress = InetSocketAddress(0)
        val proxy = Proxy.NO_PROXY

        // When
        listener.connectStart(mockCall, socketAddress, proxy)

        // Then
        verify(mockDelegate).connectStart(mockCall, socketAddress, proxy)
    }

    @Test
    fun `M delegate W connectEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val socketAddress = InetSocketAddress(0)
        val proxy = Proxy.NO_PROXY
        val protocol = Protocol.HTTP_2

        // When
        listener.connectEnd(mockCall, socketAddress, proxy, protocol)

        // Then
        verify(mockDelegate).connectEnd(mockCall, socketAddress, proxy, protocol)
    }

    @Test
    fun `M delegate W connectFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val socketAddress = InetSocketAddress(0)
        val proxy = Proxy.NO_PROXY
        val protocol = Protocol.HTTP_2
        val ioe = IOException("test")

        // When
        listener.connectFailed(mockCall, socketAddress, proxy, protocol, ioe)

        // Then
        verify(mockDelegate).connectFailed(mockCall, socketAddress, proxy, protocol, ioe)
    }

    @Test
    fun `M delegate W secureConnectStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.secureConnectStart(mockCall)

        // Then
        verify(mockDelegate).secureConnectStart(mockCall)
    }

    @Test
    fun `M delegate W secureConnectEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.secureConnectEnd(mockCall, mockHandshake)

        // Then
        verify(mockDelegate).secureConnectEnd(mockCall, mockHandshake)
    }

    @Test
    fun `M delegate W connectionAcquired()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.connectionAcquired(mockCall, mockConnection)

        // Then
        verify(mockDelegate).connectionAcquired(mockCall, mockConnection)
    }

    @Test
    fun `M delegate W connectionReleased()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.connectionReleased(mockCall, mockConnection)

        // Then
        verify(mockDelegate).connectionReleased(mockCall, mockConnection)
    }

    @Test
    fun `M delegate W requestHeadersStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestHeadersStart(mockCall)

        // Then
        verify(mockDelegate).requestHeadersStart(mockCall)
    }

    @Test
    fun `M delegate W requestHeadersEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestHeadersEnd(mockCall, mockRequest)

        // Then
        verify(mockDelegate).requestHeadersEnd(mockCall, mockRequest)
    }

    @Test
    fun `M delegate W requestBodyStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestBodyStart(mockCall)

        // Then
        verify(mockDelegate).requestBodyStart(mockCall)
    }

    @Test
    fun `M delegate W requestBodyEnd()`(
        @LongForgery(min = 0) fakeByteCount: Long
    ) {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestBodyEnd(mockCall, fakeByteCount)

        // Then
        verify(mockDelegate).requestBodyEnd(mockCall, fakeByteCount)
    }

    @Test
    fun `M delegate W requestFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.requestFailed(mockCall, ioe)

        // Then
        verify(mockDelegate).requestFailed(mockCall, ioe)
    }

    @Test
    fun `M delegate W responseHeadersStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseHeadersStart(mockCall)

        // Then
        verify(mockDelegate).responseHeadersStart(mockCall)
    }

    @Test
    fun `M delegate W responseHeadersEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseHeadersEnd(mockCall, mockResponse)

        // Then
        verify(mockDelegate).responseHeadersEnd(mockCall, mockResponse)
    }

    @Test
    fun `M delegate W responseBodyStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseBodyStart(mockCall)

        // Then
        verify(mockDelegate).responseBodyStart(mockCall)
    }

    @Test
    fun `M delegate W responseBodyEnd()`(
        @LongForgery(min = 0) fakeByteCount: Long
    ) {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseBodyEnd(mockCall, fakeByteCount)

        // Then
        verify(mockDelegate).responseBodyEnd(mockCall, fakeByteCount)
    }

    @Test
    fun `M delegate W responseFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.responseFailed(mockCall, ioe)

        // Then
        verify(mockDelegate).responseFailed(mockCall, ioe)
    }

    @Test
    fun `M delegate W cacheHit()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.cacheHit(mockCall, mockResponse)

        // Then
        verify(mockDelegate).cacheHit(mockCall, mockResponse)
    }

    @Test
    fun `M delegate W cacheMiss()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.cacheMiss(mockCall)

        // Then
        verify(mockDelegate).cacheMiss(mockCall)
    }

    @Test
    fun `M delegate W cacheConditionalHit()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.cacheConditionalHit(mockCall, mockResponse)

        // Then
        verify(mockDelegate).cacheConditionalHit(mockCall, mockResponse)
    }

    @Test
    fun `M delegate W satisfactionFailure()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.satisfactionFailure(mockCall, mockResponse)

        // Then
        verify(mockDelegate).satisfactionFailure(mockCall, mockResponse)
    }

    @Test
    fun `M delegate W canceled()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.canceled(mockCall)

        // Then
        verify(mockDelegate).canceled(mockCall)
    }

    @Test
    fun `M delegate W callEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.callEnd(mockCall)

        // Then
        verify(mockDelegate).callEnd(mockCall)
    }

    @Test
    fun `M delegate W callFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.callFailed(mockCall, ioe)

        // Then
        verify(mockDelegate).callFailed(mockCall, ioe)
    }
}
