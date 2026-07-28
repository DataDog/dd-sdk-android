/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.sessionreplay.embedded.internal.rum.EmbeddedRumEventContextProvider
import com.datadog.android.sessionreplay.embedded.internal.rum.RumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Enriches records forwarded from an embedded engine's SDK with the current RUM context, stamps
 * them with the resolved slot id, and writes them into [EmbeddedReplayFeature]'s own storage.
 *
 * Records are only written when the current RUM session is tracked and the native Session Replay
 * feature is itself recording -- [EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME]'s
 * `session_replay_is_enabled` feature-context flag already reflects the combined RUM-session and
 * Session-Replay sample rate decision for the current session, so no separate sampling check is
 * needed here.
 */
internal class EmbeddedRecordsWriter(
    private val sdkCore: FeatureSdkCore,
    private val dataWriter: DataWriter<JsonObject>,
    private val rumContextProvider: EmbeddedRumEventContextProvider
) {

    fun write(slotId: String, viewId: String, records: List<JsonObject>) {
        if (records.isEmpty()) {
            return
        }
        sdkCore.getFeature(EmbeddedReplayFeature.EMBEDDED_REPLAY_FEATURE_NAME)
            ?.withWriteContext(
                withFeatureContexts = setOf(
                    Feature.RUM_FEATURE_NAME,
                    Feature.SESSION_REPLAY_FEATURE_NAME
                )
            ) { datadogContext, writeScope ->
                val rumContext = rumContextProvider.getRumContext(datadogContext) ?: return@withWriteContext
                val sessionReplayFeatureContext = datadogContext.featuresContext[
                    Feature.SESSION_REPLAY_FEATURE_NAME
                ]
                val sessionReplayEnabled = sessionReplayFeatureContext?.get(
                    SESSION_REPLAY_ENABLED_KEY
                ) as? Boolean ?: false
                if (rumContext.sessionState == RumContext.SESSION_TRACKED_STATE && sessionReplayEnabled) {
                    writeScope {
                        val enrichedRecord = buildEnrichedRecord(records, slotId, viewId, rumContext)
                        dataWriter.write(it, enrichedRecord, EventType.DEFAULT)
                    }
                }
            }
    }

    private fun buildEnrichedRecord(
        records: List<JsonObject>,
        slotId: String,
        viewId: String,
        rumContext: RumContext
    ): JsonObject {
        val stampedRecords = JsonArray().apply {
            records.forEach { record ->
                add(
                    record.deepCopy().apply {
                        addProperty(SLOT_ID_KEY, slotId)
                    }
                )
            }
        }
        return JsonObject().apply {
            addProperty(ENRICHED_RECORD_APPLICATION_ID_KEY, rumContext.applicationId)
            addProperty(ENRICHED_RECORD_SESSION_ID_KEY, rumContext.sessionId)
            addProperty(ENRICHED_RECORD_VIEW_ID_KEY, viewId)
            add(RECORDS_KEY, stampedRecords)
        }
    }

    companion object {
        internal const val SESSION_REPLAY_ENABLED_KEY = "session_replay_is_enabled"
        const val SLOT_ID_KEY = "slotId"
        const val ENRICHED_RECORD_APPLICATION_ID_KEY = "application_id"
        const val ENRICHED_RECORD_SESSION_ID_KEY = "session_id"
        const val ENRICHED_RECORD_VIEW_ID_KEY = "view_id"
        const val RECORDS_KEY = "records"
    }
}
