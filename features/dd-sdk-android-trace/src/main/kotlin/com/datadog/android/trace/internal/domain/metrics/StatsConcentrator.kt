/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.event.EventMapper
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.internal.ddsketch.DDSketch
import com.datadog.android.trace.internal.domain.event.ContextAwareMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.trace.bootstrap.instrumentation.api.Tags
import com.datadog.trace.core.DDSpan
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class StatsConcentrator(
    private val sdkCore: FeatureSdkCore,
    private val ddSpanToSpanEventMapper: ContextAwareMapper<DDSpan, SpanEvent>,
    private val eventMapper: EventMapper<SpanEvent>,
    /**
     * Executor that owns stat calculations. It must be a single-thread executor to enforce threading contracts.
     */
    private val executorService: ExecutorService,
    private val statsWriter: StatsWriter,
    private val timeProvider: TimeProvider,
    /**
     * The number of stats buckets we keep in memory before flushing them.
     * It means that we can compute stats only for the last `bufferLen * bucketSizeNs` and that we
     * wait such time before flushing the stats.
     * This only applies to past buckets. Stats buckets in the future are allowed with no restriction.
     */
    private val bufferLen: Int = DEFAULT_BUFFER_SIZE,
    private val bucketSizeNs: Long = DEFAULT_BUCKET_LENGTH.inWholeNanoseconds
) {
    // These fields below are confined to executorService; never access them from another thread.
    private var oldestTs: Long = 0L
    private val buckets = mutableMapOf<Long, MutableMap<AggregationKey, GroupedStats>>()

    // Coalescing flags: at most one flush task lives in the executor queue at any time.
    // forcePending is set unconditionally so a force flush is never lost even if a scheduled
    // flush is already queued and will pick it up.
    private val flushPending = AtomicBoolean(false)
    private val forcePending = AtomicBoolean(false)

    fun record(trace: List<DDSpan>) {
        val metricsFeature = sdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME) ?: return
        metricsFeature.withContext { datadogContext ->
            trace.asSequence()
                .filter { shouldComputeMetrics(it) }
                .mapNotNull { span ->
                    val mapped = eventMapper.map(ddSpanToSpanEventMapper.map(datadogContext, span))
                        ?: return@mapNotNull null
                    val spanTags = mapped.meta.additionalProperties
                    val spanKind = spanTags[Tags.SPAN_KIND] ?: ""
                    SpanSnapshot(
                        service = mapped.service,
                        operationName = mapped.name,
                        resource = mapped.resource,
                        type = mapped.type,
                        spanKind = spanKind,
                        httpStatusCode = spanTags[Tags.HTTP_STATUS]?.toIntOrNull() ?: 0,
                        isRoot = span.isRootSpan,
                        peerTags = computePeerTags(spanTags, spanKind),
                        serviceSource = spanTags[KEY_SVC_SRC] ?: "",
                        startTime = span.startTime,
                        durationNs = span.durationNano,
                        isError = mapped.error != 0L,
                        isTopLevel = span.isTopLevel
                    )
                }
                .forEach { snapshot ->
                    executorService.executeSafe("stats-aggregate", sdkCore.internalLogger) { aggregate(snapshot) }
                }
        }
    }

    /**
     * Schedules a drain of ready buckets and returns immediately. If a flush is already queued,
     * this call is a no-op — the queued task will cover the window. A [flushAll] flush is never dropped:
     * [forcePending] is set before the coalescing check so the already-queued task picks it up.
     * Flushed buckets are written to [statsWriter].
     *
     * @param flushAll When `true`, drains all buckets regardless of age. Used during SDK teardown.
     */
    fun scheduleFlush(flushAll: Boolean) {
        val now = timeProvider.getDeviceTimestampMillis().milliseconds.inWholeNanoseconds
        if (flushAll) {
            forcePending.set(true)
        }

        if (!flushPending.compareAndSet(false, true)) {
            return
        }

        executorService.executeSafe("stats-flush", sdkCore.internalLogger) {
            flushPending.set(false)
            val buckets = drainBuckets(now, forcePending.getAndSet(false))
            if (buckets.isNotEmpty()) {
                statsWriter.write(buckets)
            }
        }
    }

    private fun aggregate(s: SpanSnapshot) {
        val bucketKey = alignTimestamp(bucketSizeNs, s.startTime + s.durationNs).coerceAtLeast(oldestTs)
        val key = buildAggregationKey(s)
        buckets.getOrPut(bucketKey) { mutableMapOf() }.getOrPut(key) { GroupedStats() }.add(s)
    }

    private fun drainBuckets(now: Long, flushAll: Boolean): List<ClientStatsBucket> {
        // Determine the current cuff off for which buckets to leave as still in progress
        val cutoff = now - (bufferLen * bucketSizeNs)

        val closedBuckets = buckets.filterKeys { flushAll || it <= cutoff }
        closedBuckets.keys.forEach { buckets.remove(it) }

        // Update the oldest allowed timestamp that older events will fall into
        oldestTs = (alignTimestamp(bucketSizeNs, now) - (bufferLen - 1) * bucketSizeNs).coerceAtLeast(oldestTs)

        return closedBuckets.map { (bucketStart, groups) ->
            ClientStatsBucket(
                start = bucketStart + timeProvider.getServerOffsetNanos(),
                duration = bucketSizeNs,
                stats = groups.map { (key, stats) ->
                    ClientGroupedStats(
                        service = key.service,
                        name = key.operation,
                        resource = key.resource,
                        httpStatusCode = key.httpStatusCode,
                        type = key.type,
                        spanKind = key.spanKind,
                        isTraceRoot = key.isTraceRoot,
                        hits = stats.hits,
                        errors = stats.errors,
                        duration = stats.duration,
                        topLevelHits = stats.topLevelHits,
                        okSummary = stats.okSummary.serialize(),
                        errorSummary = stats.errSummary.serialize(),
                        isSynthetic = key.synthetics,
                        peerTags = key.peerTags,
                        serviceSource = key.serviceSource
                    )
                }
            )
        }
    }

    private fun shouldComputeMetrics(span: DDSpan): Boolean {
        return (
            span.isTopLevel ||
                span.isMeasured ||
                span.getTag(DatadogTracingConstants.Tags.KEY_SPAN_KIND) in ELIGIBLE_SPAN_KINDS
            ) &&
            span.longRunningVersion <= 0 && // either not long-running or unpublished long-running span
            span.durationNano > 0
    }

    private fun buildAggregationKey(s: SpanSnapshot) = AggregationKey(
        service = s.service,
        operation = s.operationName,
        resource = s.resource,
        httpStatusCode = s.httpStatusCode,
        type = s.type,
        spanKind = s.spanKind,
        isTraceRoot = if (s.isRoot) Trilean.TRUE else Trilean.FALSE,
        synthetics = false,
        peerTags = s.peerTags,
        serviceSource = s.serviceSource
    )

    private fun computePeerTags(spanTags: Map<String, String>, spanKind: String): List<String> {
        if (spanKind !in PEER_TAG_SPAN_KINDS) {
            return emptyList()
        }

        return PEER_TAG_KEYS
            .mapNotNull { key -> spanTags[key]?.let { value -> "$key:$value" } }
            .sorted()
    }

    private data class AggregationKey(
        val service: String,
        val operation: String,
        val resource: String,
        val httpStatusCode: Int,
        val type: String,
        val spanKind: String,
        val isTraceRoot: Trilean,
        val synthetics: Boolean,
        val peerTags: List<String>,
        val serviceSource: String
    )

    /**
     * Lightweight, immutable snapshot of all span data needed for stats computation.
     * Built in [record] from the post-[eventMapper] [SpanEvent] and the raw [DDSpan], so that
     * [aggregate] and [drainBuckets] never need to touch a [SpanEvent] or parse a tag map.
     * Mirrors `SpanSnapshot` from dd-sdk-ios `feature/client-side-stats`.
     */
    private data class SpanSnapshot(
        val service: String,
        val operationName: String,
        val resource: String,
        val type: String,
        val spanKind: String,
        val httpStatusCode: Int,
        val isRoot: Boolean,
        val peerTags: List<String>,
        val serviceSource: String,
        val startTime: Long,
        val durationNs: Long,
        val isError: Boolean,
        val isTopLevel: Boolean
    )

    /**
     * Mutable per-group accumulator, confined to [executorService].
     * Not thread-safe by design — DDSketch is also not thread-safe.
     */
    private class GroupedStats {
        var hits = 0L
        var errors = 0L
        var topLevelHits = 0L
        var duration = 0L
        val okSummary = DDSketch(RELATIVE_ACCURACY, MAX_NUM_BINS)
        val errSummary = DDSketch(RELATIVE_ACCURACY, MAX_NUM_BINS)

        fun add(s: SpanSnapshot) {
            hits++
            duration += s.durationNs

            if (s.isTopLevel) topLevelHits++
            if (s.isError) errors++

            val targetSummary = if (s.isError) errSummary else okSummary
            targetSummary.add(s.durationNs.toDouble())
        }
    }

    internal companion object {
        private const val RELATIVE_ACCURACY = 0.01
        private const val MAX_NUM_BINS = 2048
        private const val KEY_SVC_SRC = "_dd.svc_src"
        private const val DEFAULT_BUFFER_SIZE = 2
        private val DEFAULT_BUCKET_LENGTH = 10.seconds

        private val ELIGIBLE_SPAN_KINDS = setOf(
            Tags.SPAN_KIND_SERVER,
            Tags.SPAN_KIND_CONSUMER,
            Tags.SPAN_KIND_CLIENT,
            Tags.SPAN_KIND_PRODUCER
        )

        private val PEER_TAG_SPAN_KINDS = setOf(
            Tags.SPAN_KIND_CONSUMER,
            Tags.SPAN_KIND_CLIENT,
            Tags.SPAN_KIND_PRODUCER
        )

        private val PEER_TAG_KEYS = setOf(
            Tags.PEER_SERVICE,
            "out.host",
            "server.address",
            "network.destination.name",
            Tags.PEER_HOSTNAME
        )

        @VisibleForTesting
        fun alignTimestamp(bucketSizeNs: Long, timestamp: Long): Long {
            return timestamp - (timestamp % bucketSizeNs)
        }
    }
}
