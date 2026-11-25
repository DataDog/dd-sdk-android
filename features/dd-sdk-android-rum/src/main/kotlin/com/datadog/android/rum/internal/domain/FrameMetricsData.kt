/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.domain

import android.os.Build
import androidx.annotation.RequiresApi

internal data class FrameMetricsData(
    var droppedFrames: Int = 0,
    var unknownDelayDuration: Long = 0L,
    var inputHandlingDuration: Long = 0L,
    var animationDuration: Long = 0L,
    var layoutMeasureDuration: Long = 0L,
    var drawDuration: Long = 0L,
    var syncDuration: Long = 0L,
    var commandIssueDuration: Long = 0L,
    var swapBuffersDuration: Long = 0L,
    var totalDuration: Long = 0L,
    var firstDrawFrame: Boolean = false,
    @RequiresApi(Build.VERSION_CODES.O) var intendedVsyncTimestamp: Long = 0L,
    @RequiresApi(Build.VERSION_CODES.O) var vsyncTimestamp: Long = 0L,
    @RequiresApi(Build.VERSION_CODES.S) var gpuDuration: Long = 0L,
    @RequiresApi(Build.VERSION_CODES.S) var deadline: Long = 0L,
    var displayRefreshRate: Double = SIXTY_FPS
) {
    companion object {
        private const val SIXTY_FPS: Double = 60.0
    }
}
