/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.metrics.performance.FrameData
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
    internal val maxSlowFramesAmount: Int = DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT,
    internal val frozenFrameThresholdNs: Long = DEFAULT_FROZEN_FRAME_THRESHOLD_NS,
    internal val continuousSlowFrameThresholdNs: Long = DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD_NS,
    internal val anrDuration: Long = DEFAULT_ANR_DURATION_NS,
    internal val minViewLifetimeThresholdNs: Long = DEFAULT_VIEW_LIFETIME_THRESHOLD_NS
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
            maxSlowFramesAmount,
            minViewLifetimeThresholdNs
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

            if (frameDurationNs > frozenFrameThresholdNs || !volatileFrameData.isJank) {
                // Frame duration is too big to be considered as a slow frame or not jank
                return
            }

            report.slowFramesDurationNs += frameDurationNs
            val previousSlowFrameRecord = report.lastSlowFrameRecord
            val delaySinceLastUpdate = frameStartedTimestampNs -
                (previousSlowFrameRecord?.startTimestampNs ?: frameStartedTimestampNs)

            if (previousSlowFrameRecord == null || delaySinceLastUpdate > continuousSlowFrameThresholdNs) {
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
                    frozenFrameThresholdNs - 1
                )
            }
        }
    }

    @WorkerThread
    override fun onAddLongTask(durationNs: Long) {
        val view = currentViewId
        if (durationNs >= anrDuration && view != null) {
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
            maxSlowFramesAmount,
            minimumViewLifetimeThresholdNs = minViewLifetimeThresholdNs
        )
    }

    companion object {
        private const val DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD_NS: Long =
            16_666_666L // 1/60 fps in nanoseconds
        private const val DEFAULT_FROZEN_FRAME_THRESHOLD_NS: Long = 700_000_000 // 700ms
        private const val DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT: Int = 512
        private const val DEFAULT_ANR_DURATION_NS: Long = 5_000_000_000L // 5s
        private const val DEFAULT_VIEW_LIFETIME_THRESHOLD_NS: Long = 100_000_000L // 100ms
    }
}
