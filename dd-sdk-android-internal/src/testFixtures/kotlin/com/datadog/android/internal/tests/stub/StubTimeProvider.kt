/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.stub

import com.datadog.android.internal.time.TimeProvider

/**
 * A stub implementation of [TimeProvider] for testing purposes.
 *
 * @property deviceTimestampMs The device timestamp in milliseconds. Defaults to `0`.
 * @property serverTimestampMs The server timestamp in milliseconds. Defaults to `0`.
 * @property elapsedTimeNs The monotonic elapsed time in nanoseconds. Defaults to `0`.
 * @property serverOffsetNs The server time offset in nanoseconds. Defaults to `0`.
 * @property serverOffsetMs The server time offset in milliseconds. Defaults to `0`.
 * @property elapsedRealtimeMs The elapsed realtime in milliseconds (includes time in sleep). Defaults to `0`.
 */
class StubTimeProvider(
    var deviceTimestampMs: Long = 0L,
    var serverTimestampMs: Long = 0L,
    var elapsedTimeNs: Long = 0L,
    var serverOffsetNs: Long = 0L,
    var serverOffsetMs: Long = 0L,
    var elapsedRealtimeMs: Long = 0L
) : TimeProvider {

    override fun getDeviceTimestampMillis(): Long = deviceTimestampMs

    override fun getServerTimestampMillis(): Long = serverTimestampMs

    override fun getDeviceElapsedTimeNanos(): Long = elapsedTimeNs

    override fun getServerOffsetNanos(): Long = serverOffsetNs

    override fun getServerOffsetMillis(): Long = serverOffsetMs

    override fun getDeviceElapsedRealtimeMillis(): Long = elapsedRealtimeMs
}
