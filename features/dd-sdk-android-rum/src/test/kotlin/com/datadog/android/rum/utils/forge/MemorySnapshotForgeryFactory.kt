/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.timeseries.MemorySnapshot
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class MemorySnapshotForgeryFactory : ForgeryFactory<MemorySnapshot> {
    override fun getForgery(forge: Forge): MemorySnapshot {
        val nonResidentKib = forge.aLong(min = 0L, max = MAX_MEMORY_KIB)
        return MemorySnapshot(
            footprintKib = nonResidentKib + forge.aLong(min = 1L, max = MAX_MEMORY_KIB),
            nonResidentKib = nonResidentKib
        )
    }

    private companion object {
        const val MAX_MEMORY_KIB = 16L * 1024L * 1024L
    }
}
