/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.core.internal.persistence.file.canReadSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.readLinesSafe
import java.io.File

/**
 * Reads the device's `VmRSS` based on the `/proc/self/status` file.
 * cf. documentation https://man7.org/linux/man-pages/man5/procfs.5.html
 */
internal class MemoryVitalReader(
    internal val statusFile: File = STATUS_FILE
) : VitalReader {

    override fun readVitalData(): Double? {
        if (!(statusFile.existsSafe() && statusFile.canReadSafe())) {
            return null
        }

        val memorySizeKb = statusFile.readLinesSafe()
            ?.mapNotNull { line ->
                VM_RSS_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)
            }
            ?.firstOrNull()
            ?.toDoubleOrNull()

        return if (memorySizeKb == null) {
            null
        } else {
            memorySizeKb * 1000
        }
    }

    companion object {

        private const val STATUS_PATH = "/proc/self/status"
        internal val STATUS_FILE = File(STATUS_PATH)
        private const val VM_RSS_PATTERN = "VmRSS:\\s+(\\d+) kB"
        private val VM_RSS_REGEX = Regex(VM_RSS_PATTERN)
    }
}
