/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.metric.slowframes

import androidx.annotation.VisibleForTesting
import androidx.metrics.performance.FrameData
import com.datadog.android.rum.internal.domain.FrameMetricsData
import com.datadog.android.rum.internal.domain.state.SlowFrameRecord
import com.datadog.android.rum.internal.domain.state.ViewUIPerformanceData
import com.datadog.android.rum.internal.vitals.FrameStateListener
import com.datadog.tools.annotation.NoOpImplementation
import kotlin.math.min

@NoOpImplementation
internal interface SlowFramesListener : FrameStateListener {
    fun onViewCreated(viewId: String, startedTimestampNs: Long)
    fun resolveReport(viewId: String): ViewUIPerformanceData
}

internal class DataDogSlowFramesListener(
    @get:VisibleForTesting internal val maxSlowFramesAmount: Int = DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT,
    @get:VisibleForTesting internal val frozenFrameThresholdNs: Long = DEFAULT_FROZEN_FRAME_THRESHOLD,
    @get:VisibleForTesting internal val continuousSlowFrameThresholdNs: Long = DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD
) : SlowFramesListener {

    private var currentViewId: String? = null
    private var currentViewStartedTimeStampNs: Long = System.nanoTime()
    private val slowFramesRecords = mutableMapOf<String, ViewUIPerformanceData>()

    override fun onViewCreated(viewId: String, startedTimestampNs: Long) {
        currentViewId = viewId
        currentViewStartedTimeStampNs = startedTimestampNs
    }

    override fun resolveReport(viewId: String): ViewUIPerformanceData {
        return slowFramesRecords.remove(viewId) ?: ViewUIPerformanceData(System.nanoTime(), maxSlowFramesAmount)
    }

    override fun onFrame(volatileFrameData: FrameData) {
        val viewId = currentViewId ?: return
        val frameDurationNs = volatileFrameData.frameDurationUiNanos
        val frameStartedTimestampNs = volatileFrameData.frameStartNanos
        val uiPerformanceData = slowFramesRecords.getOrPut(viewId) {
            ViewUIPerformanceData(currentViewStartedTimeStampNs, maxSlowFramesAmount)
        }

        // Updating frames statistics
        uiPerformanceData.totalFramesDurationNs += frameDurationNs

        if (frameDurationNs > frozenFrameThresholdNs) {
            // Frame duration is too big to be considered as a slow frame
            return
        }

        if (!volatileFrameData.isJank) {
            // No reason for the future computation because frame is not jank
            return
        }

        uiPerformanceData.slowFramesDurationNs += frameDurationNs
        val previousSlowFrameRecord = uiPerformanceData.lastSlowFrameRecord
        val delaySinceLastUpdate =
            frameStartedTimestampNs - (previousSlowFrameRecord?.startTimestampNs ?: frameStartedTimestampNs)

        if (previousSlowFrameRecord == null || delaySinceLastUpdate > continuousSlowFrameThresholdNs) {
            // No previous slow frame record or amount of time since the last update
            // is significant enough to consider it idle - adding a new slow frame record.
            if (frameDurationNs > 0) {
                uiPerformanceData.slowFramesRecords += SlowFrameRecord(
                    frameStartedTimestampNs,
                    frameDurationNs
                )
            }
            return
        }

        // It's a continuous slow frame – increasing duration
        previousSlowFrameRecord.durationNs = min(
            previousSlowFrameRecord.durationNs + frameDurationNs,
            frozenFrameThresholdNs - 1
        )
    }

    override fun onFrameMetricsData(data: FrameMetricsData) {
    }

    companion object {
        private const val DEFAULT_CONTINUOUS_SLOW_FRAME_THRESHOLD: Long = 16_666_666L // 1/60 fps in nanoseconds
        private const val DEFAULT_FROZEN_FRAME_THRESHOLD: Long = 700_000_000 // 700ms
        private const val DEFAULT_SLOW_FRAME_RECORDS_MAX_AMOUNT: Int = 512
    }
}
