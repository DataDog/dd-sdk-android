/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal.reader

import android.util.Log
import android.view.Choreographer
import java.util.concurrent.TimeUnit

internal class FpsVitalReader : VitalReader {

    private var currentFps: Double = 0.0
    private var frameCount = 0
    private var lastFrameTime: Long = 0
    private val intervalMs = FPS_SAMPLE_INTERVAL_IN_MS

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameTime == 0L) {
                lastFrameTime = System.nanoTime()
            }

            frameCount++
            val currentFrameTime = System.nanoTime()
            val elapsedTime: Long = currentFrameTime - lastFrameTime

            if (elapsedTime >= TimeUnit.MILLISECONDS.toNanos(intervalMs)) {
                val fps: Double = frameCount / (elapsedTime / NANO_IN_SECOND)
                currentFps = fps
                lastFrameTime = currentFrameTime
                frameCount = 0
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun readVitalData(): Double {
        return currentFps
    }

    override fun start() {
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun unit(): String = UNIT_FRAME

    override fun metricName(): String = METRIC_NAME_FPS

    override fun stop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    companion object {
        private const val FPS_SAMPLE_INTERVAL_IN_MS = 30L
        private const val NANO_IN_SECOND = 1e9
        private const val UNIT_FRAME = "frame"

        private const val METRIC_NAME_FPS = "android.benchmark.fps"
    }
}
