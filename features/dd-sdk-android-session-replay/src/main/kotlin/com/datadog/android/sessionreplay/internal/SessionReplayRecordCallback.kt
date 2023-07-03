/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

internal class SessionReplayRecordCallback(private val featureSdkCore: FeatureSdkCore) :
    RecordCallback {

    @Suppress("UNCHECKED_CAST")
    override fun onRecordForViewSent(record: EnrichedRecord) {
        val recordsSize = record.records.size
        if (recordsSize > 0) {
            featureSdkCore.updateFeatureContext(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME) {
                val viewId = record.viewId
                val viewMetadata: MutableMap<String, Any?> =
                    (it[viewId] as? MutableMap<String, Any?>) ?: mutableMapOf()
                viewMetadata[HAS_REPLAY_KEY] = true
                updateRecordsCount(viewMetadata, recordsSize)
                it[viewId] = viewMetadata
            }
        }
    }

    private fun updateRecordsCount(
        viewMetadata: MutableMap<String, Any?>,
        recordsCount: Int
    ) {
        val currentRecords = viewMetadata[VIEW_RECORDS_COUNT_KEY] as? Long ?: 0
        val newRecords = currentRecords + recordsCount
        viewMetadata[VIEW_RECORDS_COUNT_KEY] = newRecords
    }

    companion object {
        internal const val HAS_REPLAY_KEY = "has_replay"
        internal const val VIEW_RECORDS_COUNT_KEY = "records_count"
    }
}
