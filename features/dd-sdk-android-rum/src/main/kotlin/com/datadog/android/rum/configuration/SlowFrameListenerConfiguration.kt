/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.configuration

/**
 * The [SlowFramesListener] provides various statistics to assist in identifying UI performance issues:
 *
 * - slowFrames: A list of records containing the timestamp and duration of frames where users experience
 *   jank frames within the given view.
 *
 * - slowFrameRate: The rate of slow frames encountered during the view's lifetime.
 *
 * - freezeRate: The rate of freeze occurrences during the view's lifetime.
 *
 * This class defines thresholds for frame duration to classify frames based on their duration type.
 *
 * @param maxSlowFramesAmount The maximum number of slow frame records to track for each view.
 * The default value is 1000.
 *
 * @param maxSlowFrameThresholdNs The threshold (in nanoseconds) used to classify a frame as slow.
 * Frames with durations exceeding this threshold will not be considered slow and will not affect the
 * [com.datadog.android.rum.model.ViewEvent.ViewEventView.slowFramesRate].
 * The default value is 700,000,000 ns (700 ms).
 *
 * @param continuousSlowFrameThresholdNs The threshold (in nanoseconds) used to classify a frame as continuously slow.
 * If two consecutive slow frames are recorded and the delay between them is less than this threshold, the previous frame
 * record will be updated rather than adding a new one.
 * The default value is 16,666,666 ns (approximately 1/60 fps).
 *
 * @param freezeDurationThreshold The duration (in nanoseconds) used to classify a frame as a freeze frame.
 * The cumulative duration of such freezes contributes to the calculation of the
 * [com.datadog.android.rum.model.ViewEvent.ViewEventView.freezeRate].
 * The default value is 5,000,000,000 ns (5 seconds).
 *
 * @param minViewLifetimeThresholdNs The minimum lifetime (in nanoseconds) a view must have before it is considered
 * for monitoring. The default value is 100,000,000 ns (100 ms).
 */

data class SlowFrameListenerConfiguration(
    internal val maxSlowFramesAmount: Int = DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT,
    internal val maxSlowFrameThresholdNs: Long = DEFAULT_FROZEN_FRAME_THRESHOLD_NS,
    internal val continuousSlowFrameThresholdNs: Long = DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD_NS,
    internal val freezeDurationThreshold: Long = DEFAULT_FREEZE_DURATION_NS,
    internal val minViewLifetimeThresholdNs: Long = DEFAULT_VIEW_LIFETIME_THRESHOLD_NS
) {

    companion object {
        /**
         * A default configuration of the [SlowFrameListenerConfiguration] class with all parameters set to their default values.
         */
        val DEFAULT: SlowFrameListenerConfiguration = SlowFrameListenerConfiguration()

        // Taking into account each Hitch takes 64B in the payload, we can have 64KB max per view event
        private const val DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT: Int = 1000
        private const val DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD_NS: Long = 16_666_666L // 1/60 fps in nanoseconds
        private const val DEFAULT_FROZEN_FRAME_THRESHOLD_NS: Long = 700_000_000 // 700ms
        private const val DEFAULT_FREEZE_DURATION_NS: Long = 5_000_000_000L // 5s
        private const val DEFAULT_VIEW_LIFETIME_THRESHOLD_NS: Long = 100_000_000L // 100ms
    }
}
