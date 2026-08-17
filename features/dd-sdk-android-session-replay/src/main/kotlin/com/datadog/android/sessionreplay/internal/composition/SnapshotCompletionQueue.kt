/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

internal fun interface SnapshotCompletionProcessor {
    fun process(capture: CompletedSnapshotCapture)
}

internal class DefaultSnapshotCompletionProcessor(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: RecordWriter,
    private val internalLogger: InternalLogger,
    private val wireMapper: CapturedTreeWireMapper = DefaultCapturedTreeWireMapper()
) : SnapshotCompletionProcessor {
    override fun process(capture: CompletedSnapshotCapture) {
        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid() || rumContext.viewId != capture.snapshot.scope.value) {
            capture.generation.expire()
            return
        }

        when (val mapping = wireMapper.mapFullSnapshot(capture.snapshot)) {
            is CaptureWireMappingResult.Success -> {
                if (capture.generation.tryAccept()) {
                    recordWriter.write(
                        EnrichedRecord(
                            applicationId = rumContext.applicationId,
                            sessionId = rumContext.sessionId,
                            viewId = rumContext.viewId,
                            records = listOf(mapping.value)
                        )
                    )
                }
            }

            is CaptureWireMappingResult.Invalid -> {
                capture.generation.expire()
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { "Dropping invalid completed composition snapshot: ${mapping.failures}" }
                )
            }
        }
    }
}

/**
 * The sole downstream handoff for composition snapshots. It accepts only completed generations,
 * preserves their serial order, and uses the generation deadline instead of creating a second
 * queue-specific expiry lifecycle.
 */
internal class SnapshotCompletionQueue(
    private val executorService: ExecutorService,
    private val processor: SnapshotCompletionProcessor,
    private val internalLogger: InternalLogger
) : CompletedSnapshotConsumer {
    private val lifecycleLock = Any()
    private val stopped = AtomicBoolean(false)
    private val drainScheduled = AtomicBoolean(false)
    private val captures = ConcurrentLinkedQueue<CompletedSnapshotCapture>()
    private var processingCapture: CompletedSnapshotCapture? = null

    // ConcurrentLinkedQueue.offer throws NPE only for a null element; `capture` is a non-null Kotlin type.
    @Suppress("UnsafeThirdPartyFunctionCall")
    override fun consume(capture: CompletedSnapshotCapture) {
        val accepted = synchronized(lifecycleLock) {
            if (stopped.get() || !capture.generation.isActive()) return@synchronized false
            captures.offer(capture)
        }
        if (!accepted) return
        scheduleDrain()
    }

    fun stop() {
        val didStop = synchronized(lifecycleLock) {
            if (!stopped.compareAndSet(false, true)) return@synchronized false
            processingCapture?.generation?.expire()
            while (true) {
                val capture = captures.poll() ?: break
                capture.generation.expire()
            }
            true
        }
        if (didStop) {
            executorService.shutdownNow()
        }
    }

    private fun scheduleDrain() {
        if (!stopped.get() && drainScheduled.compareAndSet(false, true)) {
            if (stopped.get()) {
                drainScheduled.set(false)
            } else {
                executorService.executeSafe(EXECUTION_CONTEXT, internalLogger, ::drain)
            }
        }
    }

    private fun drain() {
        try {
            while (true) {
                val capture = nextCapture() ?: break
                processCapture(capture)
            }
        } finally {
            drainScheduled.set(false)
            if (!stopped.get() && captures.isNotEmpty()) scheduleDrain()
        }
    }

    private fun nextCapture(): CompletedSnapshotCapture? = synchronized(lifecycleLock) {
        if (stopped.get()) return@synchronized null
        captures.poll()?.also { processingCapture = it }
    }

    // The processor is a pluggable seam (wire mapping, RUM context lookup, disk write via
    // RecordWriter) and can throw for reasons outside our control; the queue must keep draining
    // subsequent captures regardless.
    @Suppress("TooGenericExceptionCaught")
    private fun processCapture(capture: CompletedSnapshotCapture) {
        try {
            if (capture.generation.isActive()) processor.process(capture)
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { "Composition snapshot completion processing threw an exception" },
                e
            )
        } finally {
            clearProcessingCapture(capture)
        }
    }

    private fun clearProcessingCapture(capture: CompletedSnapshotCapture) {
        synchronized(lifecycleLock) {
            if (processingCapture === capture) processingCapture = null
        }
    }

    private companion object {
        const val EXECUTION_CONTEXT = "Session Replay composition snapshot completion"
    }
}
