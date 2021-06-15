/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.view.Choreographer
import java.util.concurrent.TimeUnit

/**
 * Reads the UI framerate based on the [Choreographer.FrameCallback].
 */
internal class FrameRateVitalReader : VitalReader, Choreographer.FrameCallback {

    private var lastFrameTimestampNs: Long = 0L
    private var lastFrameRate: Double = Double.NaN

    // region VitalReader

    override fun readVitalData(): Double? {
        return if (lastFrameRate.isNaN()) null else lastFrameRate
    }

    // endregion

    // region Choreographer.FrameCallback

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrameTimestampNs != 0L) {
            val durationNs = (frameTimeNanos - lastFrameTimestampNs).toDouble()
            if (durationNs > 0.0) {
                lastFrameRate = ONE_SECOND_NS / durationNs
            }
        }
        lastFrameTimestampNs = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    // endregion

    companion object {
        val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1).toDouble()
    }
}
