/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.prerequisite

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.canReadSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.readLinesSafe
import java.io.File

internal class MemoryRequirementChecker(
    private val minRamSizeMb: Int,
    private val memInfoFile: File = File(MEM_INFO_PATH),
    private val internalLogger: InternalLogger
) : SystemRequirementChecker {

    private var checkedValue: Long? = null

    override fun checkMinimumRequirement(): Boolean {
        if (minRamSizeMb == 0) {
            return true
        }
        return getMaxRAMSize() >= minRamSizeMb
    }

    override fun name(): String = MEMORY_CHECK_NAME

    override fun checkedValue(): Any? = checkedValue

    private fun getMaxRAMSize(): Long {
        if (!(memInfoFile.existsSafe(internalLogger) && memInfoFile.canReadSafe(internalLogger))) {
            return 0L
        }

        val memorySizeKb = memInfoFile.readLinesSafe(internalLogger = internalLogger)?.mapNotNull { line ->
            if (line.startsWith(MEM_TOTAL_REGEX)) {
                val tokens = line.split(Regex("\\s+"))
                if (tokens.size > 1) {
                    tokens[1].toLongOrNull() // The memory value is in kB
                } else {
                    null
                }
            } else {
                null
            }
        }?.firstOrNull()

        val value = if (memorySizeKb == null) {
            0L
        } else {
            memorySizeKb / KB_IN_MB
        }
        checkedValue = value
        return value
    }

    companion object {
        private const val KB_IN_MB = 1000

        private const val MEM_INFO_PATH = "/proc/meminfo"
        private const val MEM_TOTAL_REGEX = "MemTotal:"

        /**
         * This name will be used in telemetry as the attribute key.
         */
        private const val MEMORY_CHECK_NAME = "ram"
    }
}
