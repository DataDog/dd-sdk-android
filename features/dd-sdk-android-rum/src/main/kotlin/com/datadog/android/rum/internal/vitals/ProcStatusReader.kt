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
import com.datadog.android.core.internal.persistence.file.readLinesSafe
import java.io.File

/**
 * Reads memory fields from a `/proc/<pid>/status` file.
 *
 * The kernel prints these fields in kibibytes (1024 bytes), even though it labels them `kB`
 * (see `task_mem()` in `fs/proc/task_mmu.c`).
 * cf. documentation https://man7.org/linux/man-pages/man5/proc_pid_status.5.html
 */
internal class ProcStatusReader(
    private val internalLogger: InternalLogger,
    internal val statusFile: File = STATUS_FILE
) {

    /**
     * A memory field of the status file.
     *
     * @param key the name of the field in the file.
     */
    enum class Field(val key: String) {
        RSS_ANON("RssAnon"),
        VM_RSS("VmRSS"),
        VM_SWAP("VmSwap");

        internal val linePrefix = "$key:"
    }

    /**
     * Reads the memory [fields].
     *
     * @return the value of each field found in the file, in kibibytes. Fields that are missing or
     * that can't be parsed are absent, and the map is empty if the file can't be read.
     */
    @WorkerThread
    fun readFieldsKib(fields: List<Field>): Map<Field, Long> {
        if (!(statusFile.existsSafe(internalLogger) && statusFile.canReadSafe(internalLogger))) {
            return emptyMap()
        }
        return statusFile.readLinesSafe(internalLogger = internalLogger)
            .orEmpty()
            .mapNotNull { parseField(it, fields) }
            .toMap()
    }

    private fun parseField(line: String, fields: List<Field>): Pair<Field, Long>? {
        val field = fields.firstOrNull { line.startsWith(it.linePrefix) }
        return field?.let {
            line.substringAfter(':')
                .trim()
                .removeSuffix(UNIT_SUFFIX)
                .toLongOrNull()
                ?.let { valueKib -> field to valueKib }
        }
    }

    internal companion object {
        val STATUS_FILE = File("/proc/self/status")

        private const val UNIT_SUFFIX = " kB"
    }
}
