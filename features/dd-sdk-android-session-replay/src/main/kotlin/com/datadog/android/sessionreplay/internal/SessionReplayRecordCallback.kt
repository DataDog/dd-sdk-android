/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

internal class SessionReplayRecordCallback(
    private val featureSdkCore: FeatureSdkCore
) : RecordCallback {

    override fun onRecordForViewSent(record: EnrichedRecord) {
        updateViewMetadata(record.viewId, record.records.size)
    }

    fun onEmbeddedRecordsForViewSent(viewId: String, recordsCount: Int) {
        updateViewMetadata(viewId, recordsCount)
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateViewMetadata(viewId: String, recordsCount: Int) {
        if (recordsCount <= 0) {
            return
        }
        // This callback runs inside the synchronized write stage after persistence succeeds.
        // Update immediately so native and embedded record counts cannot be observed between writes.
        featureSdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME, useContextThread = false) {
            val viewMetadata = (it[viewId] as? MutableMap<String, Any?>) ?: mutableMapOf()
            viewMetadata[HAS_REPLAY_KEY] = true
            updateRecordsCount(viewMetadata, recordsCount)
            it[viewId] = viewMetadata
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
