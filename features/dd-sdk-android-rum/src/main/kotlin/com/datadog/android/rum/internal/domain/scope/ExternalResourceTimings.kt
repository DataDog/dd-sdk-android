/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.internal.domain.event.ResourceTiming

private const val FIRST_BYTE_TIMING = "firstByte"
private const val DOWNLOAD_TIMING = "download"
private const val SSL_TIMING = "ssl"
private const val CONNECT_TIMING = "connect"
private const val DNS_TIMING = "dns"

private val ALL_TIMINGS = listOf(
    FIRST_BYTE_TIMING,
    DOWNLOAD_TIMING,
    SSL_TIMING,
    CONNECT_TIMING,
    DNS_TIMING
)

private const val START_TIME_KEY = "startTime"
private const val DURATION_KEY = "duration"

internal fun extractResourceTiming(timingsPayload: Map<String, Any?>?): ResourceTiming? {
    if (timingsPayload == null) {
        return null
    }

    val timings = ALL_TIMINGS.associateWith { value ->
        extractTiming(value, timingsPayload)
    }.filterValues { it != null }

    return if (timings.isNotEmpty()) {
        createResourceTiming(timings)
    } else {
        null
    }
}

@Suppress("ComplexMethod")
private fun createResourceTiming(timings: Map<String, Timing?>): ResourceTiming {
    return ResourceTiming(
        firstByteStart = timings[FIRST_BYTE_TIMING]?.startTime ?: 0L,
        firstByteDuration = timings[FIRST_BYTE_TIMING]?.duration ?: 0L,
        downloadStart = timings[DOWNLOAD_TIMING]?.startTime ?: 0L,
        downloadDuration = timings[DOWNLOAD_TIMING]?.duration ?: 0L,
        dnsStart = timings[DNS_TIMING]?.startTime ?: 0L,
        dnsDuration = timings[DNS_TIMING]?.duration ?: 0L,
        connectStart = timings[CONNECT_TIMING]?.startTime ?: 0L,
        connectDuration = timings[CONNECT_TIMING]?.duration ?: 0L,
        sslStart = timings[SSL_TIMING]?.startTime ?: 0L,
        sslDuration = timings[SSL_TIMING]?.duration ?: 0L
    )
}

private fun extractTiming(name: String, source: Map<String, Any?>): Timing? {
    val timing = source[name]

    return if (timing != null && timing is Map<*, *>) {
        // number values coming from JavaScript will always be Double, for example
        val startTime = (timing[START_TIME_KEY] as? Number)?.toLong()
        val duration = (timing[DURATION_KEY] as? Number)?.toLong()
        if (startTime != null && duration != null) {
            Timing(startTime, duration)
        } else {
            null
        }
    } else {
        null
    }
}

private data class Timing(val startTime: Long, val duration: Long)
