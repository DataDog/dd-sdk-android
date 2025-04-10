/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.insights

import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import com.datadog.android.insights.domain.TimelineEvent
import com.datadog.android.insights.extensions.Mb
import com.datadog.android.insights.extensions.round
import com.datadog.android.internal.collections.EvictingQueue
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import java.util.concurrent.TimeUnit

@InternalApi
@ExperimentalRumApi
class DefaultInsightsCollector(
    maxSize: Int = 50,
    updateIntervalMs: Long = 100L,
) : InsightsCollector {

    private val handler = Handler(Looper.getMainLooper())
    private var events = EvictingQueue<TimelineEvent>(maxSize)
    private val updatesListeners = mutableSetOf<InsightsUpdatesListener>()
    private val ticksProducer = Runnable {
        registerEvent(TimelineEvent.Tick)
        postTickProducer()
    }

    private var viewStartedNs = 0L
    private val viewDurationNs: Long get() = System.nanoTime() - viewStartedNs
    internal var viewName: String = ""
        private set

    internal var gcCallsPerSecond: Double = 0.0
    internal var threadsCount: Int = 0
    internal var cpuTicksPerSecond: Double = Double.NaN
    internal var vmRssMb: Double = Double.NaN
    internal var nativeHeapMb: Double = Double.NaN
    internal val eventsState: List<TimelineEvent> get() = events.toList()

    override var maxSize: Int = maxSize
        set(value) {
            field = value
            events = EvictingQueue(value)
        }

    override var updateIntervalMs: Long = updateIntervalMs
        set(value) {
            field = value
            handler.removeCallbacks(ticksProducer)
            postTickProducer()
        }

    init {
        postTickProducer()
    }

    override fun onAction() = registerEvent(
        TimelineEvent.Action
    )

    override fun onNewView(viewId: String, name: String) {
        viewName = name
        viewStartedNs = System.nanoTime()

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

    override fun onMemoryVitalResolved(memoryValue: Double?) = withListenersUpdate {
        vmRssMb = memoryValue?.Mb.round(2)
    }

    override fun onCpuVitalResolved(cpuTicks: Double?) = withListenersUpdate {
        cpuTicksPerSecond = cpuTicks?.perSecond().round(2)
    }

    private fun clear() = withListenersUpdate {
        events.clear()
    }

    private fun registerEvent(event: TimelineEvent) = withListenersUpdate {
        events += event
    }

    private fun withListenersUpdate(block: () -> Unit) {
        updateCommonInfo()
        handler.post {
            block()
            updatesListeners.forEach(InsightsUpdatesListener::onDataUpdated)
        }
    }

    private fun updateCommonInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gcCallsPerSecond = Debug.getRuntimeStat(GC_COUNT)
                .toDoubleOrNull()
                .perSecond()
                .round(2)
        }
        nativeHeapMb = Debug.getNativeHeapAllocatedSize().toDouble().Mb.round(2)
        threadsCount = Thread.activeCount()
    }

    private fun postTickProducer() {
        if (updateIntervalMs > 0) {
            handler.postDelayed(ticksProducer, updateIntervalMs)
        }
    }

    private fun Double?.perSecond(): Double {
        if (this == null || viewDurationNs < ONE_SECOND_NS) {
            return Double.NaN
        }

        return this * ONE_SECOND_NS / viewDurationNs
    }

    companion object {
        private const val GC_COUNT = "art.gc.gc-count"
        private val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1)
    }
}
