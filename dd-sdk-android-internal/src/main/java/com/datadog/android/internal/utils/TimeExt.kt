/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import com.datadog.android.internal.time.TimeProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Formats the given epoch milliseconds to an ISO-8601 formatted string in UTC timezone.
 *
 * Example: 2020-02-14T12:34:56.000Z
 *
 * @param epochMillis The time in milliseconds to format.
 * @return The formatted date string.
 */
@Suppress("UnsafeThirdPartyFunctionCall")
fun formatIsoUtc(epochMillis: Long): String {
    // NPE cannot happen here, ISO_8601 pattern is valid
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochMillis))
}

/**
 * Returns the offset between NTP-corrected (server) time and the boot clock, in nanoseconds.
 *
 * Adding this offset to a [TimeProvider.getDeviceElapsedRealtimeNanos] timestamp yields the
 * NTP-corrected time at which that boot-clock timestamp occurred.
 */
fun TimeProvider.bootNtpOffsetNs(): Long =
    TimeUnit.MILLISECONDS.toNanos(getServerTimestampMillis()) - getDeviceElapsedRealtimeNanos()
