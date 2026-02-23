/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.domain.state

import com.datadog.android.internal.collections.EvictingQueue
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.Long
import kotlin.math.max

// don't make it data class, because slowFramesRecords will be copied by reference
// see manual copy() below
internal class ViewUIPerformanceReport private constructor(
    val viewStartedTimeStamp: Long = 0L,
    val slowFramesRecords: EvictingQueue<SlowFrameRecord>,
    @Volatile
    var totalFramesDurationNs: Long = 0L,
    @Volatile
    var slowFramesDurationNs: Long = 0L,
    @Volatile
    var freezeFramesDuration: Long = 0,
    val minViewLifetimeThresholdNs: Long = 0
) {
    constructor(
        viewStartedTimeStamp: Long,
        maxSize: Int,
        minimumViewLifetimeThresholdNs: Long
    ) : this(
        viewStartedTimeStamp = viewStartedTimeStamp,
        slowFramesRecords = EvictingQueue(
            maxSize = maxSize,
            delegate = ConcurrentLinkedDeque()
        ),
        minViewLifetimeThresholdNs = minimumViewLifetimeThresholdNs
    )

    val lastSlowFrameRecord: SlowFrameRecord?
        // lastOrNull here is a function of EvictingQueue, because generally for the queue it is O(N)
        get() = slowFramesRecords.lastOrNull()

    fun snapshot(): ViewUIPerformanceReport.Snapshot = ViewUIPerformanceReport.Snapshot(
        viewStartedTimeStamp = viewStartedTimeStamp,
        // iterator over ConcurrentLinkedDeque is weekly consistent, but we are ok with that
        slowFramesRecords = slowFramesRecords.toList(),
        totalFramesDurationNs = totalFramesDurationNs,
        slowFramesDurationNs = slowFramesDurationNs,
        freezeFramesDuration = freezeFramesDuration,
        minViewLifetimeThresholdNs = minViewLifetimeThresholdNs
    )

    data class Snapshot(
        val viewStartedTimeStamp: Long,
        val slowFramesRecords: List<SlowFrameRecord>,
        val totalFramesDurationNs: Long,
        val slowFramesDurationNs: Long,
        val freezeFramesDuration: Long,
        val minViewLifetimeThresholdNs: Long
    ) {
        fun slowFramesRate(viewEndedTimeStamp: Long): Double = when {
            viewEndedTimeStamp - viewStartedTimeStamp <= minViewLifetimeThresholdNs -> 0.0
            totalFramesDurationNs > 0.0 -> slowFramesDurationNs.toDouble() /
                totalFramesDurationNs * MILLISECONDS_IN_SECOND
            else -> 0.0
        }

        fun freezeFramesRate(viewEndedTimeStamp: Long): Double = when {
            viewEndedTimeStamp - viewStartedTimeStamp <= minViewLifetimeThresholdNs -> 0.0
            else -> max(
                0.0,
                freezeFramesDuration.toDouble() / (viewEndedTimeStamp - viewStartedTimeStamp) * SECONDS_IN_HOUR
            )
        }
    }

    companion object {
        private const val MILLISECONDS_IN_SECOND = 1000
        private const val SECONDS_IN_HOUR = 3600
    }
}
