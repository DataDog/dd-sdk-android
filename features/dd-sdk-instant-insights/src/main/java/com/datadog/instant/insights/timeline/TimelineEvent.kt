/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights.timeline

internal sealed class TimelineEvent(
    val durationNs: Long
) {
    object Action : TimelineEvent(0L)
    object Tick : TimelineEvent(0L)
    class SlowFrame(durationNs: Long) : TimelineEvent(durationNs)
    class Resource(durationNs: Long) : TimelineEvent(durationNs)
    class LongTask(durationNs: Long) : TimelineEvent(durationNs)
}
