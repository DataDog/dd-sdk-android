/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal data class SnapshotProcessingRequest(
    val generation: CaptureGenerationContext,
    val snapshot: CapturedSnapshotInput
)

internal sealed interface SnapshotProcessingResult {
    val generationId: Long

    data class Completed(
        override val generationId: Long,
        val snapshot: CapturedFullSnapshot
    ) : SnapshotProcessingResult

    data class Failed(
        override val generationId: Long
    ) : SnapshotProcessingResult
}

internal fun interface SnapshotProcessingCallback {
    fun onProcessed(result: SnapshotProcessingResult)
}

/**
 * Owns every asynchronous enrichment operation for one capture generation. Implementations must
 * report one terminal result. Cancelling unfinished work cancels the generation scope; after the
 * callback hands off a result, cancellation must not invalidate that downstream result.
 */
internal fun interface CapturedSnapshotProcessor {
    fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork
}
