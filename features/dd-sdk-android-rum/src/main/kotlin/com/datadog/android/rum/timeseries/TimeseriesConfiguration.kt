/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.timeseries

import androidx.annotation.IntRange
import com.datadog.android.rum.ExperimentalRumApi

/**
 * Configuration for memory and CPU timeseries collection.
 *
 * Use [Builder] to create an instance.
 */
class TimeseriesConfiguration internal constructor(
    internal val bufferSize: Int,
    internal val intervalMs: Long,
    internal val collectInBackground: Boolean
) {

    /**
     * A Builder for [TimeseriesConfiguration].
     */
    @ExperimentalRumApi
    class Builder {

        private var bufferSize: Int = DEFAULT_BUFFER_SIZE
        private var intervalMs: Long = DEFAULT_INTERVAL_MS
        private var collectInBackground: Boolean = false

        /**
         * Sets the number of samples accumulated per pipeline before sending a batch event.
         * Must be > 0; out-of-range values fall back to [DEFAULT_BUFFER_SIZE].
         * Defaults to [DEFAULT_BUFFER_SIZE] (≈30 seconds at the default 1 s interval).
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

        /**
         * Sets whether to keep sampling timeseries when the app is in background.
         * Defaults to `false`.
         */
        fun setCollectInBackground(collectInBackground: Boolean): Builder = apply {
            this.collectInBackground = collectInBackground
        }

        /** Builds a [TimeseriesConfiguration] from the current builder state. */
        fun build(): TimeseriesConfiguration = TimeseriesConfiguration(
            bufferSize = bufferSize,
            intervalMs = intervalMs,
            collectInBackground = collectInBackground
        )
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
