/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.DatadogEventListener.Factory
import com.datadog.android.core.internal.net.identifyRequest
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.event.RumEventData
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response

/**
 * Datadog's RUM implementation of OkHttp [EventListener].
 *
 * This will track requests timing information (TTFB, DNS resolution, …) and append it
 * to RUM Resource events.
 *
 * To use:
 * ```
 *   OkHttpClient client = new OkHttpClient.Builder()
 *       .addInterceptor(new DatadogInterceptor())
 *       .eventListenerFactory(new DatadogEventListener.Factory())
 *       .build();
 * ```
 *
 * @see [Factory]
 */
class DatadogEventListener
internal constructor(val key: String) : EventListener() {

    private var callStart = 0L

    private var dnsStart = 0L
    private var dnsEnd = 0L

    private var connStart = 0L
    private var connEnd = 0L

    private var sslStart = 0L
    private var sslEnd = 0L

    private var headersStart = 0L
    private var headersEnd = 0L

    private var bodyStart = 0L
    private var bodyEnd = 0L

    // region EventListener

    override fun callStart(call: Call) {
        super.callStart(call)
        callStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun dnsStart(call: Call, domainName: String) {
        super.dnsStart(call, domainName)
        dnsStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: MutableList<InetAddress>) {
        super.dnsEnd(call, domainName, inetAddressList)
        dnsEnd = System.nanoTime()
    }

    /** @inheritdoc */
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        super.connectStart(call, inetSocketAddress, proxy)
        connStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        super.connectEnd(call, inetSocketAddress, proxy, protocol)
        connEnd = System.nanoTime()
    }

    /** @inheritdoc */
    override fun secureConnectStart(call: Call) {
        super.secureConnectStart(call)
        sslStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        super.secureConnectEnd(call, handshake)
        sslEnd = System.nanoTime()
    }

    /** @inheritdoc */
    override fun responseHeadersStart(call: Call) {
        super.responseHeadersStart(call)
        (GlobalRum.get() as? AdvancedRumMonitor)?.waitForResourceTiming(key)
        headersStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun responseHeadersEnd(call: Call, response: Response) {
        super.responseHeadersEnd(call, response)
        headersEnd = System.nanoTime()
        if (response.code() >= 400) {
            sendTiming()
        }
    }

    /** @inheritdoc */
    override fun responseBodyStart(call: Call) {
        super.responseBodyStart(call)
        bodyStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun responseBodyEnd(call: Call, byteCount: Long) {
        super.responseBodyEnd(call, byteCount)
        bodyEnd = System.nanoTime()
    }

    /** @inheritdoc */
    override fun callEnd(call: Call) {
        super.callEnd(call)
        sendTiming()
    }

    /** @inheritdoc */
    override fun callFailed(call: Call, ioe: IOException) {
        super.callFailed(call, ioe)
        sendTiming()
    }

    // endregion

    // region Internal

    private fun sendTiming() {

        val timing = buildTiming()
        (GlobalRum.get() as? AdvancedRumMonitor)?.addResourceTiming(key, timing)
    }

    private fun buildTiming(): RumEventData.Resource.Timing {
        val (dnsS, dnsD) = if (dnsStart == 0L) {
            0L to 0L
        } else {
            (dnsStart - callStart) to (dnsEnd - dnsStart)
        }
        val (conS, conD) = if (connStart == 0L) {
            0L to 0L
        } else {
            (connStart - callStart) to (connEnd - connStart)
        }
        val (sslS, sslD) = if (sslStart == 0L) {
            0L to 0L
        } else (sslStart - callStart) to (sslEnd - sslStart)
        val (fbS, fbD) = if (headersStart == 0L) {
            0L to 0L
        } else {
            (headersStart - callStart) to (headersEnd - headersStart)
        }
        val (dlS, dlD) = if (bodyStart == 0L) {
            0L to 0L
        } else {
            (bodyStart - callStart) to (bodyEnd - bodyStart)
        }

        return RumEventData.Resource.Timing(
            dnsStart = dnsS,
            dnsDuration = dnsD,
            connectStart = conS,
            connectDuration = conD,
            sslStart = sslS,
            sslDuration = sslD,
            firstByteStart = fbS,
            firstByteDuration = fbD,
            downloadStart = dlS,
            downloadDuration = dlD
        )
    }

    // endregion

    /**
     * Datadog's RUM implementation of OkHttp [EventListener.Factory].
     * Adding this Factory to your [OkHttpClient] will allow Datadog to monitor
     * timing information for your requests (DNS resolution, TTFB, …).
     *
     * The timing information will be appended to the relevant RUM Resource events.
     *
     * To use:
     * ```
     *   OkHttpClient client = new OkHttpClient.Builder()
     *       .addInterceptor(new DatadogInterceptor())
     *       .eventListenerFactory(new DatadogEventListener.Factory())
     *       .build();
     * ```
     */
    class Factory : EventListener.Factory {
        /** @inheritdoc */
        override fun create(call: Call): EventListener {
            val key = identifyRequest(call.request())
            return DatadogEventListener(key)
        }
    }
}
