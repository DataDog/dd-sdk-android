/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogEventListenerFactoryTest {
    @Mock(answer = Answers.RETURNS_MOCKS)
    lateinit var mockCall: Call

    @RegexForgery("[a-z]+\\.[a-z]{3}")
    lateinit var fakeDomain: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    val fakeIOException: IOException = IOException("Fake error")

    @Mock
    lateinit var mockConnecion: Connection

    @Mock
    lateinit var mockDelegatedListener: EventListener

    lateinit var testedFactory: DatadogEventListener.Factory

    @LongForgery(min = 1L)
    var fakeByteCount: Long = 1L

    @BeforeEach
    fun `set up`() {
        fakeRequest = Request.Builder().get().url("https://$fakeDomain/").build()
        fakeResponse = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("lorem ipsum dolor sit amet…")
            .build()

        val delegatedFactory = EventListener.Factory { mockDelegatedListener }
        testedFactory = DatadogEventListener.Factory(delegatedFactory)
    }

    @Test
    fun `𝕄 delegate are called 𝕎 all methods`() {
        // Given
        val testedListener = DatadogEventListener.Factory(testedFactory).create(mockCall)
        val address = InetSocketAddress(0)

        // When
        testedListener.callStart(mockCall)
        testedListener.dnsStart(mockCall, fakeDomain)
        testedListener.dnsEnd(mockCall, fakeDomain, emptyList())
        testedListener.connectStart(mockCall, address, Proxy.NO_PROXY)
        testedListener.secureConnectStart(mockCall)
        testedListener.secureConnectEnd(mockCall, null)
        testedListener
            .connectEnd(mockCall, address, Proxy.NO_PROXY, Protocol.HTTP_2)
        testedListener.responseHeadersStart(mockCall)
        testedListener.responseHeadersEnd(mockCall, fakeResponse)
        testedListener.responseBodyStart(mockCall)
        testedListener.responseBodyEnd(mockCall, fakeByteCount)
        testedListener.callEnd(mockCall)
        testedListener
            .connectFailed(mockCall, address, Proxy.NO_PROXY, Protocol.HTTP_2, fakeIOException)
        testedListener.connectionAcquired(mockCall, mockConnecion)
        testedListener.connectionReleased(mockCall, mockConnecion)
        testedListener.requestHeadersStart(mockCall)
        testedListener.requestHeadersEnd(mockCall, fakeRequest)
        testedListener.requestBodyStart(mockCall)
        testedListener.requestBodyEnd(mockCall, fakeByteCount)

        // Then

        verify(mockDelegatedListener).callStart(mockCall)
        verify(mockDelegatedListener).dnsStart(mockCall, fakeDomain)
        verify(mockDelegatedListener).dnsEnd(mockCall, fakeDomain, emptyList())
        verify(mockDelegatedListener)
            .connectStart(mockCall, address, Proxy.NO_PROXY)
        verify(mockDelegatedListener).secureConnectStart(mockCall)
        verify(mockDelegatedListener).secureConnectEnd(mockCall, null)
        verify(mockDelegatedListener)
            .connectEnd(mockCall, address, Proxy.NO_PROXY, Protocol.HTTP_2)
        verify(mockDelegatedListener).responseHeadersStart(mockCall)
        verify(mockDelegatedListener).responseHeadersEnd(mockCall, fakeResponse)
        verify(mockDelegatedListener).responseBodyStart(mockCall)
        verify(mockDelegatedListener).responseBodyEnd(mockCall, fakeByteCount)
        verify(mockDelegatedListener).callEnd(mockCall)
        verify(mockDelegatedListener)
            .connectFailed(mockCall, address, Proxy.NO_PROXY, Protocol.HTTP_2, fakeIOException)
        verify(mockDelegatedListener).connectionAcquired(mockCall, mockConnecion)
        verify(mockDelegatedListener).connectionReleased(mockCall, mockConnecion)
        verify(mockDelegatedListener).requestHeadersStart(mockCall)
        verify(mockDelegatedListener).requestHeadersEnd(mockCall, fakeRequest)
        verify(mockDelegatedListener).requestBodyStart(mockCall)
        verify(mockDelegatedListener).requestBodyEnd(mockCall, fakeByteCount)
    }
}
