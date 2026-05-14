/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

@Suppress("UnsafeThirdPartyFunctionCall") // Most of the methods are just delegation
internal class RegistryTrackingEventListener(
    private val registry: RequestTracingStateRegistry,
    private val delegate: EventListener
) : EventListener() {

    internal class Factory(
        private val registry: RequestTracingStateRegistry,
        private val delegateFactory: EventListener.Factory
    ) : EventListener.Factory {

        override fun create(call: Call): EventListener {
            registry.register(call)
            return try {
                val delegate = delegateFactory.create(call)
                RegistryTrackingEventListener(registry, delegate)
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                registry.remove(call)
                @Suppress("ThrowingInternalException")
                throw e
            }
        }
    }

    override fun callStart(call: Call) {
        delegate.callStart(call)
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        delegate.proxySelectStart(call, url)
    }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
        delegate.proxySelectEnd(call, url, proxies)
    }

    override fun dnsStart(call: Call, domainName: String) {
        delegate.dnsStart(call, domainName)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        delegate.dnsEnd(call, domainName, inetAddressList)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        delegate.connectStart(call, inetSocketAddress, proxy)
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        delegate.connectEnd(call, inetSocketAddress, proxy, protocol)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        delegate.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    }

    override fun secureConnectStart(call: Call) {
        delegate.secureConnectStart(call)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        delegate.secureConnectEnd(call, handshake)
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        delegate.connectionAcquired(call, connection)
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        delegate.connectionReleased(call, connection)
    }

    override fun requestHeadersStart(call: Call) {
        delegate.requestHeadersStart(call)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        delegate.requestHeadersEnd(call, request)
    }

    override fun requestBodyStart(call: Call) {
        delegate.requestBodyStart(call)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        delegate.requestBodyEnd(call, byteCount)
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        delegate.requestFailed(call, ioe)
    }

    override fun responseHeadersStart(call: Call) {
        delegate.responseHeadersStart(call)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        delegate.responseHeadersEnd(call, response)
    }

    override fun responseBodyStart(call: Call) {
        delegate.responseBodyStart(call)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        delegate.responseBodyEnd(call, byteCount)
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        delegate.responseFailed(call, ioe)
    }

    override fun cacheHit(call: Call, response: Response) {
        delegate.cacheHit(call, response)
    }

    override fun cacheMiss(call: Call) {
        delegate.cacheMiss(call)
    }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
        delegate.cacheConditionalHit(call, cachedResponse)
    }

    override fun satisfactionFailure(call: Call, response: Response) {
        delegate.satisfactionFailure(call, response)
    }

    override fun canceled(call: Call) {
        delegate.canceled(call)
    }

    override fun callEnd(call: Call) {
        try {
            delegate.callEnd(call)
        } finally {
            registry.remove(call)
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        try {
            delegate.callFailed(call, ioe)
        } finally {
            registry.remove(call)
        }
    }
}
