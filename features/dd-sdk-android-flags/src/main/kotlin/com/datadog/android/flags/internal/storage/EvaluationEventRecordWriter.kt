/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.storage

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.flags.internal.EvaluationEventWriter
import com.datadog.android.flags.model.BatchedFlagEvaluations

/**
 * Persists serialized flag evaluation events to SDK Core storage.
 *
 * Events are written to the EVALUATIONS feature storage and will be uploaded
 * to the EVP intake endpoint by the SDK Core's upload mechanism.
 */
internal class EvaluationEventRecordWriter(private val sdkCore: FeatureSdkCore) : EvaluationEventWriter {
    override fun writeAll(events: List<BatchedFlagEvaluations.FlagEvaluation>) {
        if (events.isEmpty()) return

        sdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)
            ?.withWriteContext { _, writeScope ->
                writeScope { batchWriter ->
                    for (event in events) {
                        val serializedRecord = event.toJson().toString().toByteArray(Charsets.UTF_8)
                        val rawBatchEvent = RawBatchEvent(data = serializedRecord)
                        batchWriter.write(
                            event = rawBatchEvent,
                            batchMetadata = null,
                            eventType = EventType.DEFAULT
                        )
                    }
                }
            }
    }
}
