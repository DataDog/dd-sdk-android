/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal.reader

import com.datadog.benchmark.ext.canReadSafe
import com.datadog.benchmark.ext.existsSafe
import com.datadog.benchmark.ext.readTextSafe
import java.io.File

/**
 * Reads the CPU `utime` based on the `/proc/self/stat` file.
 * cf. documentation https://man7.org/linux/man-pages/man5/procfs.5.html
 */
internal class CPUVitalReader(
    internal val statFile: File = STAT_FILE
) : VitalReader {

    private var lastCpuTicks = 0L

    @Suppress("ReturnCount")
    override fun readVitalData(): Double? {
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

    override fun unit(): String? = null

    override fun metricName(): String = METRIC_NAME_CPU

    companion object {

        private const val STAT_PATH = "/proc/self/stat"
        internal val STAT_FILE = File(STAT_PATH)

        private const val METRIC_NAME_CPU = "android.benchmark.cpu"
        private const val UTIME_IDX = 13
    }
}
