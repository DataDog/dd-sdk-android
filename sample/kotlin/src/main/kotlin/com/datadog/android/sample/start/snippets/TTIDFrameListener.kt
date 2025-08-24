/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.start.snippets

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi

internal data class TTIDFrameData(
    val totalDurationNanos: Long,
    val intendedVsyncNanos: Long,
    val vsyncTimeStampNanos: Long,
)

@RequiresApi(Build.VERSION_CODES.O)
internal fun subscribeToTTID(activity: Activity, block: (TTIDFrameData) -> Unit) {
    val handler = Handler(activity.mainLooper)

    val listener = object : Window.OnFrameMetricsAvailableListener {
        override fun onFrameMetricsAvailable(
            window: Window?,
            frameMetrics: FrameMetrics?,
            dropCountSinceLastInvocation: Int
        ) {
            if (frameMetrics != null) {
                val isFirstFrame = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)

                if (isFirstFrame == 1L) {
                    activity.window.removeOnFrameMetricsAvailableListener(this)

                    val result = TTIDFrameData(
                        totalDurationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
                        intendedVsyncNanos = frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                        vsyncTimeStampNanos = frameMetrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                    )
                    block(result)

                }
            }
        }
    }

    activity.window.addOnFrameMetricsAvailableListener(listener, handler)
}
