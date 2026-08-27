/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal.domain

internal sealed class TimelineEvent(
    val text: String
) {
    object Action : TimelineEvent("")
    object Tick : TimelineEvent("")
    data class Timeseries(val name: String) : TimelineEvent(name)
    class SlowFrame(durationNs: Long) : TimelineEvent(durationNs.toMsText())
    class Resource(durationNs: Long) : TimelineEvent(durationNs.toMsText())
    class LongTask(durationNs: Long) : TimelineEvent(durationNs.toMsText())

    private companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        private fun Long.toMsText(): String = (this / NANOS_PER_MILLI).toString()
    }
}
