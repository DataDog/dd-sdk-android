/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

/**
 * What changed since the previous generation drained this changeset. Implementations merge with
 * [mergedWith] so signals arriving while a generation is active, or while a scheduled capture is
 * denied admission, accumulate instead of being dropped.
 */
internal interface CaptureChangeset {
    fun isEmpty(): Boolean
    fun mergedWith(other: CaptureChangeset): CaptureChangeset

    companion object {
        val EMPTY: CaptureChangeset = EmptyCaptureChangeset()
    }
}

private class EmptyCaptureChangeset : CaptureChangeset {
    override fun isEmpty(): Boolean = true
    override fun mergedWith(other: CaptureChangeset): CaptureChangeset = other
}
