/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.content.Context
import android.view.Choreographer
import android.view.WindowManager
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.v2.api.InternalLogger
import java.util.concurrent.TimeUnit

/**
 * Reads the UI framerate based on the [Choreographer.FrameCallback] and notify a [VitalObserver].
 */
internal class VitalFrameCallback(
    private val appContext: Context,
    private val observer: VitalObserver,
    private val keepRunning: () -> Boolean
) : Choreographer.FrameCallback {

    internal var lastFrameTimestampNs: Long = 0L

    // region Choreographer.FrameCallback

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrameTimestampNs != 0L) {
            val durationNs = (frameTimeNanos - lastFrameTimestampNs).toDouble()
            if (durationNs > 0.0) {
                val refreshRateScale = detectRefreshRateScale()
                val rawFps = ONE_SECOND_NS / durationNs
                val frameRate = rawFps * refreshRateScale
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
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    "Unable to post VitalFrameCallback, thread doesn't have looper",
                    e
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun detectRefreshRateScale(): Double {
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return if (windowManager == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                "WindowManager is null, can't detect max refresh rate!"
            )
            1.0
        } else if (windowManager.defaultDisplay == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                "Display is null, can't detect max refresh rate!"
            )
            1.0
        } else {
            STANDARD_FPS / windowManager.defaultDisplay.refreshRate
        }
    }

    // endregion

    companion object {
        val ONE_SECOND_NS: Double = TimeUnit.SECONDS.toNanos(1).toDouble()

        private const val MIN_FPS: Double = 1.0
        private const val STANDARD_FPS: Double = 60.0
        val VALID_FPS_RANGE = MIN_FPS.rangeTo(STANDARD_FPS)
    }
}
