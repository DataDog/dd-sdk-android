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

@Suppress("TooManyFunctions")
internal class EmbeddedContentReceiver(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: () -> EmbeddedContentRecordWriter,
    private val resourceProcessor: () -> ResourceProcessor,
    private val isRecording: () -> Boolean,
    private val executor: () -> Executor,
    private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry,
    private val internalLogger: InternalLogger
) {
    /**
     * Batches for slots that have no placeholder yet, keyed by slot ID.
     *
     * Bounded twice over — [MAX_PENDING_BATCHES] per slot, [MAX_PENDING_SLOTS] slots — because a
     * placeholder may never come. What the bounds displace is written rather than dropped: a
     * mis-ordered record beats a missing one.
     */
    private val pendingBatches = mutableMapOf<String, EvictingQueue<PendingBatch>>()

    private class PendingBatch(
        val event: EmbeddedContentEvent.RecordBatch,
        val rumContext: SessionReplayRumContext,
        val writer: EmbeddedContentRecordWriter
    )

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
        if (placeholderCovers(event)) {
            write(pending, timestampShift(pending))
        } else {
            hold(pending)
        }
    }

    /**
     * Whether the slot's placeholder has been emitted in the same view [event] is addressed to.
     *
     * The view matters as much as the slot: records only composite into a placeholder that shares
     * their segment, so one left over from a previous view is no help to this batch.
     */
    private fun placeholderCovers(event: EmbeddedContentEvent.RecordBatch): Boolean =
        embeddedContentSlotRegistry.placeholder(event.slotId)?.viewId == event.viewId

    /** Parks [pending] until a placeholder for its slot and view has been emitted. */
    private fun hold(pending: PendingBatch) {
        val displaced = synchronized(pendingBatches) { enqueue(pending) }
        writeAll(displaced)
        // The registry notifies its listeners outside the lock above, so a placeholder can appear
        // between the check and the enqueue with no listener left to fire. Checking again covers
        // that window; whichever path removes the queue is the one that writes it.
        if (placeholderCovers(pending.event)) {
            releasePendingBatches(pending.event.slotId)
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

    /** Writes everything held for [slotId], now that a placeholder for it has been emitted. */
    private fun releasePendingBatches(slotId: String) {
        val released = synchronized(pendingBatches) {
            pendingBatches.remove(slotId)
        } ?: return
        writeAll(released)
    }

    /**
     * Writes [batches], one shift per slot and view.
     *
     * Batches held across a view change are not on the same timeline as each other and must not
     * share a shift: each view has its own placeholder, and so its own floor.
     */
    private fun writeAll(batches: Collection<PendingBatch>) {
        batches.groupBy { it.event.slotId to it.event.viewId }.forEach { (_, group) ->
            val shift = timestampShift(group)
            group.forEach { write(it, shift) }
        }
    }

    private fun write(pending: PendingBatch, shift: Long) {
        executor().executeSafe(PROCESS_EVENT_TASK_NAME, internalLogger) {
            processRecordBatch(pending.event, pending.rumContext, pending.writer, shift)
        }
    }

    private fun timestampShift(batch: PendingBatch): Long =
        timestampShift(batch.event, batch.earliestTimestamp())

    private fun timestampShift(batches: Collection<PendingBatch>): Long {
        val event = batches.firstOrNull()?.event ?: return NO_SHIFT
        val earliest = batches.mapNotNull { it.earliestTimestamp() }.minOrNull()
        return timestampShift(event, earliest)
    }

    /**
     * How far forward records move so the earliest of them clears the slot's placeholder, or
     * [NO_SHIFT] when they already do — the steady-state case.
     *
     * One shift for the whole set rather than a floor applied to each record: clamping pins
     * everything captured before the placeholder to the same millisecond, so a gesture spread over
     * several frames replays as a single jump. Moving the set keeps every original interval intact.
     */
    private fun timestampShift(event: EmbeddedContentEvent.RecordBatch, earliest: Long?): Long {
        val placeholder = embeddedContentSlotRegistry.placeholder(event.slotId)
            ?.takeIf { it.viewId == event.viewId }
        if (placeholder == null || earliest == null) {
            return NO_SHIFT
        }
        return maxOf(NO_SHIFT, placeholder.timestamp + 1 - earliest)
    }

    /** The batch's earliest record timestamp, corrected for the view's server time offset. */
    private fun PendingBatch.earliestTimestamp(): Long? = event.records
        .mapNotNull { record -> record.timestampOrNull()?.let { it + rumContext.viewTimeOffsetMs } }
        .minOrNull()

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
                        correctTimestamp(jsonRecord, record, rumContext, shift)
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

    /**
     * Puts [jsonRecord] on the same timeline as the native records it is replayed alongside: the
     * view's server time offset, which native records already carry and these do not, then the
     * placeholder [shift] computed by [timestampShift].
     */
    private fun correctTimestamp(
        jsonRecord: JsonObject,
        record: Map<String, Any?>,
        rumContext: SessionReplayRumContext,
        shift: Long
    ) {
        val timestamp = record.timestampOrNull() ?: return
        jsonRecord.addProperty(RECORD_TIMESTAMP_KEY, timestamp + rumContext.viewTimeOffsetMs + shift)
    }

    /** The record's own capture time, before any correction, or `null` if it carries none usable. */
    private fun Map<String, Any?>.timestampOrNull(): Long? {
        return when (val timestamp = this[RECORD_TIMESTAMP_KEY]) {
            is Number -> timestamp.toLong()
            is String -> timestamp.toLongOrNull()
            else -> null
        }
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
        internal const val RECORD_TIMESTAMP_KEY = "timestamp"
        internal const val MAX_PENDING_BATCHES = 20
        internal const val MAX_PENDING_SLOTS = 10
        internal const val NO_SHIFT = 0L
        internal const val INVALID_EMBEDDED_RECORD_MESSAGE =
            "Session Replay received an invalid embedded content record."
        internal const val PROCESS_EVENT_TASK_NAME = "Embedded Session Replay event processing"
    }
}
