/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View

/** A [CaptureChangeset] of decor views observed to have drawn since it was last drained. */
internal class CompositionChangeset private constructor(
    private val windows: Set<View>
) : CaptureChangeset {
    fun changedWindows(): List<View> = windows.toList()

    override fun isEmpty(): Boolean = windows.isEmpty()

    // Empty means "no information, treat everything as changed" (see CapturedSnapshotProducer), so
    // it must dominate the merge rather than act as an identity value: merging a known window set
    // with an unknown/full invalidation must stay a full invalidation in either direction.
    override fun mergedWith(other: CaptureChangeset): CaptureChangeset = when {
        isEmpty() || other.isEmpty() -> EMPTY
        other is CompositionChangeset -> CompositionChangeset(windows + other.windows)
        else -> other
    }

    companion object {
        val EMPTY: CompositionChangeset = CompositionChangeset(emptySet())

        fun of(windows: List<View>): CompositionChangeset =
            if (windows.isEmpty()) EMPTY else CompositionChangeset(windows.toSet())
    }
}
