/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.time

import com.datadog.android.internal.time.TimeProvider

/**
 * This is a terrible hack just to handle the fact that [com.datadog.android.profiling.internal.perfetto
 * .PerfettoProfiler] can be created when Datadog SDK is not yet initialized.
 */
internal interface MutableTimeProvider : TimeProvider {
    var delegate: TimeProvider

    private class MutableTimeProviderImpl(@Volatile override var delegate: TimeProvider) : MutableTimeProvider {
        override fun getDeviceTimestampMillis(): Long = delegate.getDeviceTimestampMillis()

        override fun getServerTimestampMillis(): Long = delegate.getServerTimestampMillis()

        override fun getDeviceElapsedTimeNanos(): Long = delegate.getDeviceElapsedTimeNanos()

        override fun getServerOffsetNanos(): Long = delegate.getServerOffsetNanos()

        override fun getServerOffsetMillis(): Long = delegate.getServerOffsetMillis()

        override fun getDeviceElapsedRealtimeMillis(): Long = delegate.getDeviceElapsedRealtimeMillis()

        override fun getDeviceUptimeMillis(): Long = delegate.getDeviceUptimeMillis()
    }

    companion object {
        fun create(delegate: TimeProvider): MutableTimeProvider = MutableTimeProviderImpl(delegate)
    }
}
