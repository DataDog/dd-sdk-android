/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.canReadSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import com.datadog.android.internal.time.TimeProvider
import java.io.File

/**
 * Reads CPU usage as a percentage by computing successive deltas of the utime field
 * from `/proc/self/stat`.
 *
 * CLK_TCK = 100 Hz on Android bionic: 100 ticks/s ≡ 100% single-core CPU.
 * Multi-core spikes are clamped to 100.0. The first call always returns null.
 */
internal class CpuDatapointReader(
    internal val statFile: File = STAT_FILE,
    private val cpuTimeProvider: TimeProvider,
    override val intervalMs: Long,
    private val internalLogger: InternalLogger
) : DataPointsReader<Double>(cpuTimeProvider) {

    private var lastUtime: Double? = null
    private var lastTimestampMs: Long? = null

    @Suppress("ReturnCount")
    override fun readValue(): Double? {
        val nowMs = cpuTimeProvider.getDeviceTimestampMillis()
        val cpuTicks = readCpuTicks() ?: return null
        val prevTicks = lastUtime
        val prevMs = lastTimestampMs
        lastUtime = cpuTicks
        lastTimestampMs = nowMs
        if (prevTicks == null || prevMs == null) return null
        val elapsedMs = (nowMs - prevMs).coerceAtLeast(1L)
        // CLK_TCK = 100 Hz on Android bionic → 100 ticks/s = 100% CPU on one core
        return ((cpuTicks - prevTicks) * MS_PER_SECOND / elapsedMs).coerceIn(0.0, MAX_CPU_PERCENT)
    }

    @Suppress("ReturnCount")
    private fun readCpuTicks(): Double? {
        if (!statFile.existsSafe(internalLogger) || !statFile.canReadSafe(internalLogger)) return null
        val stat = statFile.readTextSafe(internalLogger = internalLogger) ?: return null
        val tokens = stat.split(' ')
        return when {
            tokens.size <= UTIME_IDX -> null
            tokens.size <= STIME_IDX -> tokens[UTIME_IDX].toDoubleOrNull()
            else -> {
                val utime = tokens[UTIME_IDX].toDoubleOrNull() ?: return null
                val stime = tokens[STIME_IDX].toDoubleOrNull() ?: return null
                utime + stime
            }
        }
    }

    companion object {
        private const val STAT_PATH = "/proc/self/stat"
        internal val STAT_FILE = File(STAT_PATH)
        private const val UTIME_IDX = 13
        private const val STIME_IDX = 14
        private const val MS_PER_SECOND = 1000.0
        private const val MAX_CPU_PERCENT = 100.0
    }
}
