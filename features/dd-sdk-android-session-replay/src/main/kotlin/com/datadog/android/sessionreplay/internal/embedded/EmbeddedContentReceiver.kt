/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.JsonSerializer
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.collections.EvictingQueue
import com.datadog.android.sessionreplay.internal.processor.ResourceProcessor
import com.datadog.android.sessionreplay.internal.storage.EmbeddedContentRecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.Executor

internal class EmbeddedContentReceiver(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: () -> EmbeddedContentRecordWriter,
    private val resourceProcessor: () -> ResourceProcessor,
    private val isRecording: () -> Boolean,
    private val executor: () -> Executor,
    private val requestCapture: () -> Unit,
    private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
    private val internalLogger: InternalLogger
) {
    private val timeline = EmbeddedRecordTimeline(embeddedContentSlotRegistry)

    /**
     * Batches for slots that have no placeholder yet, keyed by slot ID.
     *
     * Bounded twice over — [MAX_PENDING_BATCHES] per slot, [MAX_PENDING_SLOTS] slots — because a
     * placeholder may never come. What the bounds displace is written rather than dropped: a
     * mis-ordered record beats a missing one.
     */
    private val pendingBatches = mutableMapOf<String, EvictingQueue<PendingBatch>>()

    init {
        embeddedContentSlotRegistry.addPlaceholderListener { slotId ->
            releasePendingBatches(slotId)
        }
    }

    fun receive(event: EmbeddedContentEvent) {
        if (!isRecording()) {
            return
        }
        when (event) {
            is EmbeddedContentEvent.RecordBatch -> handleRecordBatch(event)

            is EmbeddedContentEvent.Resource -> {
                if (rumContextProvider.getRumContext().hasValidApplicationAndSession()) {
                    val scheduledProcessor = resourceProcessor()
                    executor().executeSafe(PROCESS_EVENT_TASK_NAME, internalLogger) {
                        processResource(event, scheduledProcessor)
                    }
                }
            }
        }
    }

    private fun handleRecordBatch(event: EmbeddedContentEvent.RecordBatch) {
        if (event.records.isEmpty()) {
            return
        }
        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid()) {
            return
        }
        val pending = PendingBatch(event, rumContext, recordWriter())
        if (timeline.placeholderCovers(event)) {
            write(pending, timeline.shiftFor(pending))
        } else {
            hold(pending)
        }
    }

    private fun hold(pending: PendingBatch) {
        val slotId = pending.event.slotId
        val isFirstHeld: Boolean
        val displaced = synchronized(pendingBatches) {
            isFirstHeld = (pendingBatches[slotId]?.size ?: 0) == 0
            enqueue(pending)
        }
        writeAll(displaced)
        // The registry notifies its listeners outside the lock above, so a placeholder can appear
        // between the check and the enqueue with no listener left to fire. Checking again covers
        // that window; whichever path removes the queue is the one that writes it.
        if (timeline.placeholderCovers(pending.event)) {
            releasePendingBatches(slotId)
        } else if (isFirstHeld) {
            // Nothing else is guaranteed to draw the placeholder these batches are waiting for: the
            // host may be idle behind the embedded surface, and a draw that registering the slot did
            // schedule can still be skipped by the recorder's frame budget. Asking here rather than
            // when the slot is registered keys the request to the invariant actually being broken,
            // whatever broke it. Only for the batch that starts the queue — until it is released,
            // the queue stays non-empty, so this is one request per slot per view.
            requestCapture()
        }
    }

    /** Holds [pending] and returns whatever the bounds displaced to make room for it. */
    private fun enqueue(pending: PendingBatch): List<PendingBatch> {
        val displaced = mutableListOf<PendingBatch>()
        val slotId = pending.event.slotId
        if (pendingBatches.size >= MAX_PENDING_SLOTS && !pendingBatches.containsKey(slotId)) {
            pendingBatches.keys.firstOrNull()
                ?.let { pendingBatches.remove(it) }
                ?.let { displaced.addAll(it) }
        }
        val queue = pendingBatches.getOrPut(slotId) { EvictingQueue(MAX_PENDING_BATCHES) }
        if (queue.size >= MAX_PENDING_BATCHES) {
            // What EvictingQueue is about to evict, taken before add() discards it.
            queue.peek()?.let { displaced.add(it) }
        }
        queue.add(pending)
        return displaced
    }

    private fun releasePendingBatches(slotId: String) {
        val released = synchronized(pendingBatches) {
            pendingBatches.remove(slotId)
        } ?: return
        writeAll(released)
    }

    /**
     * Writes [batches], one shift per slot and view: batches held across a view change are not on
     * the same timeline as each other, and each view has its own placeholder and so its own floor.
     */
    private fun writeAll(batches: Collection<PendingBatch>) {
        batches.groupBy { it.event.slotId to it.event.viewId }.forEach { (_, group) ->
            val shift = timeline.shiftFor(group)
            group.forEach { write(it, shift) }
        }
    }

    private fun write(pending: PendingBatch, shift: Long) {
        executor().executeSafe(PROCESS_EVENT_TASK_NAME, internalLogger) {
            processRecordBatch(pending.event, pending.rumContext, pending.writer, shift)
        }
    }

    private fun processRecordBatch(
        event: EmbeddedContentEvent.RecordBatch,
        rumContext: SessionReplayRumContext,
        writer: EmbeddedContentRecordWriter,
        shift: Long
    ) {
        val records = JsonArray()
            .also { array ->
                event.records.forEach { record ->
                    val jsonRecord = JsonSerializer.toJsonElement(record)
                    if (jsonRecord is JsonObject) {
                        jsonRecord.addProperty(RECORD_SLOT_ID_KEY, event.slotId)
                        timeline.correctTimestamp(jsonRecord, record, rumContext.viewTimeOffsetMs, shift)
                        array.add(jsonRecord)
                    } else {
                        logInvalidRecord()
                    }
                }
            }

        if (records.size() == 0) {
            return
        }

        val enrichedRecord = JsonObject().apply {
            addProperty(APPLICATION_ID_KEY, rumContext.applicationId)
            addProperty(SESSION_ID_KEY, rumContext.sessionId)
            addProperty(VIEW_ID_KEY, event.viewId)
            add(RECORDS_KEY, records)
        }
        writer.writeRaw(
            enrichedRecord.toString().toByteArray(Charsets.UTF_8),
            event.viewId,
            records.size()
        )
    }

    private fun processResource(
        event: EmbeddedContentEvent.Resource,
        processor: ResourceProcessor
    ) {
        processor.process(event.identifier, event.data, event.mimeType)
    }

    private fun logInvalidRecord() {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { INVALID_EMBEDDED_RECORD_MESSAGE }
        )
    }

    companion object {
        // Enriched record envelope keys use the storage schema naming convention.
        internal const val APPLICATION_ID_KEY = "application_id"
        internal const val SESSION_ID_KEY = "session_id"
        internal const val VIEW_ID_KEY = "view_id"
        internal const val RECORDS_KEY = "records"

        // Embedded records use the Session Replay record schema naming convention.
        internal const val RECORD_SLOT_ID_KEY = "slotId"
        internal const val MAX_PENDING_BATCHES = 20
        internal const val MAX_PENDING_SLOTS = 10
        internal const val INVALID_EMBEDDED_RECORD_MESSAGE =
            "Session Replay received an invalid embedded content record."
        internal const val PROCESS_EVENT_TASK_NAME = "Embedded Session Replay event processing"
    }
}
