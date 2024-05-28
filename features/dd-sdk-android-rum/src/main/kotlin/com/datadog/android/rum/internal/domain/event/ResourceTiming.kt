/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.lint.InternalApi

/**
 * Timings for the resource connection.
 *
 * FOR INTERNAL USAGE ONLY.
 */
@InternalApi
data class ResourceTiming(
    /**
     * Timestamp (in nanoseconds) of DNS lookup start.
     */
    val dnsStart: Long = 0L,
    /**
     * Duration (in nanoseconds) of DNS lookup.
     */
    val dnsDuration: Long = 0L,
    /**
     * Timestamp (in nanoseconds) of the connection start.
     */
    val connectStart: Long = 0L,
    /**
     * Duration (in nanoseconds) of the connection.
     */
    val connectDuration: Long = 0L,
    /**
     * Timestamp (in nanoseconds) of SSL handshake start.
     */
    val sslStart: Long = 0L,
    /**
     * Duration (in nanoseconds) of SSL handshake.
     */
    val sslDuration: Long = 0L,
    /**
     * Timestamp (in nanoseconds) of headers fetch start.
     */
    val firstByteStart: Long = 0L,
    /**
     * Duration (in nanoseconds) of headers fetch.
     */
    val firstByteDuration: Long = 0L,
    /**
     * Timestamp (in nanoseconds) of body download start.
     */
    val downloadStart: Long = 0L,
    /**
     * Duration (in nanoseconds) of body download.
     */
    val downloadDuration: Long = 0L
)
