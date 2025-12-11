/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.metrics.performance.FrameData
import com.datadog.android.rum.configuration.SlowFramesConfiguration
import com.datadog.android.rum.internal.domain.FrameMetricsData
import com.datadog.android.rum.internal.domain.state.SlowFrameRecord
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.vitals.FrameStateListener
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

internal interface SlowFramesListener : FrameStateListener {
    fun onViewCreated(viewId: String, startedTimestampNs: Long)
    fun resolveReport(viewId: String, isViewCompleted: Boolean, viewDurationNs: Long): ViewUIPerformanceReport?
    fun onAddLongTask(durationNs: Long)
}

internal class DefaultSlowFramesListener(
    internal val configuration: SlowFramesConfiguration,
    internal val metricDispatcher: UISlownessMetricDispatcher,
    internal val insightsCollector: InsightsCollector
) : SlowFramesListener {

    @Volatile
    private var currentViewId: String? = null

    @Volatile
    private var currentViewStartedTimeStampNs: Long = System.nanoTime()

    private val slowFramesRecords = ConcurrentHashMap<String, ViewUIPerformanceReport>()

    // Called from the main thread
    override fun onViewCreated(viewId: String, startedTimestampNs: Long) {
        currentViewId = viewId
        currentViewStartedTimeStampNs = startedTimestampNs
        metricDispatcher.onViewCreated(viewId)
    }

    // Called from the main thread
    override fun resolveReport(
        viewId: String,
        isViewCompleted: Boolean,
        viewDurationNs: Long
    ): ViewUIPerformanceReport? {
        @Suppress("UnsafeThirdPartyFunctionCall") // can't have NPE here
        val report = if (isViewCompleted) slowFramesRecords.remove(viewId) else slowFramesRecords[viewId]

        if (report == null) return null

        // making sure that report is not partially updated
        return synchronized(report) {
            if (isViewCompleted) metricDispatcher.sendMetric(viewId, viewDurationNs)
            report.copy()
        }
    }

    // Called from the background thread
    override fun onFrame(volatileFrameData: FrameData) {
        val viewId = currentViewId
        if (viewId == null || volatileFrameData.frameStartNanos < currentViewStartedTimeStampNs) {
            if (viewId != null) {
                metricDispatcher.incrementMissedFrameCount(viewId)
            }
            // Due to the asynchronous nature of collecting jank frames data, there is a possible situation where
            // androidx.performance.metrics started detecting jank frames on a previous view, but reported them after the new view
            // was started. In this case, we don't save this data, because the previous view has already
            // sent its report, so there is no way to add more data to it, adding a slow frame to the
            // current view, would be also wrong, so we just drop such frame data.
            return
        }
        val frameDurationNs = volatileFrameData.frameDurationUiNanos
        val frameStartedTimestampNs = volatileFrameData.frameStartNanos
        val report = getViewPerformanceReport(viewId)

        // We have to synchronize here because it's the only way to update
        // all fields of ViewUIPerformanceReport atomically. onFrame is a "hot" method
        // so we can't make ViewUIPerformanceReport immutable because that will force us to
        // create tons of copies on each call which will lead to a lot of gc calls
        synchronized(report) {
            // Updating frames statistics
            report.totalFramesDurationNs += frameDurationNs

            if (frameDurationNs > configuration.maxSlowFrameThresholdNs || !volatileFrameData.isJank) {
                // Frame duration is too big to be considered as a slow frame or not jank
                metricDispatcher.incrementIgnoredFrameCount(viewId)
                return
            }

            report.slowFramesDurationNs += frameDurationNs
            metricDispatcher.incrementSlowFrameCount(viewId)
            val previousSlowFrameRecord = report.lastSlowFrameRecord
            val delaySinceLastUpdate = frameStartedTimestampNs -
                (previousSlowFrameRecord?.startTimestampNs ?: frameStartedTimestampNs)

            if (previousSlowFrameRecord == null ||
                delaySinceLastUpdate > configuration.continuousSlowFrameThresholdNs
            ) {
                // No previous slow frame record or amount of time since the last update
                // is significant enough to consider it idle - adding a new slow frame record.
                if (frameDurationNs > 0) {
                    report.slowFramesRecords += SlowFrameRecord(
                        frameStartedTimestampNs,
                        frameDurationNs
                    )
                    insightsCollector.onSlowFrame(frameStartedTimestampNs, frameDurationNs)
                }
            } else {
                // It's a continuous slow frame – increasing duration
                previousSlowFrameRecord.durationNs = min(
                    previousSlowFrameRecord.durationNs + frameDurationNs,
                    configuration.maxSlowFrameThresholdNs - 1
                )
                insightsCollector.onSlowFrame(frameStartedTimestampNs, frameDurationNs)
            }
        }
    }

    // Called from the background thread
    override fun onAddLongTask(durationNs: Long) {
        val viewId = currentViewId
        if (durationNs >= configuration.freezeDurationThresholdNs && viewId != null) {
            val report = getViewPerformanceReport(viewId)
            synchronized(report) {
                report.freezeFramesDuration += durationNs
            }
        }
    }

    // Called from the main thread
    override fun onFrameMetricsData(data: FrameMetricsData) {
        // do nothing
    }

    private fun getViewPerformanceReport(viewId: String) = slowFramesRecords.getOrPut(viewId) {
        ViewUIPerformanceReport(
            currentViewStartedTimeStampNs,
            configuration.maxSlowFramesAmount,
            minimumViewLifetimeThresholdNs = configuration.minViewLifetimeThresholdNs
        )
    }
}
