/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

/**
 * A snapshot of the memory of the current process.
 *
 * @param footprintKib memory charged to the process, in kibibytes, including the pages that the
 * system has swapped out.
 * @param nonResidentKib the portion of [footprintKib] that the system has swapped out, in kibibytes.
 */
internal data class MemorySnapshot(
    val footprintKib: Long,
    val nonResidentKib: Long
)
