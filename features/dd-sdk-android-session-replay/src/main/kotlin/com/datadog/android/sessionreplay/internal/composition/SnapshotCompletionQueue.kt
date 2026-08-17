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
                // Failure identities and details are per-view, so they are deliberately left out:
                // the message stays constant and only the bounded error codes are attached.
                val errorCodes = mapping.failures.map(CaptureValidationFailure::code).distinct().sorted()
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { INVALID_SNAPSHOT_MESSAGE },
                    additionalProperties = mapOf(
                        VALIDATION_ERROR_CODES_PROPERTY to errorCodes.map(Enum<*>::name),
                        VALIDATION_FAILURE_COUNT_PROPERTY to mapping.failures.size
                    )
                )
            }
        }
    }

    internal companion object {
        internal const val INVALID_SNAPSHOT_MESSAGE = "Dropping invalid completed composition snapshot"
        internal const val VALIDATION_ERROR_CODES_PROPERTY = "validation_error_codes"
        internal const val VALIDATION_FAILURE_COUNT_PROPERTY = "validation_failure_count"
    }
}

/**
 * The sole downstream handoff for composition snapshots. It accepts only completed generations,
 * preserves their serial order, and uses the generation deadline instead of creating a second
 * queue-specific expiry lifecycle.
 *
 * A new generation can start as soon as the previous one is handed off, so the backlog is bounded
 * to [maxQueuedCaptures] retained snapshots: when a slow write path cannot keep up, the oldest
 * queued snapshot is expired and dropped in favour of the newest visual state.
 */
internal class SnapshotCompletionQueue(
    private val executorService: ExecutorService,
    private val processor: SnapshotCompletionProcessor,
    private val internalLogger: InternalLogger,
    maxQueuedCaptures: Int = DEFAULT_MAX_QUEUED_CAPTURES
) : CompletedSnapshotConsumer {
    private val maxQueuedCaptures = maxQueuedCaptures.coerceAtLeast(1)
    private val lifecycleLock = Any()
    private val stopped = AtomicBoolean(false)
    private val drainScheduled = AtomicBoolean(false)
    private val captures = ConcurrentLinkedQueue<CompletedSnapshotCapture>()
    private var processingCapture: CompletedSnapshotCapture? = null
    private var droppedCaptureCount = 0L

    // ConcurrentLinkedQueue.offer throws NPE only for a null element; `capture` is a non-null Kotlin type.
    @Suppress("UnsafeThirdPartyFunctionCall")
    override fun consume(capture: CompletedSnapshotCapture) {
        var droppedCapture: CompletedSnapshotCapture? = null
        val accepted = synchronized(lifecycleLock) {
            if (stopped.get() || !capture.generation.isActive()) return@synchronized false
            if (captures.size >= maxQueuedCaptures) {
                droppedCapture = captures.poll()
                droppedCaptureCount++
            }
            captures.offer(capture)
        }
        droppedCapture?.let {
            it.generation.expire()
            // Saturation persists for as long as the write path is slow, so this is reported once
            // per process; the running total is reported when the queue stops.
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { QUEUE_SATURATED_MESSAGE },
                onlyOnce = true,
                additionalProperties = mapOf(MAX_QUEUED_CAPTURES_PROPERTY to maxQueuedCaptures)
            )
        }
        if (!accepted) {
            // This queue is the last stage of a generation, so a refused capture has to be expired
            // here or it would stay active - and keep its snapshot reachable - indefinitely.
            capture.generation.expire()
            return
        }
        scheduleDrain()
    }

    fun stop() {
        var totalDropped = 0L
        val didStop = synchronized(lifecycleLock) {
            if (!stopped.compareAndSet(false, true)) return@synchronized false
            processingCapture?.generation?.expire()
            while (true) {
                val capture = captures.poll() ?: break
                capture.generation.expire()
            }
            totalDropped = droppedCaptureCount
            true
        }
        if (!didStop) return
        if (totalDropped > 0L) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { DROPPED_CAPTURES_ON_STOP_MESSAGE },
                additionalProperties = mapOf(DROPPED_CAPTURE_COUNT_PROPERTY to totalDropped)
            )
        }
        executorService.shutdownNow()
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

    // Deliberately broad rather than targeted: this runs on an SDK-owned executor thread, so an
    // escaping throwable crashes the host app. The processor covers wire mapping, RUM context
    // lookup and a RecordWriter disk write, whose failure types are not enumerable from here, and
    // the queue must keep draining regardless of which one surfaces.
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

    internal companion object {
        internal const val DEFAULT_MAX_QUEUED_CAPTURES = 4
        internal const val QUEUE_SATURATED_MESSAGE = "Composition snapshot completion queue is saturated, " +
            "dropping the oldest queued snapshot to keep the newest visual state"
        internal const val DROPPED_CAPTURES_ON_STOP_MESSAGE =
            "Dropped saturated composition snapshots before the completion queue stopped"
        internal const val MAX_QUEUED_CAPTURES_PROPERTY = "max_queued_captures"
        internal const val DROPPED_CAPTURE_COUNT_PROPERTY = "dropped_capture_count"
        private const val EXECUTION_CONTEXT = "Session Replay composition snapshot completion"
    }
}
