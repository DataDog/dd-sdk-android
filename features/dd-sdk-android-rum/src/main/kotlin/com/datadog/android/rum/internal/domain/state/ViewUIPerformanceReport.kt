/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.domain.state

import com.datadog.android.internal.collections.EvictingQueue
import java.util.Queue
import kotlin.math.max

internal data class ViewUIPerformanceReport(
    val viewStartedTimeStamp: Long = 0L,
    var slowFramesRecords: Queue<SlowFrameRecord> = EvictingQueue(),
    var totalFramesDurationNs: Long = 0L,
    var slowFramesDurationNs: Long = 0L,
    var anrDurationNs: Long = 0,
    val minViewLifetimeThresholdNs: Long = 0
) {
    constructor(
        viewStartedTimeStamp: Long,
        maxSize: Int,
        minimumViewLifetimeThresholdNs: Long
    ) : this(
        viewStartedTimeStamp = viewStartedTimeStamp,
        slowFramesRecords = EvictingQueue(maxSize),
        minViewLifetimeThresholdNs = minimumViewLifetimeThresholdNs
    )

    val lastSlowFrameRecord: SlowFrameRecord?
        get() = slowFramesRecords.lastOrNull()

    val size: Int
        get() = slowFramesRecords.size

    fun isEmpty() = slowFramesRecords.isEmpty()

    fun slowFramesRate(viewEndedTimeStamp: Long): Double = when {
        viewEndedTimeStamp - viewStartedTimeStamp <= minViewLifetimeThresholdNs -> 0.0
        totalFramesDurationNs > 0.0 -> slowFramesDurationNs.toDouble() / totalFramesDurationNs
        else -> 0.0
    }

    fun anrDurationRatio(viewEndedTimeStamp: Long): Double = when {
        viewEndedTimeStamp - viewStartedTimeStamp <= minViewLifetimeThresholdNs -> 0.0
        else -> max(
            0.0,
            anrDurationNs.toDouble() / (viewEndedTimeStamp - viewStartedTimeStamp)
        )
    }
}
