/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.sessionreplay.RECORD_TYPE_FULL_SNAPSHOT
import com.datadog.android.sessionreplay.internal.gson.safeGetAsJsonArray
import com.datadog.android.sessionreplay.internal.gson.safeGetAsJsonObject
import com.datadog.android.sessionreplay.internal.gson.safeGetAsLong
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.processor.tryFromSource
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/**
 *  Maps a batch to a Pair<MobileSegment, SerializedMobileSegment> for uploading.
 *  This class is meant for internal usage.
 */
internal class BatchesToSegmentsMapper(private val internalLogger: InternalLogger) {

    fun map(datadogContext: DatadogContext, batchData: List<ByteArray>): List<Pair<MobileSegment, JsonObject>> {
        return groupBatchDataIntoSegments(datadogContext, batchData)
    }

    // region Internal

    private fun groupBatchDataIntoSegments(
        datadogContext: DatadogContext,
        batchData: List<ByteArray>
    ): List<Pair<MobileSegment, JsonObject>> {
        return batchData
            .asSequence()
            .mapNotNull {
                @Suppress("SwallowedException")
                try {
                    JsonParser.parseString(String(it)).safeGetAsJsonObject(internalLogger)
                } catch (e: JsonParseException) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.TELEMETRY,
                        { UNABLE_TO_DESERIALIZE_ENRICHED_RECORD_ERROR_MESSAGE },
                        e
                    )
                    null
                } catch (e: IllegalStateException) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.TELEMETRY,
                        { UNABLE_TO_DESERIALIZE_ENRICHED_RECORD_ERROR_MESSAGE },
                        e
                    )
                    null
                }
            }
            .mapNotNull {
                val records = it.records()
                val rumContext = it.rumContext()
                if (records == null || rumContext == null || records.isEmpty) {
                    null
                } else {
                    Pair(rumContext, records)
                }
            }
            .groupBy { it.first }
            .mapValues {
                it.value.fold(JsonArray()) { acc, pair ->
                    acc.addAll(pair.second)
                    acc
                }
            }
            .filter { !it.value.isEmpty }
            .mapNotNull {
                mapToSegment(datadogContext, it.key, it.value)
            }
    }

    @Suppress("ReturnCount")
    private fun mapToSegment(
        datadogContext: DatadogContext,
        rumContext: SessionReplayRumContext,
        records: JsonArray
    ): Pair<MobileSegment, JsonObject>? {
        val orderedRecords = records
            .asSequence()
            .mapNotNull {
                it.safeGetAsJsonObject(internalLogger)
            }
            .mapNotNull {
                val timestamp = it.timestamp()
                if (timestamp == null) {
                    null
                } else {
                    Pair(it, timestamp)
                }
            }
            .sortedBy { it.second }
            .map { it.first }
            .fold(JsonArray()) { acc, jsonObject ->
                acc.add(jsonObject)
                acc
            }

        if (orderedRecords.isEmpty) {
            return null
        }

        val startTimestamp = orderedRecords
            .firstOrNull()
            ?.safeGetAsJsonObject(internalLogger)
            ?.timestamp()
        val stopTimestamp = orderedRecords
            .lastOrNull()
            ?.safeGetAsJsonObject(internalLogger)
            ?.timestamp()

        if (startTimestamp == null || stopTimestamp == null) {
            // this is just to avoid having kotlin warnings but the elements
            // without timestamp property were already removed in the logic above
            return null
        }

        val hasFullSnapshotRecord = hasFullSnapshotRecord(orderedRecords)
        val segment = MobileSegment(
            application = MobileSegment.Application(rumContext.applicationId),
            session = MobileSegment.Session(rumContext.sessionId),
            view = MobileSegment.View(rumContext.viewId),
            start = startTimestamp,
            end = stopTimestamp,
            recordsCount = orderedRecords.size().toLong(),
            // TODO RUM-861 Find a way or alternative to provide a reliable indexInView
            indexInView = null,
            hasFullSnapshot = hasFullSnapshotRecord,
            source = MobileSegment.Source.tryFromSource(datadogContext.source, internalLogger),
            records = emptyList()
        )
        val segmentAsJsonObject = segment.toJson().safeGetAsJsonObject(internalLogger)
            ?: return null
        segmentAsJsonObject.add(RECORDS_KEY, orderedRecords)
        return Pair(segment, segmentAsJsonObject)
    }

    private fun hasFullSnapshotRecord(records: JsonArray) =
        records.any {
            val typeAsLong = it.asJsonObject.getAsJsonPrimitive(RECORD_TYPE_KEY)?.safeGetAsLong(internalLogger)
            typeAsLong == FULL_SNAPSHOT_RECORD_TYPE_MOBILE || typeAsLong == FULL_SNAPSHOT_RECORD_TYPE_BROWSER
        }

    private fun JsonObject.records(): JsonArray? {
        return get(EnrichedRecord.RECORDS_KEY)?.safeGetAsJsonArray(internalLogger)
    }

    private fun JsonObject.timestamp(): Long? {
        return getAsJsonPrimitive(TIMESTAMP_KEY)?.safeGetAsLong(internalLogger)
    }

    private fun JsonObject.rumContext(): SessionReplayRumContext? {
        val applicationId = get(EnrichedRecord.APPLICATION_ID_KEY)?.asString
        val sessionId = get(EnrichedRecord.SESSION_ID_KEY)?.asString
        val viewId = get(EnrichedRecord.VIEW_ID_KEY)?.asString
        if (applicationId == null || sessionId == null || viewId == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE },
                null,
                true
            )
            return null
        }
        return SessionReplayRumContext(
            applicationId = applicationId,
            sessionId = sessionId,
            viewId = viewId
        )
    }

    // endregion

    companion object {

        private const val FULL_SNAPSHOT_RECORD_TYPE_MOBILE = RECORD_TYPE_FULL_SNAPSHOT
        private const val FULL_SNAPSHOT_RECORD_TYPE_BROWSER = 2L

        internal const val RECORDS_KEY = "records"
        private const val RECORD_TYPE_KEY = "type"
        internal const val TIMESTAMP_KEY = "timestamp"
        internal const val UNABLE_TO_DESERIALIZE_ENRICHED_RECORD_ERROR_MESSAGE =
            "SR BatchesToSegmentMapper: unable to deserialize EnrichedRecord"
        internal const val ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE =
            "SR BatchesToSegmentMapper: Enriched record was missing the context information"
    }
}
