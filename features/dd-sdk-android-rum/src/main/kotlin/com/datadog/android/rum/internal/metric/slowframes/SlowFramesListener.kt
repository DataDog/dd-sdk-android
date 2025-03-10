/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.metrics.performance.FrameData
import com.datadog.android.rum.configuration.SlowFrameListenerConfiguration
import com.datadog.android.rum.internal.domain.FrameMetricsData
import com.datadog.android.rum.internal.domain.state.SlowFrameRecord
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceReport
import com.datadog.android.rum.internal.vitals.FrameStateListener
import com.datadog.tools.annotation.NoOpImplementation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@NoOpImplementation
internal interface SlowFramesListener : FrameStateListener {
    fun onViewCreated(viewId: String, startedTimestampNs: Long)
    fun resolveReport(viewId: String): ViewUIPerformanceReport
    fun onAddLongTask(durationNs: Long)
}

internal class DefaultSlowFramesListener(
    internal val configuration: SlowFrameListenerConfiguration
) : SlowFramesListener {

    @Volatile
    private var currentViewId: String? = null

    @Volatile
    private var currentViewStartedTimeStampNs: Long = System.nanoTime()

    private val slowFramesRecords = ConcurrentHashMap<String, ViewUIPerformanceReport>()

    @MainThread
    override fun onViewCreated(viewId: String, startedTimestampNs: Long) {
        currentViewId = viewId
        currentViewStartedTimeStampNs = startedTimestampNs
    }

    @MainThread
    override fun resolveReport(viewId: String): ViewUIPerformanceReport {
        @Suppress("UnsafeThirdPartyFunctionCall") // can't have NPE here
        val report = slowFramesRecords.remove(viewId) ?: ViewUIPerformanceReport(
            System.nanoTime(),
            configuration.maxSlowFramesAmount,
            configuration.minViewLifetimeThresholdNs
        )

        // making sure that report is not partially updated
        return synchronized(report) { report.copy() }
    }

    @WorkerThread
    override fun onFrame(volatileFrameData: FrameData) {
        val viewId = currentViewId ?: return
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

            if (frameDurationNs > configuration.frozenFrameThresholdNs || !volatileFrameData.isJank) {
                // Frame duration is too big to be considered as a slow frame or not jank
                return
            }

            report.slowFramesDurationNs += frameDurationNs
            val previousSlowFrameRecord = report.lastSlowFrameRecord
            val delaySinceLastUpdate = frameStartedTimestampNs -
                (previousSlowFrameRecord?.startTimestampNs ?: frameStartedTimestampNs)

            if (previousSlowFrameRecord == null || delaySinceLastUpdate > configuration.continuousSlowFrameThresholdNs) {
                // No previous slow frame record or amount of time since the last update
                // is significant enough to consider it idle - adding a new slow frame record.
                if (frameDurationNs > 0) {
                    report.slowFramesRecords += SlowFrameRecord(
                        frameStartedTimestampNs,
                        frameDurationNs
                    )
                }
            } else {
                // It's a continuous slow frame – increasing duration
                previousSlowFrameRecord.durationNs = min(
                    previousSlowFrameRecord.durationNs + frameDurationNs,
                    configuration.frozenFrameThresholdNs - 1
                )
            }
        }
    }

    @WorkerThread
    override fun onAddLongTask(durationNs: Long) {
        val view = currentViewId
        if (durationNs >= configuration.anrDuration && view != null) {
            val report = getViewPerformanceReport(view)
            synchronized(report) { report.anrDurationNs += durationNs }
        }
    }

    @MainThread
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
