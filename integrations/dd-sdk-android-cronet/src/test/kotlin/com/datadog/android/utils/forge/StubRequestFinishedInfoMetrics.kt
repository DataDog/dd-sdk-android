/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils.forge

import fr.xgouchet.elmyr.Forge
import org.chromium.net.RequestFinishedInfo
import java.util.Date

@Suppress("LongParameterList")
internal class StubRequestFinishedInfoMetrics(
    private val requestStart: Date?,
    private val dnsStart: Date?,
    private val dnsEnd: Date?,
    private val connectStart: Date?,
    private val connectEnd: Date?,
    private val sslStart: Date?,
    private val sslEnd: Date?,
    private val sendingStart: Date?,
    private val sendingEnd: Date?,
    private val pushStart: Date?,
    private val pushEnd: Date?,
    private val responseStart: Date?,
    private val requestEnd: Date?,
    private val socketReused: Boolean,
    private val ttfbMs: Long?,
    private val totalTimeMs: Long?,
    private val sentByteCount: Long?,
    private val receivedByteCount: Long?
) : RequestFinishedInfo.Metrics() {

    override fun getRequestStart() = requestStart
    override fun getDnsStart(): Date? = dnsStart
    override fun getDnsEnd(): Date? = dnsEnd
    override fun getConnectStart(): Date? = connectStart
    override fun getConnectEnd(): Date? = connectEnd
    override fun getSslStart(): Date? = sslStart
    override fun getSslEnd(): Date? = sslEnd
    override fun getSendingStart(): Date? = sendingStart
    override fun getSendingEnd(): Date? = sendingEnd
    override fun getPushStart(): Date? = pushStart
    override fun getPushEnd(): Date? = pushEnd
    override fun getResponseStart(): Date? = responseStart
    override fun getRequestEnd(): Date? = requestEnd
    override fun getSocketReused(): Boolean = socketReused
    override fun getTtfbMs(): Long? = ttfbMs
    override fun getTotalTimeMs(): Long? = totalTimeMs
    override fun getSentByteCount(): Long? = sentByteCount
    override fun getReceivedByteCount(): Long? = receivedByteCount

    companion object {
        fun from(forge: Forge): RequestFinishedInfo.Metrics {
            val requestStartTime = forge.aLong(min = 0)
            val dnsStartTime = forge.aLong(min = requestStartTime)
            val dnsEndTime = forge.aLong(min = dnsStartTime)
            val connectStartTime = forge.aLong(min = dnsEndTime)
            val sslStartTime = forge.aLong(min = connectStartTime)
            val sslEndTime = forge.aLong(min = sslStartTime)
            val connectEndTime = forge.aLong(min = connectStartTime)
            val sendingStartTime = forge.aLong(min = sslEndTime)
            val sendingEndTime = forge.aLong(min = sendingStartTime)
            val pushStart = forge.aLong(min = sendingEndTime)
            val pushEnd = forge.aLong(min = pushStart)
            val responseStart = forge.aLong(min = sslEndTime)
            val requestEnd = forge.aLong(min = responseStart)

            return StubRequestFinishedInfoMetrics(
                requestStart = forge.aNullable { Date(requestStartTime) },
                dnsStart = forge.aNullable { Date(dnsStartTime) },
                dnsEnd = forge.aNullable { Date(dnsEndTime) },
                connectStart = forge.aNullable { Date(connectStartTime) },
                connectEnd = forge.aNullable { Date(connectEndTime) },
                sslStart = forge.aNullable { Date(sslStartTime) },
                sslEnd = forge.aNullable { Date(sslEndTime) },
                sendingStart = forge.aNullable { Date(sendingStartTime) },
                sendingEnd = forge.aNullable { Date(sendingEndTime) },
                pushStart = forge.aNullable { Date(pushStart) },
                pushEnd = forge.aNullable { Date(pushEnd) },
                responseStart = forge.aNullable { Date(responseStart) },
                requestEnd = forge.aNullable { Date(requestEnd) },
                socketReused = forge.aBool(),
                ttfbMs = pushEnd,
                totalTimeMs = forge.aLong(min = requestEnd + 100),
                sentByteCount = forge.aLong(min = 0),
                receivedByteCount = forge.aLong(min = 0)
            )
        }

        val NULL_METRICS = StubRequestFinishedInfoMetrics(
            requestStart = null,
            dnsStart = null,
            dnsEnd = null,
            connectStart = null,
            connectEnd = null,
            sslStart = null,
            sslEnd = null,
            sendingStart = null,
            sendingEnd = null,
            pushStart = null,
            pushEnd = null,
            responseStart = null,
            requestEnd = null,
            socketReused = false,
            ttfbMs = null,
            totalTimeMs = null,
            sentByteCount = null,
            receivedByteCount = null
        )
    }
}
