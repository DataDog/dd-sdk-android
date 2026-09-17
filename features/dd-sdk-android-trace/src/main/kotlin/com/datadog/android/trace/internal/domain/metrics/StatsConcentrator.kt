/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.getSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.event.EventMapper
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.internal.ddsketch.DDSketch
import com.datadog.android.trace.internal.domain.event.ContextAwareMapper
import com.datadog.android.trace.model.SpanEvent
import com.datadog.trace.bootstrap.instrumentation.api.Tags
import com.datadog.trace.core.DDSpan
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
internal class StatsConcentrator(
    private val sdkCore: FeatureSdkCore,
    private val ddSpanToSpanEventMapper: ContextAwareMapper<DDSpan, SpanEvent>,
    private val eventMapper: EventMapper<SpanEvent>,
    /**
     * Executor that owns stat calculations. It must be a single-thread executor to enforce threading contracts.
     */
    private val executorService: ScheduledExecutorService,
    private val statsWriter: StatsWriter,
    private val timeProvider: TimeProvider,
    initialConsent: TrackingConsent,
    /**
     * The number of stats buckets we keep in memory before flushing them.
     * It means that we can compute stats only for the last `bufferLen * bucketSizeNs` and that we
     * wait such time before flushing the stats.
     * This only applies to past buckets. Stats buckets in the future are allowed with no restriction.
     */
    private val bufferLen: Int = DEFAULT_BUFFER_SIZE,
    private val bucketSizeNs: Long = DEFAULT_BUCKET_LENGTH.inWholeNanoseconds,
    startPeriodicFlush: Boolean = true
) {
    @Volatile
    private var currentConsent: TrackingConsent = initialConsent

    // These fields below are confined to executorService; never access them from another thread.
    private var oldestTs: Long = 0L
    private val buckets = mutableMapOf<Long, MutableMap<AggregationKey, GroupedStats>>()

    @Volatile
    private var isStopped = false

    init {
        if (startPeriodicFlush) {
            schedulePeriodicFlush()
        }
    }

    fun record(trace: List<DDSpan>) {
        // Possible rare race when consent switches from NOT_GRANTED to PENDING/GRANTED or vice versa but
        // the rare and small data loss is worth it for the performance gain
        if (currentConsent == TrackingConsent.NOT_GRANTED || isStopped) {
            return
        }

        val metricsFeature = sdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME) ?: return
        metricsFeature.withContext { datadogContext ->
            val applicableSpans = trace.asSequence()
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
                .toList()

            executorService.executeSafe("stats-aggregate", sdkCore.internalLogger) {
                if (currentConsent == TrackingConsent.NOT_GRANTED) {
                    return@executeSafe
                }

                aggregate(applicableSpans)
            }
        }
    }

    fun stop() {
        isStopped = true
        scheduleFlush(flushAll = true)
    }

    /**
     * Synchronously drains any pending tasks queued on [executorService], shuts the executor down,
     * awaits its termination, runs the drained tasks on the calling thread, and finally performs a
     * synchronous flush-all of the in-memory buckets so [statsWriter] is invoked while the shared
     * core write executor is still guaranteed to be alive.
     *
     * Unlike [stop], which only schedules a flush asynchronously on [executorService] (and is
     * therefore unsafe to use right before that executor — or the shared core write executor — gets
     * shut down), this method blocks the calling thread until the flush has actually completed.
     */
    fun drainAndFlush() {
        isStopped = true

        (executorService as? ScheduledThreadPoolExecutor)?.let {
            // Don't let not-yet-due delayed tasks (e.g. the periodic flush) run after shutdown.
            it.executeExistingDelayedTasksAfterShutdownPolicy = false
        }
        // Submit the final flush ON the executor thread so it queues after any in-flight
        // aggregation tasks and runs on the thread that owns buckets/oldestTs.
        executorService
            .submitSafe("stats-drain-flush", sdkCore.internalLogger) { flushBuckets(flushAll = true) }
            ?.getSafe("stats-drain-flush", DRAIN_WAIT_SECONDS, TimeUnit.SECONDS, sdkCore.internalLogger)
        executorService.shutdown()
        try {
            executorService.awaitTermination(DRAIN_WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            try {
                // Restore the interrupted status
                Thread.currentThread().interrupt()
            } catch (se: SecurityException) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "Thread was unable to set its own interrupted state" },
                    se
                )
            }
        }
    }

    private fun schedulePeriodicFlush() {
        if (isStopped) {
            return
        }

        executorService.scheduleSafe(
            "stats-flush-periodic",
            FLUSH_INTERVAL_SECS,
            TimeUnit.SECONDS,
            sdkCore.internalLogger
        ) {
            flushBuckets(flushAll = false)

            // Schedule next refresh
            schedulePeriodicFlush()
        }
    }

    /**
     * Schedules a drain of ready buckets and returns immediately.
     * Flushed buckets are written to [statsWriter].
     *
     * @param flushAll When `true`, drains all buckets regardless of age. Used during SDK teardown.
     */
    @VisibleForTesting
    fun scheduleFlush(flushAll: Boolean) {
        executorService.executeSafe("stats-flush-all=$flushAll", sdkCore.internalLogger) {
            flushBuckets(flushAll)
        }
    }

    private fun flushBuckets(flushAll: Boolean) {
        val buckets = drainBuckets(flushAll)
        if (buckets.isNotEmpty()) {
            statsWriter.write(buckets, forced = flushAll)
        }
    }

    private fun aggregate(spans: List<SpanSnapshot>) {
        for (span in spans) {
            val spanEndTime = span.startTime + span.durationNs
            val bucketKey = alignTimestamp(bucketSizeNs, spanEndTime).coerceAtLeast(oldestTs)
            val aggregationKey = buildAggregationKey(span)
            buckets.getOrPut(bucketKey) { mutableMapOf() }
                .getOrPut(aggregationKey) { GroupedStats() }.add(span)
        }
    }

    private fun drainBuckets(flushAll: Boolean): List<ClientStatsBucket> {
        val now = timeProvider.getDeviceTimestampMillis().milliseconds.inWholeNanoseconds

        // Determine the current cuff off for which buckets to leave as still in progress
        val cutoff = now - (bufferLen * bucketSizeNs)

        val closedBuckets = buckets.filterKeys { flushAll || it <= cutoff }
        closedBuckets.keys.forEach { buckets.remove(it) }

        // Update the oldest allowed timestamp that older events will fall into
        oldestTs = (alignTimestamp(bucketSizeNs, now) - (bufferLen - 1) * bucketSizeNs).coerceAtLeast(oldestTs)

        return if (currentConsent == TrackingConsent.NOT_GRANTED) {
            emptyList()
        } else {
            closedBuckets.map { (bucketStart, groups) ->
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

    fun onConsentUpdated(newConsent: TrackingConsent) {
        executorService.executeSafe("stats-consent-change", sdkCore.internalLogger) {
            currentConsent = newConsent
            // Only flush when revoking consent. PENDING <-> GRANTED transitions are both recording
            // states and need no boundary flush. Consent is set first so drainBuckets discards
            // the in-memory buffer rather than forwarding it to the writer.
            if (newConsent == TrackingConsent.NOT_GRANTED) {
                flushBuckets(flushAll = true)
            }
        }
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
        private const val FLUSH_INTERVAL_SECS = 30L
        private const val DRAIN_WAIT_SECONDS = 10L

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
