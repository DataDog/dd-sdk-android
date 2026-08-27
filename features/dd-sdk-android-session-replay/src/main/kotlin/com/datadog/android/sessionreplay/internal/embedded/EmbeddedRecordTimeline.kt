/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import com.datadog.android.sessionreplay.internal.storage.EmbeddedContentRecordWriter
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.google.gson.JsonObject

/** A received batch, with the context and writer it was received under. */
internal class PendingBatch(
    val event: EmbeddedContentEvent.RecordBatch,
    val rumContext: SessionReplayRumContext,
    val writer: EmbeddedContentRecordWriter
)

/** Puts embedded records on the same timeline as the native records they are replayed alongside. */
internal class EmbeddedRecordTimeline(
    private val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry
) {
    /**
     * Whether the slot's placeholder has been emitted in the same view [event] is addressed to. The
     * view matters as much as the slot: records only composite into a placeholder that shares their
     * segment, so one left over from a previous view is no help to this batch.
     */
    fun placeholderCovers(event: EmbeddedContentEvent.RecordBatch): Boolean =
        embeddedContentSlotRegistry.placeholder(event.slotId)?.viewId == event.viewId

    fun shiftFor(batch: PendingBatch): Long = shiftFor(batch.event, batch.earliestTimestamp())

    fun shiftFor(batches: Collection<PendingBatch>): Long {
        val event = batches.firstOrNull()?.event ?: return NO_SHIFT
        return shiftFor(event, batches.mapNotNull { it.earliestTimestamp() }.minOrNull())
    }

    /** Applies the view's server time offset, which native records carry and these do not, then [shift]. */
    fun correctTimestamp(
        jsonRecord: JsonObject,
        record: Map<String, Any?>,
        viewTimeOffsetMs: Long,
        shift: Long
    ) {
        val timestamp = record.timestampOrNull() ?: return
        jsonRecord.addProperty(RECORD_TIMESTAMP_KEY, timestamp + viewTimeOffsetMs + shift)
    }

    /**
     * How far forward records move so the earliest of them clears the slot's placeholder, or
     * [NO_SHIFT] when they already do — the steady-state case.
     *
     * One shift for the whole set rather than a floor applied to each record: clamping pins
     * everything captured before the placeholder to the same millisecond, so a gesture spread over
     * several frames replays as a single jump. Moving the set keeps every original interval intact.
     */
    private fun shiftFor(event: EmbeddedContentEvent.RecordBatch, earliest: Long?): Long {
        val placeholder = embeddedContentSlotRegistry.placeholder(event.slotId)
            ?.takeIf { it.viewId == event.viewId }
        if (placeholder == null || earliest == null) {
            return NO_SHIFT
        }
        return maxOf(NO_SHIFT, placeholder.timestamp + 1 - earliest)
    }

    private fun PendingBatch.earliestTimestamp(): Long? = event.records
        .mapNotNull { record -> record.timestampOrNull()?.let { it + rumContext.viewTimeOffsetMs } }
        .minOrNull()

    private fun Map<String, Any?>.timestampOrNull(): Long? =
        when (val timestamp = this[RECORD_TIMESTAMP_KEY]) {
            is Number -> timestamp.toLong()
            is String -> timestamp.toLongOrNull()
            else -> null
        }

    companion object {
        // Embedded records use the Session Replay record schema naming convention.
        internal const val RECORD_TIMESTAMP_KEY = "timestamp"
        internal const val NO_SHIFT = 0L
    }
}
