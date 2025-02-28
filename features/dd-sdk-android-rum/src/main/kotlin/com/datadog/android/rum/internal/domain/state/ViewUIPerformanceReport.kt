/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.domain.state

import com.datadog.android.core.collections.EvictingQueue
import java.util.Queue

internal data class ViewUIPerformanceReport(
    val viewStartedTimeStamp: Long = 0L,
    var slowFramesRecords: Queue<SlowFrameRecord> = EvictingQueue(),
    var totalFramesDurationNs: Long = 0L,
    var slowFramesDurationNs: Long = 0L
) {
    constructor(viewStartedTimeStamp: Long, maxSize: Int) : this(
        viewStartedTimeStamp = viewStartedTimeStamp,
        slowFramesRecords = EvictingQueue(maxSize)
    )

    internal val lastSlowFrameRecord: SlowFrameRecord?
        get() = slowFramesRecords.lastOrNull()

    val slowFramesRate: Double
        get() = slowFramesDurationNs.toDouble() /
            (totalFramesDurationNs + 1) // avoiding division by zero

    fun isEmpty() = slowFramesRecords.isEmpty()
    val size: Int
        get() = slowFramesRecords.size
}
