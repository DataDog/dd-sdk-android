/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.net

import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/**
 *  Maps a batch to a List<Pair<MobileSegment, SerializedMobileSegment>> for uploading.
 *  This class is meant for internal usage.
 */
class BatchesToSegmentsMapper {

    @Suppress("UndocumentedPublicFunction")
    fun map(batchData: List<ByteArray>): List<Pair<MobileSegment, JsonObject>> {
        return groupBatchDataIntoSegments(batchData)
    }

    // region Internal

    private fun groupBatchDataIntoSegments(batchData: List<ByteArray>):
        List<Pair<MobileSegment, JsonObject>> {
        return batchData
            .mapNotNull {
                @Suppress("SwallowedException")
                try {
                    JsonParser.parseString(String(it)).asJsonObject
                } catch (e: JsonParseException) {
                    // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
                    null
                } catch (e: IllegalStateException) {
                    // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
                    null
                }
            }
            .map {
                val applicationId = it.get(EnrichedRecord.APPLICATION_ID_KEY).asString
                val sessionId = it.get(EnrichedRecord.SESSION_ID_KEY).asString
                val viewId = it.get(EnrichedRecord.VIEW_ID_KEY).asString
                val context = SessionReplayRumContext(applicationId, sessionId, viewId)
                val records = it.get(EnrichedRecord.RECORDS_KEY).asJsonArray
                Pair(context, records)
            }
            .groupBy { it.first }
            .mapValues {
                it.value.fold(JsonArray()) { acc, pair ->
                    acc.addAll(pair.second)
                    acc
                }
            }
            .filter { !it.value.isEmpty }
            .mapNotNull { entry ->
                @Suppress("SwallowedException")
                try {
                    groupToSegmentsPair(entry)
                } catch (e: JsonParseException) {
                    // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
                    null
                } catch (e: IllegalStateException) {
                    // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
                    null
                }
            }
    }

    private fun groupToSegmentsPair(entry: Map.Entry<SessionReplayRumContext, JsonArray>):
        Pair<MobileSegment, JsonObject> {
        val records = entry.value
            .map { it.asJsonObject }
            .sortedBy {
                it.getAsJsonPrimitive(TIMESTAMP_KEY).asLong
            }

        // we are filtering out empty records so we are safe to call first/last functions
        @Suppress("UnsafeThirdPartyFunctionCall")
        val startTimestamp = records.first().getAsJsonPrimitive(TIMESTAMP_KEY).asLong

        @Suppress("UnsafeThirdPartyFunctionCall")
        val stopTimestamp = records.last().getAsJsonPrimitive(TIMESTAMP_KEY).asLong
        val hasFullSnapshotRecord = hasFullSnapshotRecord(records)
        val segment = MobileSegment(
            application = MobileSegment.Application(entry.key.applicationId),
            session = MobileSegment.Session(entry.key.sessionId),
            view = MobileSegment.View(entry.key.viewId),
            start = startTimestamp,
            end = stopTimestamp,
            recordsCount = records.size.toLong(),
            // TODO: RUMM-2518 Find a way or alternative to provide a reliable indexInView
            indexInView = null,
            hasFullSnapshot = hasFullSnapshotRecord,
            source = MobileSegment.Source.ANDROID,
            records = emptyList()
        )
        val segmentAsJsonObject = segment.toJson().asJsonObject
        segmentAsJsonObject.add(RECORDS_KEY, entry.value)
        return Pair(segment, segmentAsJsonObject)
    }

    private fun hasFullSnapshotRecord(records: List<JsonObject>) =
        records.firstOrNull {
            it.getAsJsonPrimitive(RECORD_TYPE_KEY).asLong == FULL_SNAPSHOT_RECORD_TYPE
        } != null

    // endregion

    companion object {
        private const val FULL_SNAPSHOT_RECORD_TYPE = 10L
        internal const val RECORDS_KEY = "records"
        private const val RECORD_TYPE_KEY = "type"
        internal const val TIMESTAMP_KEY = "timestamp"
    }
}
