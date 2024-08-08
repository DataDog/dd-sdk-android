/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal

import android.view.Choreographer
import com.datadog.benchmark.ext.canReadSafe
import com.datadog.benchmark.ext.existsSafe
import com.datadog.benchmark.ext.readLinesSafe
import com.datadog.benchmark.ext.readTextSafe
import java.io.File
import java.util.concurrent.TimeUnit

internal class DatadogMetricProfiler {

    private var currentFps: Int = 0
    private var frameCount = 0
    private var lastFrameTime: Long = 0
    private val intervalMs = FPS_SAMPLE_INTERVAL_IN_MS

    private val statusFile = STATUS_FILE
    private val statFile = STAT_FILE

    private var lastCpuTicks = 0L

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
                currentFps = fps.toInt()
                lastFrameTime = currentFrameTime
                frameCount = 0
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun readMemoryVitalData(): Double? {
        if (!(statusFile.existsSafe() && statusFile.canReadSafe())) {
            return null
        }

        return statusFile.readLinesSafe()?.firstNotNullOfOrNull { line ->
            VM_RSS_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)
        }?.toDoubleOrNull()?.div(KB_IN_MB)
    }

    @Suppress("ReturnCount")
    fun readCpuVitalData(): Double? {
        if (!(statFile.existsSafe() && statFile.canReadSafe())) {
            return null
        }

        val stat = statFile.readTextSafe() ?: return null
        val tokens = stat.split(' ')
        val utime = if (tokens.size > UTIME_IDX) {
            tokens[UTIME_IDX].toLong()
        } else {
            null
        }
        val cpuTicksDiff = utime?.let { it - lastCpuTicks }
        lastCpuTicks = utime ?: 0L
        return cpuTicksDiff?.toDouble()
    }

    fun getCurrentFps(): Int {
        return currentFps
    }

    fun startFpsReader() {
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFpsReader() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    companion object {

        private const val KB_IN_MB = 1000

        private const val NANO_IN_SECOND = 1e9

        private const val FPS_SAMPLE_INTERVAL_IN_MS = 30L

        private const val STATUS_PATH = "/proc/self/status"
        internal val STATUS_FILE = File(STATUS_PATH)

        private const val STAT_PATH = "/proc/self/stat"
        internal val STAT_FILE = File(STAT_PATH)
        private const val VM_RSS_PATTERN = "VmRSS:\\s+(\\d+) kB"
        private val VM_RSS_REGEX = Regex(VM_RSS_PATTERN)
        private const val UTIME_IDX = 13
    }
}
