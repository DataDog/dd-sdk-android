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
        val terminal = AtomicBoolean(false)
        val generation = request.generation
        val pending = AtomicReference(request)
        val pixelWork = AtomicReference<CancellableCaptureWork>()
        val complete = SnapshotProcessingCallback { result ->
            if (terminal.compareAndSet(false, true)) callback.onProcessed(result)
        }
        val constructionWork = constructionProcessor.process(request) { result ->
            val input = pending.getAndSet(null)
            if (!terminal.get() && input != null) {
                when (result) {
                    is SnapshotProcessingResult.Completed -> {
                        val work = pixelProcessor.process(input.copy(snapshot = result.snapshot), complete)
                        pixelWork.set(work)
                        if (terminal.get()) work.cancel()
                    }
                    is SnapshotProcessingResult.Failed -> complete.onProcessed(result)
                }
            }
        }
        return CancellableCaptureWork {
            pending.set(null)
            if (terminal.compareAndSet(false, true)) {
                generation.expire()
                constructionWork.cancel()
                pixelWork.get()?.cancel()
            }
        }
    }
}
