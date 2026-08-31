/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal fun interface SnapshotCompletionProcessor {
    fun process(capture: CompletedSnapshotCapture)
}

/** The last accepted snapshot a generation can be diffed against, and when it was last a full one. */
private data class RetainedSnapshotState(
    val snapshot: CapturedFullSnapshot,
    val orientation: Int,
    val lastFullSnapshotAtNs: Long
)

/**
 * Bundles composition wire-mapping with the view-lifecycle records the player needs around it -
 * mirrors legacy `RecordedDataProcessor`'s new-view handling: every genuinely new RUM view opens
 * with a [MobileSegment.MobileRecord.MetaRecord] (viewport size) and
 * [MobileSegment.MobileRecord.FocusRecord] before its first wireframe content, and the previous
 * view (if any) is closed with a [MobileSegment.MobileRecord.ViewEndRecord]. Without a Meta
 * record, a player following this rrweb-derived protocol has no viewport to render into at all -
 * this is not an optional enrichment, every view's very first record set depends on it.
 *
 * Also drains [resourceDataQueueHandler] once per processed capture, mirroring the legacy
 * pipeline's own per-draw-cycle call to the same [DataQueueHandler.tryToConsumeItems] (see
 * `WindowsOnDrawListener`/`RecorderWindowCallback`). Resource bytes for any pixel capture this
 * generation resolved (see `PixelFallbackSnapshotProcessor`/`ResourceResolver`) are queued in
 * memory by [ResourceItemCreationHandler][com.datadog.android.sessionreplay.internal.recorder.resources.ResourceItemCreationHandler]
 * well before this point, but never actually written to storage or uploaded until something
 * calls [DataQueueHandler.tryToConsumeItems] - without a caller, those items sat in memory
 * forever, so every composition-tree pixel capture's `resourceId` referenced a resource that had
 * never actually been persisted anywhere.
 *
 * Also diffs each completed generation against the last *accepted* one (retained only inside the
 * [CaptureGenerationContext.tryAccept]-gated success branch below, so an expired/rejected
 * generation never corrupts it) and emits up to two independent incremental mutation records where
 * possible - a layer-structure mutation via [CapturedSnapshotDiffer], and a wireframe-content
 * mutation via [CapturedTreeWireMapper.mapWireframeMutation] - falling back to a full snapshot on a
 * new RUM view, a periodic checkpoint, an orientation change, or a layer mutation that unexpectedly
 * fails validation (self-healing rather than dropping the generation) - mirroring legacy
 * `RecordedDataProcessor`'s `isNewView`/`isTimeForFullSnapshot`/`screenOrientationChanged` gating
 * for this pipeline's state. Emitting the two mutations as separate records - rather than forcing a
 * full snapshot whenever wireframe content changes - mirrors the iOS composition pipeline, which
 * emits its own layer-tree mutation and flat-wireframe mutation independently for the same reason.
 *
 * An orientation change additionally gets a [MobileSegment.MobileIncrementalData.ViewportResizeData]
 * record ahead of the (still-forced) full snapshot, exactly mirroring legacy `RecordedDataProcessor`:
 * unlike the iOS composition pipeline, an Android rotation re-measures and re-lays-out nearly every
 * View on screen anyway, so a full resend stays justified here - the gap this closes is narrower
 * than iOS's: this pipeline was simply missing the viewport-resize record legacy already sends,
 * not the full-snapshot behavior itself.
 */
