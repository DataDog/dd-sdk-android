/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import androidx.annotation.MainThread
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.utils.GlobalBounds

/**
 * Discovers roots and synchronously inspects Android/Compose state on the main thread. Concrete
 * walkers must use [CaptureGenerationContext.shouldContinue] between bounded operations and may
 * return a contract-provided placeholder or cached resource before the deadline. [changeset]
 * identifies what triggered this generation so a walker may skip untouched subtrees; an empty
 * changeset means the trigger carried no such information and everything should be considered
 * changed.
 */
internal fun interface CapturedSnapshotProducer {
    @MainThread
    fun capture(context: CaptureGenerationContext, changeset: CaptureChangeset): CapturedFullSnapshot?
}

/**
 * What changed since the previous generation drained this changeset. Implementations merge with
 * [mergedWith] so signals arriving while a generation is active, or while a scheduled capture is
 * denied admission, accumulate instead of being dropped.
 */
internal interface CaptureChangeset {
    fun isEmpty(): Boolean
    fun mergedWith(other: CaptureChangeset): CaptureChangeset

    companion object {
        val EMPTY: CaptureChangeset = EmptyCaptureChangeset
    }
}

private object EmptyCaptureChangeset : CaptureChangeset {
    override fun isEmpty(): Boolean = true
    override fun mergedWith(other: CaptureChangeset): CaptureChangeset = other
}

internal fun GlobalBounds.toCaptured() = CapturedBounds(x, y, width, height)

internal data class CapturedFullSnapshot(
    val timestamp: Long,
    val scope: RumViewIdentityScope,
    val root: CapturedLayer?,
    val layers: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)
