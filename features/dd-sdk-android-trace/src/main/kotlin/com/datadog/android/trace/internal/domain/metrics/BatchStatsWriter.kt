/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/**
 * Writes flushed trace client stats to storage, splitting oversized flushes into multiple
 * size-bounded batches so a single flush can't be rejected outright for exceeding the storage
 * item size limit. Splitting matches datadog-agent's own approach: groups are distributed
 * round-robin across a pre-computed number of batches, so no single batch ends up
 * disproportionately large.
 */
internal class BatchStatsWriter(
    private val sdkCore: FeatureSdkCore,
    private val runtimeID: String,
    maxGroupsPerBatch: Int = MAX_GROUPS_PER_BATCH
) : StatsWriter {
    private val maxGroupsPerBatch = maxGroupsPerBatch.coerceAtLeast(1)
    private val sequenceNumber = AtomicLong(0)

    override fun write(statBuckets: List<ClientStatsBucket>, forced: Boolean) {
        if (statBuckets.sumOf { it.stats.size } == 0) return

        sdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)
            ?.withWriteContext { datadogContext, writeScope ->
                val sharedSequenceNumber = sequenceNumber.getAndIncrement()
                val batches = splitIntoBatches(statBuckets, datadogContext, sharedSequenceNumber)
                val isSplit = batches.size > 1
                val wrappedBatches = batches.map { batch ->
                    gzip(StatsPayload(clientStats = listOf(batch), splitPayload = isSplit).toMsgPackPayload())
                }

                writeScope { batchWriter ->
                    val results = wrappedBatches.map { wrappedBytes ->
                        batchWriter.write(
                            event = RawBatchEvent(data = wrappedBytes),
                            batchMetadata = null,
                            eventType = EventType.DEFAULT
                        )
                    }
                    val succeededBuckets = mutableListOf<ClientStatsBucket>()
                    val droppedBuckets = mutableListOf<ClientStatsBucket>()
                    batches.forEachIndexed { index, batch ->
                        val target = if (results[index]) succeededBuckets else droppedBuckets
                        target.addAll(batch.stats)
                    }
                    sendFlushMetric(succeededBuckets, droppedBuckets, forced, isSplit)
                }
            }
    }

    /**
     * Splits [statBuckets] into one or more [ClientStatsPayload] batches, each holding no more
     * than [maxGroupsPerBatch] [ClientGroupedStats] groups in total across its buckets.
     */
    private fun splitIntoBatches(
        statBuckets: List<ClientStatsBucket>,
        datadogContext: DatadogContext,
        sequenceNumber: Long
    ): List<ClientStatsPayload> {
        val totalGroups = statBuckets.sumOf { it.stats.size }
        if (totalGroups <= maxGroupsPerBatch) {
            return listOf(buildBatchPayload(datadogContext, sequenceNumber, statBuckets))
        }

        var batchCount = totalGroups / maxGroupsPerBatch
        if (totalGroups % maxGroupsPerBatch != 0) batchCount++

        // Keyed by (start, duration) so a batch emits at most one ClientStatsBucket per original bucket
        val batchGroupsByBucketKey = Array(batchCount) {
            mutableMapOf<Pair<Long, Long>, MutableList<ClientGroupedStats>>()
        }

        // Round-robin every group across the batches so they stay similar in size
        var entryIndex = 0
        for ((start, duration, stats) in statBuckets) {
            val bucketKey = Pair(start, duration)
            for (group in stats) {
                batchGroupsByBucketKey[entryIndex % batchCount].getOrPut(bucketKey) { mutableListOf() }.add(group)
                entryIndex++
            }
        }

        return batchGroupsByBucketKey.map { groupsByBucketKey ->
            val buckets = groupsByBucketKey.map { (key, groups) -> ClientStatsBucket(key.first, key.second, groups) }
            buildBatchPayload(datadogContext, sequenceNumber, buckets)
        }
    }

    private fun buildBatchPayload(
        datadogContext: DatadogContext,
        sequenceNumber: Long,
        batchBuckets: List<ClientStatsBucket>
    ) = ClientStatsPayload(
        hostname = "", // left blank intentionally
        env = datadogContext.env,
        version = datadogContext.version,
        service = datadogContext.service,
        tracerVersion = datadogContext.sdkVersion,
        runtimeID = runtimeID,
        sequenceNumber = sequenceNumber,
        stats = batchBuckets
    )

    /**
     * Compresses [bytes] with gzip before persisting, since the storage size limits are sized
     * against the compressed payload the intake receives, not the raw msgpack bytes. The upload
     * layer marks the request as already gzip-encoded so it isn't compressed a second time.
     */
    @Suppress("UnsafeThirdPartyFunctionCall") // GZIPOutputStream/ByteArrayOutputStream declare IOException,
    // but an in-memory ByteArrayOutputStream never actually throws on write/close.
    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    /**
     * A split flush can emit multiple [ClientStatsBucket]s for the same (start, duration) — one
     * per batch. Counts the distinct original time buckets rather than the raw list size.
     */
    private fun List<ClientStatsBucket>.distinctBucketKeyCount() = distinctBy { it.start to it.duration }.size

    private class GroupsSpansErrorsCount(val groupsCount: Int, val spansCount: Long, val errorsCount: Long)

    private fun countGroupsSpansErrors(buckets: List<ClientStatsBucket>): GroupsSpansErrorsCount {
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
        return GroupsSpansErrorsCount(groupsCount, spansCount, errorsCount)
    }

    private fun sendFlushMetric(
        succeededBuckets: List<ClientStatsBucket>,
        droppedBuckets: List<ClientStatsBucket>,
        forced: Boolean,
        isSplit: Boolean
    ) {
        val succeeded = countGroupsSpansErrors(succeededBuckets)
        val dropped = countGroupsSpansErrors(droppedBuckets)

        sdkCore.internalLogger.logMetric(
            messageBuilder = { METRIC_MESSAGE },
            additionalProperties = mapOf(
                KEY_METRIC_TYPE to VALUE_METRIC_TYPE,
                KEY_BUCKETS_COUNT to succeededBuckets.distinctBucketKeyCount(),
                KEY_GROUPS_COUNT to succeeded.groupsCount,
                KEY_SPANS_COUNT to succeeded.spansCount,
                KEY_ERRORS_COUNT to succeeded.errorsCount,
                KEY_DROPPED_BUCKETS_COUNT to droppedBuckets.distinctBucketKeyCount(),
                KEY_DROPPED_GROUPS_COUNT to dropped.groupsCount,
                KEY_DROPPED_SPANS_COUNT to dropped.spansCount,
                KEY_DROPPED_ERRORS_COUNT to dropped.errorsCount,
                KEY_FORCED to forced,
                KEY_SPLIT to isSplit
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
        internal const val KEY_DROPPED_BUCKETS_COUNT = "dropped_buckets_count"
        internal const val KEY_DROPPED_GROUPS_COUNT = "dropped_groups_count"
        internal const val KEY_DROPPED_SPANS_COUNT = "dropped_spans_count"
        internal const val KEY_DROPPED_ERRORS_COUNT = "dropped_errors_count"
        internal const val KEY_FORCED = "forced"
        internal const val KEY_SPLIT = "split"

        private const val SAMPLING_RATE: Float = 15.0f

        // Matches datadog-agent's own heuristic (~1.5 MB compressed at an estimated
        // 375 bytes/group, well under ClientStatsFeature.STORAGE_MAX_ITEM_SIZE_BYTES).
        internal const val MAX_GROUPS_PER_BATCH = 4000
    }
}
