/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.view.Choreographer
import com.datadog.android.core.internal.utils.sdkLogger
import java.util.concurrent.TimeUnit

/**
 * Reads the UI framerate based on the [Choreographer.FrameCallback] and notify a [VitalObserver].
 */
internal class VitalFrameCallback(
    private val observer: VitalObserver,
    private val keepRunning: () -> Boolean
) : Choreographer.FrameCallback {

    internal var lastFrameTimestampNs: Long = 0L

    // region Choreographer.FrameCallback

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrameTimestampNs != 0L) {
            val durationNs = (frameTimeNanos - lastFrameTimestampNs).toDouble()
            if (durationNs > 0.0) {
                val frameRate = ONE_SECOND_NS / durationNs
                if (frameRate in VALID_FPS_RANGE) {
                    observer.onNewSample(frameRate)
                }
            }
        }
        lastFrameTimestampNs = frameTimeNanos

        @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
        if (keepRunning()) {
            try {
                Choreographer.getInstance().postFrameCallback(this)
            } catch (e: IllegalStateException) {
                sdkLogger.e("Unable to post VitalFrameCallback, thread doesn't have looper", e)
            }
        }
    }

    // endregion

    companion object {
        val ONE_SECOND_NS: Double = TimeUnit.SECONDS.toNanos(1).toDouble()

        private const val MIN_FPS: Double = 1.0
        private const val MAX_FPS: Double = 240.0
        val VALID_FPS_RANGE = MIN_FPS.rangeTo(MAX_FPS)
    }
}
