/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.InternalSdkCore
import java.util.concurrent.TimeUnit

internal data class Time(
    val timestamp: Long = System.currentTimeMillis(),
    val nanoTime: Long = System.nanoTime()
)

internal fun InternalSdkCore.currentTime() =
    Time(timeProvider.getDeviceTimestampMillis(), timeProvider.getDeviceElapsedTimeNanos())

/**
 * Convert a value obtained from [System.currentTimeMillis] to [Time].
 */
internal fun Long.asTime(): Time {
    // Because nanoTime only measures the nanoseconds since the beginning
    // of the current JVM lifetime, we need to approximate the nanotime we want.
    // We simply convert the delay between the desired and real timestamp and
    // apply it to the measured nanotime
    val now = Time()
    val offset = this - now.timestamp
    return Time(this, TimeUnit.MILLISECONDS.toNanos(offset) + now.nanoTime)
}

/**
 * Convert a value obtained from [System.nanoTime] to [Time].
 */
internal fun Long.asTimeNs(): Time {
    val now = Time()
    val offset = this - now.nanoTime
    return Time(TimeUnit.NANOSECONDS.toMillis(offset) + now.timestamp, this)
}