internal class DefaultSnapshotCompletionProcessor(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: RecordWriter,
    private val internalLogger: InternalLogger,
    private val timeProvider: TimeProvider,
    private val orientationProvider: OrientationProvider = DefaultOrientationProvider(),
    private val wireMapper: CapturedTreeWireMapper = DefaultCapturedTreeWireMapper(),
    private val resourceDataQueueHandler: DataQueueHandler? = null
) : SnapshotCompletionProcessor {

    // Only ever read/written from SnapshotCompletionQueue's single draining thread - same
    // single-threaded-processor assumption legacy RecordedDataProcessor's own prevRumContext relies on.
    private var lastViewContext: SessionReplayRumContext? = null
    private var retained: RetainedSnapshotState? = null

    override fun process(capture: CompletedSnapshotCapture) {
        // Whatever resources this generation's pixel captures resolved were already queued
        // upstream, in PixelFallbackSnapshotProcessor, before this callback ever fires -
        // unconditional and first, so it runs regardless of which branch below this capture
        // ends up taking (including the early-return/invalid-context guard).
        resourceDataQueueHandler?.tryToConsumeItems()

        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid() || rumContext.viewId != capture.snapshot.scope.value) {
            capture.generation.expire()
            return
        }

        val currentOrientation = orientationProvider.currentOrientation()
        val orientationChanged = retained?.orientation?.let { it != currentOrientation } ?: false
        when (val mapping = resolveMapping(capture.snapshot, currentOrientation)) {
            is CaptureWireMappingResult.Success -> {
                if (capture.generation.tryAccept()) {
                    writeViewEndRecordIfViewChanged(rumContext, capture.snapshot.timestamp)
                    val records = mutableListOf<MobileSegment.MobileRecord>()
                    if (lastViewContext?.viewId != rumContext.viewId) {
                        records += viewOpeningRecords(capture)
                        lastViewContext = rumContext
                    }
                    if (orientationChanged) records += viewportResizeRecord(capture.snapshot)
                    records += mapping.value
                    recordWriter.write(
                        EnrichedRecord(
                            applicationId = rumContext.applicationId,
                            sessionId = rumContext.sessionId,
                            viewId = rumContext.viewId,
                            records = records
                        )
                    )
                    retained = RetainedSnapshotState(
                        snapshot = capture.snapshot,
                        orientation = currentOrientation,
                        lastFullSnapshotAtNs = lastFullSnapshotAtNs(mapping.value)
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

    private fun writeViewEndRecordIfViewChanged(rumContext: SessionReplayRumContext, timestamp: Long) {
        val previous = lastViewContext ?: return
        if (previous.viewId == rumContext.viewId) return
        recordWriter.write(
            EnrichedRecord(
                applicationId = previous.applicationId,
                sessionId = previous.sessionId,
                viewId = previous.viewId,
                records = listOf(MobileSegment.MobileRecord.ViewEndRecord(timestamp))
            )
        )
    }

    private fun viewOpeningRecords(capture: CompletedSnapshotCapture): List<MobileSegment.MobileRecord> {
        val timestamp = capture.snapshot.timestamp
        val bounds = capture.snapshot.root?.bounds
        return listOf(
            MobileSegment.MobileRecord.MetaRecord(
                timestamp = timestamp,
                data = MobileSegment.Data1(width = bounds?.width ?: 0L, height = bounds?.height ?: 0L)
            ),
            MobileSegment.MobileRecord.FocusRecord(
                timestamp = timestamp,
                data = MobileSegment.Data2(hasFocus = true)
            )
        )
    }

    private fun viewportResizeRecord(
        snapshot: CapturedFullSnapshot
    ): MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord {
        val bounds = snapshot.root?.bounds
        return MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            timestamp = snapshot.timestamp,
            data = MobileSegment.MobileIncrementalData.ViewportResizeData(
                width = bounds?.width ?: 0L,
                height = bounds?.height ?: 0L
            )
        )
    }

    /** Only a full snapshot resets the periodic-checkpoint clock; a mutation cycle leaves it running. */
    private fun lastFullSnapshotAtNs(records: List<MobileSegment.MobileRecord>): Long =
        if (records.any { it is MobileSegment.MobileRecord.MobileFullSnapshotRecord }) {
            timeProvider.getDeviceElapsedTimeNanos()
        } else {
            retained?.lastFullSnapshotAtNs ?: timeProvider.getDeviceElapsedTimeNanos()
        }

    @Suppress("ReturnCount")
    private fun resolveMapping(
        snapshot: CapturedFullSnapshot,
        currentOrientation: Int
    ): CaptureWireMappingResult<List<MobileSegment.MobileRecord>> {
        val retainedState = retained ?: return wireMapper.mapFullSnapshot(snapshot).asRecordList()
        val fullSnapshotRequired = retainedState.snapshot.scope != snapshot.scope ||
            currentOrientation != retainedState.orientation ||
            isTimeForFullSnapshot(retainedState)
        if (fullSnapshotRequired) return wireMapper.mapFullSnapshot(snapshot).asRecordList()

        val mutation = CapturedSnapshotDiffer.diff(retainedState.snapshot, snapshot)
            ?: return wireMapper.mapFullSnapshot(snapshot).asRecordList()

        return when (val mutationMapping = wireMapper.mapMutation(mutation, retainedState.snapshot)) {
            is CaptureWireMappingResult.Success -> {
                val records = mutableListOf<MobileSegment.MobileRecord>(mutationMapping.value)
                wireMapper.mapWireframeMutation(retainedState.snapshot, snapshot)?.let(records::add)
                CaptureWireMappingResult.Success(records)
            }

            is CaptureWireMappingResult.Invalid -> {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { "Computed mutation failed validation, retrying as a full snapshot: ${mutationMapping.failures}" }
                )
                wireMapper.mapFullSnapshot(snapshot).asRecordList()
            }
        }
    }

    // listOf throws only for a null element; `value` is a non-null Kotlin type.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun <T : MobileSegment.MobileRecord> CaptureWireMappingResult<T>.asRecordList():
        CaptureWireMappingResult<List<MobileSegment.MobileRecord>> = when (this) {
        is CaptureWireMappingResult.Success -> CaptureWireMappingResult.Success(listOf(value))
        is CaptureWireMappingResult.Invalid -> this
    }

    private fun isTimeForFullSnapshot(retainedState: RetainedSnapshotState): Boolean =
        timeProvider.getDeviceElapsedTimeNanos() - retainedState.lastFullSnapshotAtNs >= FULL_SNAPSHOT_INTERVAL_NS

    private companion object {
        val FULL_SNAPSHOT_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(3000)
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
