/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import java.util.concurrent.atomic.AtomicLong

internal class BatchStatsWriter(
    private val sdkCore: FeatureSdkCore,
    private val runtimeID: String
) : StatsWriter {
    private val sequenceNumber = AtomicLong(0)

    override fun write(statBuckets: List<ClientStatsBucket>) {
        sdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)
            ?.withWriteContext { datadogContext, writeScope ->
                val payload = ClientStatsPayload(
                    hostname = "", // left blank intentionally
                    env = datadogContext.env,
                    version = datadogContext.version,
                    service = datadogContext.service,
                    tracerVersion = datadogContext.sdkVersion,
                    runtimeID = runtimeID,
                    sequenceNumber = sequenceNumber.getAndIncrement(),
                    stats = statBuckets
                )

                writeScope { batchWriter ->
                    val rawBatchEvent = RawBatchEvent(data = payload.toMsgPackPayload())
                    batchWriter.write(
                        event = rawBatchEvent,
                        batchMetadata = null,
                        eventType = EventType.DEFAULT
                    )
                }
            }
    }
}
