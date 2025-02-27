/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.vitals

import android.os.Build
import androidx.metrics.performance.FrameData
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.FrameMetricsData
import java.util.concurrent.TimeUnit

internal class FPSVitalListener(
    private val vitalObserver: VitalObserver,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
    private var screenRefreshRate: Double = 60.0
) : FrameStateListener {
    private var frameDeadline = SIXTEEN_MS_NS
    private var displayRefreshRate: Double = SIXTY_FPS

    override fun onFrame(volatileFrameData: FrameData) {
        val durationNs = volatileFrameData.frameDurationUiNanos
        if (durationNs > 0.0) {
            var frameRate = (ONE_SECOND_NS / durationNs)

            if (buildSdkVersionProvider.version >= Build.VERSION_CODES.S) {
                screenRefreshRate = ONE_SECOND_NS / frameDeadline
            } else if (buildSdkVersionProvider.version == Build.VERSION_CODES.R) {
                screenRefreshRate = displayRefreshRate
            }

            // If normalized frame rate is still at over 60fps it means the frame rendered
            // quickly enough for the devices refresh rate.
            frameRate = (frameRate * (SIXTY_FPS / screenRefreshRate)).coerceAtMost(MAX_FPS)

            if (frameRate > MIN_FPS) {
                vitalObserver.onNewSample(frameRate)
            }
        }
    }

    override fun onFrameMetricsData(data: FrameMetricsData) {
        displayRefreshRate = data.displayRefreshRate
        if (buildSdkVersionProvider.version >= Build.VERSION_CODES.S) {
            frameDeadline = data.deadline
        }
    }

    companion object {
        private const val SIXTEEN_MS_NS: Long = 16666666
        private val ONE_SECOND_NS: Double = TimeUnit.SECONDS.toNanos(1).toDouble()

        private const val MIN_FPS: Double = 1.0
        private const val MAX_FPS: Double = 60.0
        private const val SIXTY_FPS: Double = 60.0
    }
}
