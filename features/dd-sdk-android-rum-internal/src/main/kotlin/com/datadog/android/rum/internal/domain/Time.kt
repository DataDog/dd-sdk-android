/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.internal.time.TimeProvider
import java.util.concurrent.TimeUnit

data class Time(
    val timestamp: Long,
    val nanoTime: Long
) {
    companion object {
        fun now(timeProvider: TimeProvider): Time {
            return Time(
                timeProvider.getDeviceTimestampMillis(),
                timeProvider.getDeviceElapsedTimeNanos()
            )
        }

        fun fromTimestampMillis(timestamp: Long, timeProvider: TimeProvider): Time {
            // Because nanoTime only measures the nanoseconds since the beginning
            // of the current JVM lifetime, we need to approximate the nanoTime we want.
            // We convert the delay between the desired and current timestamp and
            // apply it to the currently measured nanoTime.
            val now = now(timeProvider)
            val offset = timestamp - now.timestamp
            return Time(timestamp, now.nanoTime + TimeUnit.MILLISECONDS.toNanos(offset))
        }

        fun fromNanoTime(nanoTime: Long, timeProvider: TimeProvider): Time {
            // Symmetric to [fromTimestampMillis]: we have a nanoTime reading and
            // approximate the matching wall-clock timestamp by applying the delay
            // between the desired and current nanoTime to the current timestamp.
            val now = now(timeProvider)
            val offset = nanoTime - now.nanoTime
            return Time(now.timestamp + TimeUnit.NANOSECONDS.toMillis(offset), nanoTime)
        }
    }
}
