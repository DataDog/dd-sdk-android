/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

internal class RumInstrumentationTimingsCounter(
    internal val sdkCore: SdkCore,
    private val rumNetworkInstrumentation: RumNetworkInstrumentation,
    private val requestTracingStateRegistry: RequestTracingStateRegistry
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
        requestTracingStateRegistry.get(call)?.let(::sendWaitForResourceTimingEvent)
        callStart = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun dnsStart(call: Call, domainName: String) {
        super.dnsStart(call, domainName)
        requestTracingStateRegistry.get(call)?.let(::sendWaitForResourceTimingEvent)
        dnsStart = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        super.dnsEnd(call, domainName, inetAddressList)
        dnsEnd = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        super.connectStart(call, inetSocketAddress, proxy)
        requestTracingStateRegistry.get(call)?.let(::sendWaitForResourceTimingEvent)
        connStart = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        super.connectEnd(call, inetSocketAddress, proxy, protocol)
        connEnd = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun secureConnectStart(call: Call) {
        super.secureConnectStart(call)
        requestTracingStateRegistry.get(call)?.let(::sendWaitForResourceTimingEvent)
        sslStart = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        super.secureConnectEnd(call, handshake)
        sslEnd = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun responseHeadersStart(call: Call) {
        super.responseHeadersStart(call)
        requestTracingStateRegistry.get(call)?.let(::sendWaitForResourceTimingEvent)
        headersStart = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun responseHeadersEnd(call: Call, response: Response) {
        super.responseHeadersEnd(call, response)
        headersEnd = sdkCore.time.deviceTimeNs
        if (response.code >= HttpURLConnection.HTTP_BAD_REQUEST) {
            requestTracingStateRegistry.get(call)?.let(::sendTiming)
        }
    }

    /** @inheritdoc */
    override fun responseBodyStart(call: Call) {
        super.responseBodyStart(call)
        requestTracingStateRegistry.get(call)?.let(::sendWaitForResourceTimingEvent)
        bodyStart = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun responseBodyEnd(call: Call, byteCount: Long) {
        super.responseBodyEnd(call, byteCount)
        bodyEnd = sdkCore.time.deviceTimeNs
    }

    /** @inheritdoc */
    override fun callEnd(call: Call) {
        super.callEnd(call)
        requestTracingStateRegistry.get(call)?.let(::sendTiming)
    }

    /** @inheritdoc */
    override fun callFailed(call: Call, ioe: IOException) {
        super.callFailed(call, ioe)
        requestTracingStateRegistry.get(call)?.let(::sendTiming)
    }

    // endregion

    // region Internal

    private fun sendWaitForResourceTimingEvent(requestTracingState: RequestTracingState) {
        rumNetworkInstrumentation.sendWaitForResourceTimingEvent(requestTracingState.createRequestInfo())
    }

    private fun sendTiming(requestTracingState: RequestTracingState) {
        rumNetworkInstrumentation.sendTiming(requestTracingState.createRequestInfo(), buildTiming())
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

    class Factory(
        private val rumNetworkInstrumentation: RumNetworkInstrumentation,
        private val requestTracingStateRegistry: RequestTracingStateRegistry
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener {
            val sdkCore = rumNetworkInstrumentation.sdkCore
            return if (sdkCore != null) {
                RumInstrumentationTimingsCounter(sdkCore, rumNetworkInstrumentation, requestTracingStateRegistry)
            } else {
                InternalLogger.UNBOUND.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "No SDK instance is available, skipping tracking timing information." }
                )
                NO_OP_EVENT_LISTENER
            }
        }

        internal companion object {
            val NO_OP_EVENT_LISTENER: EventListener = object : EventListener() {}
        }
    }
}
