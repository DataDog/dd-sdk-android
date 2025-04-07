/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.instant.insights.collector

import com.datadog.android.internal.collections.EvictingQueue
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import com.datadog.instant.insights.timeline.TimelineEvent

@InternalApi
@ExperimentalRumApi
class DefaultInsightsCollector : InsightsCollector {

    private val events = EvictingQueue<TimelineEvent>(maxSize = 100)
    internal val state: List<TimelineEvent>
        get() = events.toList()
    private val updatesListeners = mutableSetOf<InsightsUpdatesListener>()

    override fun onSlowFrame(startedTimestamp: Long, durationNs: Long) {
        events.add(
            TimelineEvent.SlowFrame(
                startedTimestamp,
                durationNs
            )
        )
    }

    override fun onAction() {
        events.add(
            TimelineEvent.Action
        )
    }

    override fun onLongTask(startedTimestamp: Long, durationNs: Long) {
        events.add(
            TimelineEvent.LongTask(
                startedTimestamp,
                durationNs
            )
        )
    }

    override fun onNetworkRequest(startedTimestamp: Long, durationNs: Long) {
        events.add(
            TimelineEvent.Resource(
                startedTimestamp,
                durationNs
            )
        )
    }

    override fun addUpdateListener(listener: InsightsUpdatesListener) {
        updatesListeners.add(listener)
    }

    override fun removeUpdateListener(listener: InsightsUpdatesListener) {
        updatesListeners.remove(listener)
    }
}
