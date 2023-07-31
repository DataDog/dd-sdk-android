/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

internal class SessionReplayRecordWriter(
    private val sdkCore: FeatureSdkCore,
    private val recordCallback: RecordCallback
) : RecordWriter {
    private var lastRumContextId: String = ""
    override fun write(record: EnrichedRecord) {
        val forceNewBatch = resolveForceNewBatch(record)
        sdkCore.getFeature(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME)
            ?.withWriteContext(forceNewBatch) { _, eventBatchWriter ->
                val serializedRecord = record.toJson().toByteArray(Charsets.UTF_8)
                synchronized(this) {
                    @Suppress("ThreadSafety") // called from the worker thread
                    if (eventBatchWriter.write(serializedRecord, null)) {
                        updateViewSent(record)
                    }
                }
            }
    }

    private fun updateViewSent(record: EnrichedRecord) {
        /**
         * We have to see whether it's ok that this method is being called from the background.
         * However this gives us the most certainty that the records were actually queued for
         * sending, and not optimized away in the processor. Depending upon the amount of time
         * that it takes to process the nodes, the view may not be relevant anymore.
         */
        recordCallback.onRecordForViewSent(record)
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
