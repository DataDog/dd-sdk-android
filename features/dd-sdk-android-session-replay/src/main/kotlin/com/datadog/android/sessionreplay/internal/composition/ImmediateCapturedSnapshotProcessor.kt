/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

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
