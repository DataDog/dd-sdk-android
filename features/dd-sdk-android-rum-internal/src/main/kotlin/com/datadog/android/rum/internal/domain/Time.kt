/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// These types are public only so that :features:dd-sdk-android-rum and
// :features:dd-sdk-android-rum-prelaunch can share them across module boundaries. They are not
// part of the SDK's public API and carry no KDoc for that reason.
@file:Suppress(
    "PackageNameVisibility",
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty"
)

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
            val now = now(timeProvider)
            val offset = timestamp - now.timestamp
            return Time(timestamp, now.nanoTime + TimeUnit.MILLISECONDS.toNanos(offset))
        }

        fun fromNanoTime(nanoTime: Long, timeProvider: TimeProvider): Time {
            val now = now(timeProvider)
            val offset = nanoTime - now.nanoTime
            return Time(now.timestamp + TimeUnit.NANOSECONDS.toMillis(offset), nanoTime)
        }
    }
}
