/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import androidx.annotation.WorkerThread

/**
 * Reads the device's `VmRSS` based on the `/proc/self/status` file.
 * cf. documentation https://man7.org/linux/man-pages/man5/procfs.5.html
 */
internal class MemoryVitalReader(
    private val procStatusReader: ProcStatusReader
) : VitalReader {

    @WorkerThread
    override fun readVitalData(): Double? = procStatusReader.readFieldsKib(FIELDS)[ProcStatusReader.Field.VM_RSS]
        ?.let { it.toDouble() * BYTES_IN_KB }

    private companion object {
        const val BYTES_IN_KB = 1000

        val FIELDS = listOf(ProcStatusReader.Field.VM_RSS)
    }
}
