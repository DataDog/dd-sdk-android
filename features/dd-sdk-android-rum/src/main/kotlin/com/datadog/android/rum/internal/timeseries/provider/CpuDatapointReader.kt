/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import androidx.annotation.WorkerThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.vitals.CpuStatReader

/**
 * Reads CPU usage as a percentage of total device capacity by computing successive
 * deltas of the utime+stime CPU time provided by [CpuStatReader], divided by the number of
 * available processors.
 *
 * CLK_TCK = 100 Hz on Android bionic: 100 ticks/s per core. Dividing by the core
 * count normalizes the result to [0, 100] relative to total device CPU capacity,
 * so multi-threaded work is represented correctly without artificial clamping.
 * The first call always returns null.
 */
internal class CpuDatapointReader(
    private val cpuStatReader: CpuStatReader,
    private val timeProvider: TimeProvider,
    override val intervalMs: Long,
    private val availableProcessors: Int = Runtime.getRuntime().availableProcessors()
) : DataPointsReader<Double>(timeProvider) {

    private var lastUtime: Double? = null
    private var lastNs: Long? = null

    @WorkerThread
    override fun readValue() = cpuStatReader.readActiveTime()?.let { currentCpuTicks ->
        val nowNs = timeProvider.getDeviceElapsedTimeNanos()
        val (prevCpuTicks, prevNs) = updateValues(currentCpuTicks, nowNs)
        if (prevCpuTicks == null || prevNs == null) return null
        val elapsedMs = ((nowNs - prevNs) / NS_PER_MS).coerceAtLeast(1L)
        // Sampling is suspended while the app is not in the foreground, so a gap much longer than
        // the sampling interval means this delta spans that pause and would smear background CPU
        // time over the first foreground sample. Drop it; the read above already re-baselined.
        if (elapsedMs > intervalMs * MAX_GAP_FACTOR) return null
        val corePercent = (currentCpuTicks - prevCpuTicks) * MS_PER_SECOND / elapsedMs
        (corePercent / availableProcessors.coerceAtLeast(1)).coerceIn(0.0, MAX_CPU_PERCENT)
    }

    private fun updateValues(
        cpuTicks: Double,
        nowNs: Long
    ): Pair<Double?, Long?> {
        val prevCpuTicks = lastUtime
        val prevNs = lastNs
        lastUtime = cpuTicks
        lastNs = nowNs
        return prevCpuTicks to prevNs
    }

    companion object {
        private const val MS_PER_SECOND = 1000.0
        private const val NS_PER_MS = 1_000_000L
        private const val MAX_CPU_PERCENT = 100.0

        // How many sampling intervals a delta may span before it is treated as a gap.
        internal const val MAX_GAP_FACTOR = 2
    }
}
