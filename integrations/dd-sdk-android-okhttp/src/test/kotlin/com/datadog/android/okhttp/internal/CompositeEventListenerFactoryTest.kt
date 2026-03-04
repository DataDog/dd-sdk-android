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
internal class CompositeEventListenerFactoryTest {

    @Mock
    lateinit var mockFactory1: EventListener.Factory

    @Mock
    lateinit var mockFactory2: EventListener.Factory

    @Mock
    lateinit var mockListener1: EventListener

    @Mock
    lateinit var mockListener2: EventListener

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

    private lateinit var testedFactory: CompositeEventListener.Factory

    @BeforeEach
    fun `set up`() {
        whenever(mockFactory1.create(mockCall)) doReturn mockListener1
        whenever(mockFactory2.create(mockCall)) doReturn mockListener2
        testedFactory = CompositeEventListener.Factory(mockRegistry, listOf(mockFactory1, mockFactory2))
    }

    @Test
    fun `M delegate to all listeners W callStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.callStart(mockCall)

        // Then
        verify(mockListener1).callStart(mockCall)
        verify(mockListener2).callStart(mockCall)
    }

    @Test
    fun `M delegate to all listeners W proxySelectStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.proxySelectStart(mockCall, mockHttpUrl)

        // Then
        verify(mockListener1).proxySelectStart(mockCall, mockHttpUrl)
        verify(mockListener2).proxySelectStart(mockCall, mockHttpUrl)
    }

    @Test
    fun `M delegate to all listeners W proxySelectEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val proxies = listOf(Proxy.NO_PROXY)

        // When
        listener.proxySelectEnd(mockCall, mockHttpUrl, proxies)

        // Then
        verify(mockListener1).proxySelectEnd(mockCall, mockHttpUrl, proxies)
        verify(mockListener2).proxySelectEnd(mockCall, mockHttpUrl, proxies)
    }

    @Test
    fun `M delegate to all listeners W dnsStart()`(
        @StringForgery fakeDomainName: String
    ) {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.dnsStart(mockCall, fakeDomainName)

        // Then
        verify(mockListener1).dnsStart(mockCall, fakeDomainName)
        verify(mockListener2).dnsStart(mockCall, fakeDomainName)
    }

    @Test
    fun `M delegate to all listeners W dnsEnd()`(
        @StringForgery fakeDomainName: String
    ) {
        // Given
        val listener = testedFactory.create(mockCall)
        val inetAddressList = emptyList<InetAddress>()

        // When
        listener.dnsEnd(mockCall, fakeDomainName, inetAddressList)

        // Then
        verify(mockListener1).dnsEnd(mockCall, fakeDomainName, inetAddressList)
        verify(mockListener2).dnsEnd(mockCall, fakeDomainName, inetAddressList)
    }

    @Test
    fun `M delegate to all listeners W connectStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val socketAddress = InetSocketAddress(0)
        val proxy = Proxy.NO_PROXY

        // When
        listener.connectStart(mockCall, socketAddress, proxy)

        // Then
        verify(mockListener1).connectStart(mockCall, socketAddress, proxy)
        verify(mockListener2).connectStart(mockCall, socketAddress, proxy)
    }

    @Test
    fun `M delegate to all listeners W connectEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val socketAddress = InetSocketAddress(0)
        val proxy = Proxy.NO_PROXY
        val protocol = Protocol.HTTP_2

        // When
        listener.connectEnd(mockCall, socketAddress, proxy, protocol)

        // Then
        verify(mockListener1).connectEnd(mockCall, socketAddress, proxy, protocol)
        verify(mockListener2).connectEnd(mockCall, socketAddress, proxy, protocol)
    }

    @Test
    fun `M delegate to all listeners W connectFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val socketAddress = InetSocketAddress(0)
        val proxy = Proxy.NO_PROXY
        val protocol = Protocol.HTTP_2
        val ioe = IOException("test")

        // When
        listener.connectFailed(mockCall, socketAddress, proxy, protocol, ioe)

        // Then
        verify(mockListener1).connectFailed(mockCall, socketAddress, proxy, protocol, ioe)
        verify(mockListener2).connectFailed(mockCall, socketAddress, proxy, protocol, ioe)
    }

    @Test
    fun `M delegate to all listeners W secureConnectStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.secureConnectStart(mockCall)

        // Then
        verify(mockListener1).secureConnectStart(mockCall)
        verify(mockListener2).secureConnectStart(mockCall)
    }

    @Test
    fun `M delegate to all listeners W secureConnectEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.secureConnectEnd(mockCall, mockHandshake)

        // Then
        verify(mockListener1).secureConnectEnd(mockCall, mockHandshake)
        verify(mockListener2).secureConnectEnd(mockCall, mockHandshake)
    }

    @Test
    fun `M delegate to all listeners W connectionAcquired()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.connectionAcquired(mockCall, mockConnection)

        // Then
        verify(mockListener1).connectionAcquired(mockCall, mockConnection)
        verify(mockListener2).connectionAcquired(mockCall, mockConnection)
    }

    @Test
    fun `M delegate to all listeners W connectionReleased()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.connectionReleased(mockCall, mockConnection)

        // Then
        verify(mockListener1).connectionReleased(mockCall, mockConnection)
        verify(mockListener2).connectionReleased(mockCall, mockConnection)
    }

    @Test
    fun `M delegate to all listeners W requestHeadersStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestHeadersStart(mockCall)

        // Then
        verify(mockListener1).requestHeadersStart(mockCall)
        verify(mockListener2).requestHeadersStart(mockCall)
    }

    @Test
    fun `M delegate to all listeners W requestHeadersEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestHeadersEnd(mockCall, mockRequest)

        // Then
        verify(mockListener1).requestHeadersEnd(mockCall, mockRequest)
        verify(mockListener2).requestHeadersEnd(mockCall, mockRequest)
    }

    @Test
    fun `M delegate to all listeners W requestBodyStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestBodyStart(mockCall)

        // Then
        verify(mockListener1).requestBodyStart(mockCall)
        verify(mockListener2).requestBodyStart(mockCall)
    }

    @Test
    fun `M delegate to all listeners W requestBodyEnd()`(
        @LongForgery(min = 0) fakeByteCount: Long
    ) {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.requestBodyEnd(mockCall, fakeByteCount)

        // Then
        verify(mockListener1).requestBodyEnd(mockCall, fakeByteCount)
        verify(mockListener2).requestBodyEnd(mockCall, fakeByteCount)
    }

    @Test
    fun `M delegate to all listeners W requestFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.requestFailed(mockCall, ioe)

        // Then
        verify(mockListener1).requestFailed(mockCall, ioe)
        verify(mockListener2).requestFailed(mockCall, ioe)
    }

    @Test
    fun `M delegate to all listeners W responseHeadersStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseHeadersStart(mockCall)

        // Then
        verify(mockListener1).responseHeadersStart(mockCall)
        verify(mockListener2).responseHeadersStart(mockCall)
    }

    @Test
    fun `M delegate to all listeners W responseHeadersEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseHeadersEnd(mockCall, mockResponse)

        // Then
        verify(mockListener1).responseHeadersEnd(mockCall, mockResponse)
        verify(mockListener2).responseHeadersEnd(mockCall, mockResponse)
    }

    @Test
    fun `M delegate to all listeners W responseBodyStart()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseBodyStart(mockCall)

        // Then
        verify(mockListener1).responseBodyStart(mockCall)
        verify(mockListener2).responseBodyStart(mockCall)
    }

    @Test
    fun `M delegate to all listeners W responseBodyEnd()`(
        @LongForgery(min = 0) fakeByteCount: Long
    ) {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.responseBodyEnd(mockCall, fakeByteCount)

        // Then
        verify(mockListener1).responseBodyEnd(mockCall, fakeByteCount)
        verify(mockListener2).responseBodyEnd(mockCall, fakeByteCount)
    }

    @Test
    fun `M delegate to all listeners W responseFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.responseFailed(mockCall, ioe)

        // Then
        verify(mockListener1).responseFailed(mockCall, ioe)
        verify(mockListener2).responseFailed(mockCall, ioe)
    }

    @Test
    fun `M delegate to all listeners W cacheHit()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.cacheHit(mockCall, mockResponse)

        // Then
        verify(mockListener1).cacheHit(mockCall, mockResponse)
        verify(mockListener2).cacheHit(mockCall, mockResponse)
    }

    @Test
    fun `M delegate to all listeners W cacheMiss()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.cacheMiss(mockCall)

        // Then
        verify(mockListener1).cacheMiss(mockCall)
        verify(mockListener2).cacheMiss(mockCall)
    }

    @Test
    fun `M delegate to all listeners W cacheConditionalHit()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.cacheConditionalHit(mockCall, mockResponse)

        // Then
        verify(mockListener1).cacheConditionalHit(mockCall, mockResponse)
        verify(mockListener2).cacheConditionalHit(mockCall, mockResponse)
    }

    @Test
    fun `M delegate to all listeners W satisfactionFailure()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.satisfactionFailure(mockCall, mockResponse)

        // Then
        verify(mockListener1).satisfactionFailure(mockCall, mockResponse)
        verify(mockListener2).satisfactionFailure(mockCall, mockResponse)
    }

    @Test
    fun `M delegate to all listeners W canceled()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.canceled(mockCall)

        // Then
        verify(mockListener1).canceled(mockCall)
        verify(mockListener2).canceled(mockCall)
    }

    @Test
    fun `M delegate to all listeners W callEnd()`() {
        // Given
        val listener = testedFactory.create(mockCall)

        // When
        listener.callEnd(mockCall)

        // Then
        verify(mockListener1).callEnd(mockCall)
        verify(mockListener2).callEnd(mockCall)
    }

    @Test
    fun `M delegate to all listeners W callFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.callFailed(mockCall, ioe)

        // Then
        verify(mockListener1).callFailed(mockCall, ioe)
        verify(mockListener2).callFailed(mockCall, ioe)
    }

    @Test
    fun `M create composite listener from factories W create()`() {
        // When
        val listener = testedFactory.create(mockCall)

        // Then
        verify(mockFactory1).create(mockCall)
        verify(mockFactory2).create(mockCall)
        listener.callStart(mockCall)
        verify(mockListener1).callStart(mockCall)
        verify(mockListener2).callStart(mockCall)
    }

    @Test
    fun `M register call in registry W create()`() {
        // When
        testedFactory.create(mockCall)

        // Then
        verify(mockRegistry).register(mockCall)
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
    fun `M clean up registry W callFailed()`() {
        // Given
        val listener = testedFactory.create(mockCall)
        val ioe = IOException("test")

        // When
        listener.callFailed(mockCall, ioe)

        // Then
        verify(mockRegistry).remove(mockCall)
    }
}
