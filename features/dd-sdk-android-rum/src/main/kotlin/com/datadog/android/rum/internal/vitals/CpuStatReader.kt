/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.canReadSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import java.io.File

/**
 * Reads the process' CPU time counters from the `/proc/self/stat` file.
 * cf. documentation https://man7.org/linux/man-pages/man5/procfs.5.html
 */
internal class CpuStatReader(
    internal val statFile: File = STAT_FILE,
    private val internalLogger: InternalLogger
) {

    /**
     * Cumulative user-mode CPU time (`utime`, field 14 of `/proc/self/stat`) in clock ticks,
     * or `null` when the file is unavailable or the field can't be parsed.
     */
    @WorkerThread
    fun readUserTime(): Double? = readTokens()?.getOrNull(UTIME_IDX)?.toDoubleOrNull()

    /**
     * Cumulative active CPU time (`utime` + `stime`, fields 14 & 15) in clock ticks. Falls back
     * to `utime` alone when `stime` is absent; returns `null` when the file is unavailable,
     * `utime` is missing/unparseable, or `stime` is present but unparseable.
     */
    @WorkerThread
    @Suppress("ReturnCount")
    fun readActiveTime(): Double? {
        val tokens = readTokens() ?: return null
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

    @WorkerThread
    private fun readTokens(): List<String>? {
        if (!(statFile.existsSafe(internalLogger) && statFile.canReadSafe(internalLogger))) return null
        return statFile.readTextSafe(internalLogger = internalLogger)?.split(' ')
    }

    companion object {
        private const val STAT_PATH = "/proc/self/stat"
        internal val STAT_FILE = File(STAT_PATH)
        private const val UTIME_IDX = 13
        private const val STIME_IDX = 14
    }
}
