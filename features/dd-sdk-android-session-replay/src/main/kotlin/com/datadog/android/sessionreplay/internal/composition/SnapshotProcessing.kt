/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory

internal data class SnapshotProcessingRequest(
    val generation: CaptureGenerationContext,
    val snapshot: CapturedFullSnapshot,
    val pendingPixelCaptures: List<PendingPixelCapture> = emptyList(),
    val identityFactory: CompositionIdentityFactory? = null
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

internal data class CompletedSnapshotCapture(
    val generation: CaptureGenerationContext,
    val snapshot: CapturedFullSnapshot
)

internal fun interface SnapshotProcessingCallback {
    fun onProcessed(result: SnapshotProcessingResult)
}

/**
 * Owns every asynchronous enrichment operation for one capture generation. Implementations must
 * report one terminal result. Cancelling the returned work cancels the whole generation scope.
 */
internal fun interface CapturedSnapshotProcessor {
    fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork
}

/** Default processing scope until asynchronous enrichers are supplied by capture implementations. */
internal class ImmediateCapturedSnapshotProcessor : CapturedSnapshotProcessor {
    override fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork {
        callback.onProcessed(
            SnapshotProcessingResult.Completed(request.generation.id, request.snapshot)
        )
        return CancellableCaptureWork.NONE
    }
}

/** Receives only complete snapshots; traversal and enrichment never enter the downstream queue. */
internal fun interface CompletedSnapshotConsumer {
    fun consume(capture: CompletedSnapshotCapture)
}
