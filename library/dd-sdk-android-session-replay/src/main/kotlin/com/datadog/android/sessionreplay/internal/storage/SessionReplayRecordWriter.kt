/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.sessionreplay.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.v2.api.SdkCore

internal class SessionReplayRecordWriter(private val sdkCore: SdkCore) : RecordWriter {
    private var lastRumContextId: String = ""
    override fun write(record: EnrichedRecord) {
        val forceNewBatch = resolveForceNewBatch(record)
        sdkCore.getFeature(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME)
            ?.withWriteContext(forceNewBatch) { _, eventBatchWriter ->
                val serializedRecord = record.toJson().toByteArray(Charsets.UTF_8)
                synchronized(this) {
                    @Suppress("ThreadSafety") // called from the worker thread
                    eventBatchWriter.write(serializedRecord, null)
                }
            }
    }

    private fun resolveForceNewBatch(record: EnrichedRecord): Boolean {
        val newRumContextId = resoleRumContextId(record)
        val forceNewBatch = lastRumContextId != newRumContextId
        lastRumContextId = newRumContextId
        return forceNewBatch
    }

    private fun resoleRumContextId(record: EnrichedRecord): String {
        return "${record.applicationId}-${record.sessionId}-${record.viewId}"
    }
}
