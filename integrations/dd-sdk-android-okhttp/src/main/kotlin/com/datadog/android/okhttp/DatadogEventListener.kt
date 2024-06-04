/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.core.SdkReference
import com.datadog.android.okhttp.DatadogEventListener.Factory
import com.datadog.android.okhttp.internal.utils.identifyRequest
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

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
 * @param sdkCore the SDK instance to use.
 * @param key Call identity.
 * @see [Factory]
 */
class DatadogEventListener
internal constructor(
    internal val sdkCore: SdkCore,
    internal val key: String
) : EventListener() {

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

    /** @inheritdoc */
    override fun callStart(call: Call) {
        super.callStart(call)
        sendWaitForResourceTimingEvent()
        callStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun dnsStart(call: Call, domainName: String) {
        super.dnsStart(call, domainName)
        sendWaitForResourceTimingEvent()
        dnsStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        super.dnsEnd(call, domainName, inetAddressList)
        dnsEnd = System.nanoTime()
    }

    /** @inheritdoc */
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        super.connectStart(call, inetSocketAddress, proxy)
        sendWaitForResourceTimingEvent()
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
        sendWaitForResourceTimingEvent()
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
        sendWaitForResourceTimingEvent()
        headersStart = System.nanoTime()
    }

    /** @inheritdoc */
    override fun responseHeadersEnd(call: Call, response: Response) {
        super.responseHeadersEnd(call, response)
        headersEnd = System.nanoTime()
        if (response.code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            sendTiming()
        }
    }

    /** @inheritdoc */
    override fun responseBodyStart(call: Call) {
        super.responseBodyStart(call)
        sendWaitForResourceTimingEvent()
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

    private fun sendWaitForResourceTimingEvent() {
        (GlobalRumMonitor.get(sdkCore) as? AdvancedNetworkRumMonitor)?.waitForResourceTiming(key)
    }

    private fun sendTiming() {
        val timing = buildTiming()
        (GlobalRumMonitor.get(sdkCore) as? AdvancedNetworkRumMonitor)?.addResourceTiming(key, timing)
    }

    private fun buildTiming(): ResourceTiming {
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
        } else {
            (sslStart - callStart) to (sslEnd - sslStart)
        }
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

        return ResourceTiming(
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
     *
     * @param sdkInstanceName SDK instance name to bind to, or null to check the default instance.
     * Instrumentation won't be working until SDK instance is ready.
     */
    class Factory(
        sdkInstanceName: String? = null
    ) : EventListener.Factory {

        private val sdkCoreReference = SdkReference(sdkInstanceName)

        /** @inheritdoc */
        override fun create(call: Call): EventListener {
            val key = identifyRequest(call.request())
            val sdkCore = sdkCoreReference.get()
            return if (sdkCore != null) {
                DatadogEventListener(sdkCore, key)
            } else {
                InternalLogger.UNBOUND.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    {
                        "No SDK instance is available, skipping tracking" +
                            " timing information of request with url ${call.request().url}."
                    }
                )
                NO_OP_EVENT_LISTENER
            }
        }

        internal companion object {
            val NO_OP_EVENT_LISTENER: EventListener = object : EventListener() {}
        }
    }
}
