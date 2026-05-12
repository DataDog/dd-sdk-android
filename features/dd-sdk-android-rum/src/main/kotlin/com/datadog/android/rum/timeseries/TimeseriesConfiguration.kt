/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.timeseries

import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.timeseries.TimeseriesConfiguration.Companion.DEFAULT_BUFFER_SIZE
import com.datadog.android.rum.timeseries.TimeseriesConfiguration.Companion.DEFAULT_INTERVAL_MS
import com.datadog.android.rum.timeseries.TimeseriesConfiguration.Companion.MIN_INTERVAL_MS
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * Configuration for memory and CPU timeseries collection.
 *
 * @param bufferSize Number of samples accumulated per pipeline before sending a batch event.
 *                   Must be > 0; out-of-range values fall back to [DEFAULT_BUFFER_SIZE].
 *                   Defaults to [DEFAULT_BUFFER_SIZE] (≈30 seconds at the default 1 s interval).
 * @param intervalMs Sampling interval in milliseconds. Must be ≥ [MIN_INTERVAL_MS];
 *                   out-of-range values fall back to [DEFAULT_INTERVAL_MS].
 *                   Defaults to [DEFAULT_INTERVAL_MS].
 * @param collectInBackground Whether to keep sampling timeseries when the app is in background.
 *                            Defaults to `false`.
 */

class TimeseriesConfiguration
@JvmOverloads @ExperimentalRumApi
constructor(
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    intervalMs: Long = DEFAULT_INTERVAL_MS,
    val collectInBackground: Boolean = false
) {
    val bufferSize: Int = if (bufferSize > 0) bufferSize else DEFAULT_BUFFER_SIZE
    val intervalMs: Long = if (intervalMs >= MIN_INTERVAL_MS) intervalMs else DEFAULT_INTERVAL_MS

    internal val executorFactory: () -> ScheduledExecutorService = {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            Thread(runnable, "datadog-timeseries").apply { isDaemon = true }
        }
    }

    companion object {
        /** Default number of samples to accumulate before emitting a timeseries event. */
        const val DEFAULT_BUFFER_SIZE: Int = 30

        /** Default sampling interval in milliseconds. */
        const val DEFAULT_INTERVAL_MS: Long = 1000L

        /** Minimum allowed sampling interval in milliseconds. */
        const val MIN_INTERVAL_MS: Long = 100L
    }
}
