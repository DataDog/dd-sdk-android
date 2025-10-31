/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal

import android.os.Handler
import android.os.Looper
import com.datadog.android.insights.internal.domain.TimelineEvent
import com.datadog.android.insights.internal.extensions.Mb
import com.datadog.android.insights.internal.extensions.round
import com.datadog.android.insights.internal.platform.Platform
import com.datadog.android.internal.collections.EvictingQueue
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.InsightsUpdatesListener
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Default implementation of [com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector] that collects performance related events and notifies
 * its listeners at a regular interval.
 *
 * @param maxSize Maximum number of events stored in memory.
 * @param updateIntervalMs Time interval in milliseconds at which the listeners will be notified of data updates.
 * @param handler Handler to post updates on. Injected for test purposes.
 * @param platform Platform abstraction to access system information. Injected for test purposes.
 */
@ExperimentalRumApi
internal class DefaultInsightsCollector internal constructor(
    maxSize: Int,
    updateIntervalMs: Long,
    private val handler: Handler,
    private val platform: Platform
) : InsightsCollector {

    constructor(
        maxSize: Int = 50,
        updateIntervalMs: Long = 100L
    ) : this(
        maxSize = maxSize,
        updateIntervalMs = updateIntervalMs,
        handler = Handler(Looper.getMainLooper()),
        platform = Platform()
    )

    private var events = EvictingQueue<TimelineEvent>(maxSize)
    internal val eventsState: List<TimelineEvent> get() = events.toList()
    private val updatesListeners = CopyOnWriteArraySet<InsightsUpdatesListener>()
    private val ticksProducer = Runnable {
        registerEvent(TimelineEvent.Tick)
        postTickProducer()
    }

    private var viewStartedNs = 0L
    private val viewDurationNs: Long get() = platform.nanoTime() - viewStartedNs
    internal var viewName: String = ""
        private set
    internal var threadsCount: Int = 0
        private set
    internal var gcCallsPerSecond: Double = 0.0
        private set
    internal var vmRssMb: Double = Double.NaN
        private set
    internal var nativeHeapMb: Double = Double.NaN
        private set
    internal var slowFramesRate: Double = Double.NaN
        private set
    internal var cpuTicksPerSecond: Double = Double.NaN
        private set

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

    override fun onNewView(name: String) {
        viewName = name
        viewStartedNs = platform.nanoTime()

        clear()
    }

    override fun onSlowFrame(startedTimestamp: Long, durationNs: Long) = registerEvent(
        TimelineEvent.SlowFrame(durationNs)
    )

    override fun onLongTask(startedTimestamp: Long, durationNs: Long) = registerEvent(
        TimelineEvent.LongTask(durationNs)
    )

    override fun onNetworkRequest(startedTimestamp: Long, durationNs: Long) = registerEvent(
        TimelineEvent.Resource(durationNs)
    )

    override fun addUpdateListener(listener: InsightsUpdatesListener) {
        updatesListeners += listener
    }

    override fun removeUpdateListener(listener: InsightsUpdatesListener) {
        updatesListeners -= listener
    }

    override fun onMemoryVital(memoryValue: Double?) = withListenersUpdate {
        vmRssMb = memoryValue?.Mb.round(PRECISION)
    }

    override fun onCpuVital(cpuTicks: Double?) = withListenersUpdate {
        cpuTicksPerSecond = cpuTicks?.perSecond().round(PRECISION)
    }

    override fun onSlowFrameRate(rate: Double?) = withListenersUpdate {
        slowFramesRate = rate.round(PRECISION)
    }

    private fun clear() = withListenersUpdate {
        events.clear()
    }

    private fun registerEvent(event: TimelineEvent) = withListenersUpdate {
        events += event
    }

    private fun withListenersUpdate(block: () -> Unit) {
        handler.post {
            updateCommonInfo()
            block()
            updatesListeners.forEach(InsightsUpdatesListener::onDataUpdated)
        }
    }

    private fun updateCommonInfo() {
        gcCallsPerSecond = platform.getRuntimeStat(GC_COUNT)
            ?.toDoubleOrNull()
            .perSecond()
            .round(PRECISION)
        nativeHeapMb = platform.getNativeHeapAllocatedSize().toDouble().Mb.round(0)
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

        val seconds = viewDurationNs / ONE_SECOND_NS.toDouble()

        return this / seconds
    }

    companion object {
        internal const val PRECISION = 2
        internal const val GC_COUNT = "art.gc.gc-count"
        internal const val ONE_SECOND_NS = 1_000_000_000L
    }
}
