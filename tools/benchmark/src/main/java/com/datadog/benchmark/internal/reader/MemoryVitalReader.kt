/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal.reader

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Reads the device's `VmRSS` based on the `/proc/self/status` file.
 * cf. documentation https://man7.org/linux/man-pages/man5/procfs.5.html
 */
internal class MemoryVitalReader(
    internal val statusFile: File = STATUS_FILE,
    private val readerDelay: Long = STATUS_FILE_READ_INTERVAL_MS
) : VitalReader {

    @Volatile
    private var maxMemory: Double = 0.0
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val readRunnable: Runnable = Runnable {
        maxMemory = maxMemory.coerceAtLeast(readStatusFile() ?: 0.0)
        maxMemory = max(readStatusFile() ?: 0.0, maxMemory)
    }

    override fun readVitalData(): Double {
        val current = maxMemory
        maxMemory = 0.0
        return current
    }

    override fun start() {
        executorService.scheduleWithFixedDelay(readRunnable, 0L, readerDelay, TimeUnit.MILLISECONDS)
    }

    override fun stop() {
        executorService.shutdownNow()
    }

    private fun readStatusFile(): Double? {
        if (!(statusFile.exists() && statusFile.canRead())) {
            return null
        }

        val memorySizeKb = statusFile.readLines().firstNotNullOfOrNull { line ->
            VM_RSS_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)
        }?.toDoubleOrNull()

        return if (memorySizeKb == null) {
            null
        } else {
            memorySizeKb * BYTES_IN_KB
        }
    }

    override fun unit() = UNIT_BYTE

    override fun metricName(): String = METRIC_NAME_MEMORY

    companion object {

        private const val BYTES_IN_KB = 1000

        private const val STATUS_FILE_READ_INTERVAL_MS = 500L

        private const val STATUS_PATH = "/proc/self/status"
        internal val STATUS_FILE = File(STATUS_PATH)
        private const val VM_RSS_PATTERN = "VmRSS:\\s+(\\d+) kB"
        private val VM_RSS_REGEX = Regex(VM_RSS_PATTERN)

        private const val UNIT_BYTE = "byte"
        private const val METRIC_NAME_MEMORY = "android.benchmark.memory"
    }
}
