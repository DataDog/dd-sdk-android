/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.provider

import androidx.annotation.WorkerThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.timeseries.MemorySnapshot
import com.datadog.android.rum.internal.vitals.ProcStatusReader
import com.datadog.android.rum.internal.vitals.ProcStatusReader.Field

/**
 * Reads the memory of the current process from the `/proc/self/status` file.
 *
 * The footprint is the anonymous resident memory (`RssAnon`) plus the swapped-out memory (`VmSwap`),
 * which is also reported as the non-resident portion. `VmRSS` replaces `RssAnon` on kernels that
 * don't report it (before 4.5), and a missing `VmSwap` means that nothing is swapped out.
 */
internal class MemoryDatapointReader(
    private val procStatusReader: ProcStatusReader,
    timeProvider: TimeProvider,
    override val intervalMs: Long
) : DataPointsReader<MemorySnapshot>(timeProvider) {

    @WorkerThread
    override fun readValue(): MemorySnapshot? {
        val valuesKib = procStatusReader.readFieldsKib(FIELDS)
        val residentKib = valuesKib[Field.RSS_ANON] ?: valuesKib[Field.VM_RSS]
        val swapKib = valuesKib[Field.VM_SWAP] ?: 0L
        return residentKib?.let {
            MemorySnapshot(footprintKib = it + swapKib, nonResidentKib = swapKib)
        }
    }

    private companion object {
        val FIELDS = listOf(Field.RSS_ANON, Field.VM_RSS, Field.VM_SWAP)
    }
}
