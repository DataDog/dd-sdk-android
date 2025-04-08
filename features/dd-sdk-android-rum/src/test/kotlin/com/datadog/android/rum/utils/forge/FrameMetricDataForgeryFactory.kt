/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.FrameMetricsData
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FrameMetricDataForgeryFactory : ForgeryFactory<FrameMetricsData> {
    override fun getForgery(forge: Forge): FrameMetricsData {
        return FrameMetricsData(
            unknownDelayDuration = forge.aLong(min = 1),
            inputHandlingDuration = forge.aLong(min = 1),
            animationDuration = forge.aLong(min = 1),
            layoutMeasureDuration = forge.aLong(min = 1),
            drawDuration = forge.aLong(min = 1),
            syncDuration = forge.aLong(min = 1),
            commandIssueDuration = forge.aLong(min = 1),
            swapBuffersDuration = forge.aLong(min = 1),
            totalDuration = forge.aLong(min = 1),
            firstDrawFrame = forge.aBool(),
            intendedVsyncTimestamp = forge.aLong(min = 0),
            vsyncTimestamp = forge.aLong(min = 0),
            gpuDuration = forge.aLong(min = 1),
            deadline = forge.aLong(min = 1),
            displayRefreshRate = forge.aDouble(min = 1.0, max = 200.0),
            droppedFrames = forge.anInt(min = 0, max = 100)
        )
    }
}
