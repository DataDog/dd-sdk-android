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

// Most of the methods are just delegation
@Suppress("UnsafeThirdPartyFunctionCall")
internal class CompositeEventListener(
    private val registry: RequestTracingStateRegistry,
    private val delegates: List<EventListener>
) : EventListener() {

    internal class Factory(
        private val registry: RequestTracingStateRegistry,
        private val delegates: List<EventListener.Factory>
    ) : EventListener.Factory {

        override fun create(call: Call): EventListener {
            registry.register(call)
            val listeners = delegates.map { it.create(call) }
            return CompositeEventListener(registry, listeners)
        }
    }

    override fun callStart(call: Call) {
        delegates.forEach { it.callStart(call) }
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        delegates.forEach { it.proxySelectStart(call, url) }
    }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
        delegates.forEach { it.proxySelectEnd(call, url, proxies) }
    }

    override fun dnsStart(call: Call, domainName: String) {
        delegates.forEach { it.dnsStart(call, domainName) }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        delegates.forEach { it.dnsEnd(call, domainName, inetAddressList) }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        delegates.forEach { it.connectStart(call, inetSocketAddress, proxy) }
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        delegates.forEach { it.connectEnd(call, inetSocketAddress, proxy, protocol) }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        delegates.forEach { it.connectFailed(call, inetSocketAddress, proxy, protocol, ioe) }
    }

    override fun secureConnectStart(call: Call) {
        delegates.forEach { it.secureConnectStart(call) }
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        delegates.forEach { it.secureConnectEnd(call, handshake) }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        delegates.forEach { it.connectionAcquired(call, connection) }
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        delegates.forEach { it.connectionReleased(call, connection) }
    }

    override fun requestHeadersStart(call: Call) {
        delegates.forEach { it.requestHeadersStart(call) }
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        delegates.forEach { it.requestHeadersEnd(call, request) }
    }

    override fun requestBodyStart(call: Call) {
        delegates.forEach { it.requestBodyStart(call) }
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        delegates.forEach { it.requestBodyEnd(call, byteCount) }
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        delegates.forEach { it.requestFailed(call, ioe) }
    }

    override fun responseHeadersStart(call: Call) {
        delegates.forEach { it.responseHeadersStart(call) }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        delegates.forEach { it.responseHeadersEnd(call, response) }
    }

    override fun responseBodyStart(call: Call) {
        delegates.forEach { it.responseBodyStart(call) }
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        delegates.forEach { it.responseBodyEnd(call, byteCount) }
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        delegates.forEach { it.responseFailed(call, ioe) }
    }

    override fun cacheHit(call: Call, response: Response) {
        delegates.forEach { it.cacheHit(call, response) }
    }

    override fun cacheMiss(call: Call) {
        delegates.forEach { it.cacheMiss(call) }
    }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
        delegates.forEach { it.cacheConditionalHit(call, cachedResponse) }
    }

    override fun satisfactionFailure(call: Call, response: Response) {
        delegates.forEach { it.satisfactionFailure(call, response) }
    }

    override fun canceled(call: Call) {
        delegates.forEach { it.canceled(call) }
    }

    override fun callEnd(call: Call) {
        delegates.forEach { it.callEnd(call) }
        cleanUp(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        delegates.forEach { it.callFailed(call, ioe) }
        cleanUp(call)
    }

    private fun cleanUp(call: Call) = registry.remove(call)
}
