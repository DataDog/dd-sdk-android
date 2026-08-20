/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Resolves pixel captures only after the native copy has been constructed on the worker. */
internal class ChainedCapturedSnapshotProcessor(
    private val constructionProcessor: CapturedSnapshotProcessor,
    private val pixelProcessor: CapturedSnapshotProcessor
) : CapturedSnapshotProcessor {
    @Suppress("UnsafeThirdPartyFunctionCall") // Atomic references accept null and are owned by this request.
    override fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork {
        val cancelled = AtomicBoolean(false)
        val pixelWork = AtomicReference<CancellableCaptureWork>()
        val constructionWork = constructionProcessor.process(request) { result ->
            if (!cancelled.get()) {
                when (result) {
                    is SnapshotProcessingResult.Completed -> {
                        val work = pixelProcessor.process(request.copy(snapshot = result.snapshot), callback)
                        pixelWork.set(work)
                        if (cancelled.get()) work.cancel()
                    }
                    is SnapshotProcessingResult.Failed -> callback.onProcessed(result)
                }
            }
        }
        return CancellableCaptureWork {
            cancelled.set(true)
            constructionWork.cancel()
            pixelWork.get()?.cancel()
        }
    }
}
