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

    override fun write(statBuckets: List<ClientStatsBucket>, forced: Boolean) {
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
                    val written = batchWriter.write(
                        event = rawBatchEvent,
                        batchMetadata = null,
                        eventType = EventType.DEFAULT
                    )
                    if (written) {
                        sendFlushMetric(statBuckets, forced)
                    }
                }
            }
    }

    private fun sendFlushMetric(buckets: List<ClientStatsBucket>, forced: Boolean) {
        var groupsCount = 0
        var spansCount = 0L
        var errorsCount = 0L
        for (bucket in buckets) {
            groupsCount += bucket.stats.size
            for (group in bucket.stats) {
                spansCount += group.hits
                errorsCount += group.errors
            }
        }

        sdkCore.internalLogger.logMetric(
            messageBuilder = { METRIC_MESSAGE },
            additionalProperties = mapOf(
                KEY_METRIC_TYPE to VALUE_METRIC_TYPE,
                KEY_BUCKETS_COUNT to buckets.size,
                KEY_GROUPS_COUNT to groupsCount,
                KEY_SPANS_COUNT to spansCount,
                KEY_ERRORS_COUNT to errorsCount,
                KEY_FORCED to forced
            ),
            samplingRate = SAMPLING_RATE
        )
    }

    internal companion object {
        internal const val METRIC_MESSAGE = "[Mobile Metric] Trace Client Stats"

        internal const val KEY_METRIC_TYPE = "metric_type"
        internal const val VALUE_METRIC_TYPE = "trace client stats"

        internal const val KEY_BUCKETS_COUNT = "buckets_count"
        internal const val KEY_GROUPS_COUNT = "groups_count"
        internal const val KEY_SPANS_COUNT = "spans_count"
        internal const val KEY_ERRORS_COUNT = "errors_count"
        internal const val KEY_FORCED = "forced"

        private const val SAMPLING_RATE: Float = 15.0f
    }
}
