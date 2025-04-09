/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights

import android.os.Handler
import android.os.Looper
import com.datadog.android.insights.domain.TimelineEvent
import com.datadog.android.internal.collections.EvictingQueue
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener

@InternalApi
@ExperimentalRumApi
class DefaultInsightsCollector(
    maxSize: Int = 50,
    updateIntervalMs: Long = 100L,
) : InsightsCollector {

    private var events = EvictingQueue<TimelineEvent>(maxSize)
    private val updatesListeners = mutableSetOf<InsightsUpdatesListener>()
    private val handler = Handler(Looper.getMainLooper())
    private val ticksProducer = Runnable {
        registerEvent(TimelineEvent.Tick)
        postTickProducer()
    }

    internal val state: List<TimelineEvent> get() = events.toList()

    override var maxSize = maxSize
        set(value) {
            field = value
            events = EvictingQueue(value)
        }

    override var updateIntervalMs = updateIntervalMs
        set(value) {
            field = value
            handler.removeCallbacksAndMessages(null)
            postTickProducer()
        }

    init {
        postTickProducer()
    }

    override fun onAction() = registerEvent(
        TimelineEvent.Action
    )

    override fun onNewView(viewId: String) {
        clear()
    }

    override fun onSlowFrame(startedTimestamp: Long, durationNs: Long) = registerEvent(
        TimelineEvent.SlowFrame(startedTimestamp, durationNs)
    )

    override fun onLongTask(startedTimestamp: Long, durationNs: Long) = registerEvent(
        TimelineEvent.LongTask(startedTimestamp, durationNs)
    )

    override fun onNetworkRequest(startedTimestamp: Long, durationNs: Long) = registerEvent(
        TimelineEvent.Resource(startedTimestamp, durationNs)
    )

    override fun addUpdateListener(listener: InsightsUpdatesListener) {
        updatesListeners += listener
    }

    override fun removeUpdateListener(listener: InsightsUpdatesListener) {
        updatesListeners -= listener
    }

    private fun registerEvent(event: TimelineEvent) {
        events += event
        updatesListeners.forEach(InsightsUpdatesListener::onDataUpdated)
    }

    private fun clear() {
        events.clear()
        updatesListeners.forEach(InsightsUpdatesListener::onDataUpdated)
    }

    private fun postTickProducer() {
        if (updateIntervalMs > 0) {
            handler.postDelayed(ticksProducer, updateIntervalMs)
        }
    }
}
