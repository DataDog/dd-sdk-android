/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.thread.NamedRunnable
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Constructs the tree off the UI thread, under the capture's existing deadline and cancellation scope. */
internal class ExecutorCapturedSnapshotProcessor(
    private val executor: Executor,
    private val internalLogger: InternalLogger,
    private val builder: CapturedNativeSnapshotBuilder = CapturedNativeSnapshotBuilder()
) : CapturedSnapshotProcessor {
    override fun process(
        request: SnapshotProcessingRequest,
        callback: SnapshotProcessingCallback
    ): CancellableCaptureWork {
        val generation = request.generation
        val terminal = AtomicBoolean(false)

        @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts this non-null input.
        val pending = AtomicReference(request)
        val work = CancellableCaptureWork {
            @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts a null cleared value.
            pending.set(null)
            // Once the result is handed off, the completion queue owns its remaining lifetime.
            if (terminal.compareAndSet(false, true)) generation.expire()
        }
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // Rejection is handled below; no Future cancellation is required.
            executor.execute(
                NamedRunnable(EXECUTION_CONTEXT) {
                    val input = pending.getAndSet(null) ?: return@NamedRunnable
                    val snapshot = buildSafely(input)
                    if (input.generation.isActive() && terminal.compareAndSet(false, true)) {
                        callback.onProcessed(
                            snapshot?.let { SnapshotProcessingResult.Completed(input.generation.id, it) }
                                ?: SnapshotProcessingResult.Failed(input.generation.id)
                        )
                    }
                }
            )
        } catch (e: RejectedExecutionException) {
            @Suppress("UnsafeThirdPartyFunctionCall") // AtomicReference accepts a null cleared value.
            pending.set(null)
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { "Unable to schedule composition snapshot construction" },
                e,
                onlyOnce = true
            )
            if (terminal.compareAndSet(false, true)) {
                callback.onProcessed(SnapshotProcessingResult.Failed(request.generation.id))
            }
        }
        return work
    }

    @Suppress("TooGenericExceptionCaught") // Contain mapper failures at the SDK processing boundary.
    private fun buildSafely(input: SnapshotProcessingRequest): CapturedFullSnapshot? = try {
        if (!input.generation.isActive()) {
            null
        } else {
            when (val copied = input.snapshot) {
                is CapturedNativeSnapshot -> builder.build(copied, input.generation)
                is CapturedFullSnapshot -> copied
            }
        }
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            { "Composition snapshot construction threw an exception" },
            e,
            onlyOnce = true
        )
        null
    }

    private companion object {
        const val EXECUTION_CONTEXT = "Session Replay composition snapshot construction"
    }
}
