/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.timeseries

import androidx.annotation.IntRange
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.timeseries.TimeseriesConfiguration.Companion.DEFAULT_BUFFER_SIZE
import com.datadog.android.rum.timeseries.TimeseriesConfiguration.Companion.DEFAULT_INTERVAL_MS
import com.datadog.android.rum.timeseries.TimeseriesConfiguration.Companion.MIN_INTERVAL_MS

/**
 * Configuration for memory and CPU timeseries collection.
 *
 * Use [Builder] to create an instance.
 */
class TimeseriesConfiguration internal constructor(
    internal val bufferSize: Int,
    internal val intervalMs: Long
) {

    /**
     * A Builder for [TimeseriesConfiguration].
     */
    @ExperimentalRumApi
    class Builder {

        private var bufferSize: Int = DEFAULT_BUFFER_SIZE
        private var intervalMs: Long = DEFAULT_INTERVAL_MS

        /**
         * Sets the number of samples accumulated per pipeline before sending a batch event.
         * Must be > 0; out-of-range values fall back to [DEFAULT_BUFFER_SIZE].
         * Defaults to [DEFAULT_BUFFER_SIZE] (≈120 seconds at the default 1 s interval).
         */
        fun setBufferSize(
            @IntRange(from = 1, to = Int.MAX_VALUE.toLong()) bufferSize: Int
        ): Builder = apply {
            this.bufferSize = if (bufferSize > 0) bufferSize else DEFAULT_BUFFER_SIZE
        }

        /**
         * Sets the sampling interval in milliseconds.
         * Must be ≥ [MIN_INTERVAL_MS]; out-of-range values fall back to [DEFAULT_INTERVAL_MS].
         * Defaults to [DEFAULT_INTERVAL_MS].
         */
        fun setIntervalMs(
            @IntRange(from = MIN_INTERVAL_MS, to = Long.MAX_VALUE) intervalMs: Long
        ): Builder = apply {
            this.intervalMs = if (intervalMs >= MIN_INTERVAL_MS) intervalMs else DEFAULT_INTERVAL_MS
        }

        /** Builds a [TimeseriesConfiguration] from the current builder state. */
        fun build(): TimeseriesConfiguration = TimeseriesConfiguration(
            bufferSize = bufferSize,
            intervalMs = intervalMs
        )
    }

    companion object {

        /** Default [TimeseriesConfiguration] built with all default settings. */
        @ExperimentalRumApi
        val DEFAULT: TimeseriesConfiguration = Builder().build()

        /** Default number of samples to accumulate before emitting a timeseries event. */
        internal const val DEFAULT_BUFFER_SIZE: Int = 120

        /** Default sampling interval in milliseconds. */
        internal const val DEFAULT_INTERVAL_MS: Long = 1000L

        /** Minimum allowed sampling interval in milliseconds. */
        internal const val MIN_INTERVAL_MS: Long = 100L
    }
}
