/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights.timeline

internal sealed class TimelineEvent(
    val startedTimestamp: Long,
    val durationNs: Long
) {
    object Action : TimelineEvent(System.nanoTime(), 0L)
    object Tick : TimelineEvent(System.nanoTime(), 0L)
    class SlowFrame(startedTimestamp: Long, durationNs: Long) : TimelineEvent(startedTimestamp, durationNs)
    class Resource(startedTimestamp: Long, durationNs: Long) : TimelineEvent(startedTimestamp, durationNs)
    class LongTask(startedTimestamp: Long, durationNs: Long) : TimelineEvent(startedTimestamp, durationNs)
}
