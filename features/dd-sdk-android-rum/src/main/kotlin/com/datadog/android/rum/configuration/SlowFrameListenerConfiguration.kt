/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.configuration

/**
 * Configuration class for the slow frames listener, used to define thresholds for detecting
 * slow frames, frozen frames, and ANRs (Application Not Responding) in the context of performance monitoring.
 *
 * @param maxSlowFramesAmount The maximum number of slow frame records to track. Default is 1000.
 * @param frozenFrameThresholdNs The threshold (in nanoseconds) used to determine whether a frame is considered frozen.
 *                               Default is 700 000 000 ns (700ms).
 * @param continuousSlowFrameThresholdNs The threshold (in nanoseconds) for considering a frame as continuously slow.
 *                                       Default is 16 666 666 ns (approximately 1/60 fps).
 * @param anrDuration The duration (in nanoseconds) to consider as an ANR (Application Not Responding).
 *                    Default is 5 000 000 000 ns (5 seconds).
 * @param minViewLifetimeThresholdNs The minimum view lifetime threshold (in nanoseconds) before it is considered for monitoring.
 *                                   Default is 100 000 000 ns (100ms).
 */
data class SlowFrameListenerConfiguration(
    internal val maxSlowFramesAmount: Int = DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT,
    internal val frozenFrameThresholdNs: Long = DEFAULT_FROZEN_FRAME_THRESHOLD_NS,
    internal val continuousSlowFrameThresholdNs: Long = DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD_NS,
    internal val anrDuration: Long = DEFAULT_ANR_DURATION_NS,
    internal val minViewLifetimeThresholdNs: Long = DEFAULT_VIEW_LIFETIME_THRESHOLD_NS
) {

    companion object {
        // Taking into account each Hitch takes 64B in the payload, we can have 64KB max per view event
        private const val DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT: Int = 1000
        private const val DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD_NS: Long = 16_666_666L // 1/60 fps in nanoseconds
        private const val DEFAULT_FROZEN_FRAME_THRESHOLD_NS: Long = 700_000_000 // 700ms
        private const val DEFAULT_ANR_DURATION_NS: Long = 5_000_000_000L // 5s
        private const val DEFAULT_VIEW_LIFETIME_THRESHOLD_NS: Long = 100_000_000L // 100ms
    }
}
