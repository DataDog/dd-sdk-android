/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.threads.FakeSameThreadExecutorService
import com.datadog.android.event.EventMapper
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.internal.domain.event.ContextAwareMapper
import com.datadog.android.trace.internal.domain.metrics.StatsConcentrator.Companion.alignTimestamp
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.bootstrap.instrumentation.api.Tags
import com.datadog.trace.core.DDSpan
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class StatsConcentratorTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockStatsFeatureScope: FeatureScope

    @Mock
    lateinit var mockSpanEventMapper: ContextAwareMapper<DDSpan, SpanEvent>

    @Mock
    lateinit var mockEventMapper: EventMapper<SpanEvent>

    @Mock
    lateinit var mockStatsWriter: StatsWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private val executorService: ExecutorService = FakeSameThreadExecutorService()
    private lateinit var testedConcentrator: StatsConcentrator

    // Fixed parameters for deterministic timestamp math
    private val fakeBucketSizeNs = 10_000_000_000L // 10 s in ns
    private val fakeBufferLen = 2

    @BeforeEach
    fun setUp() {
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn mockStatsFeatureScope
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockStatsFeatureScope.withContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1).invoke(fakeDatadogContext)
        }
        whenever(mockEventMapper.map(any())) doAnswer { it.getArgument(0) }

        testedConcentrator = StatsConcentrator(
            sdkCore = mockSdkCore,
            ddSpanToSpanEventMapper = mockSpanEventMapper,
            eventMapper = mockEventMapper,
            bufferLen = fakeBufferLen,
            bucketSizeNs = fakeBucketSizeNs,
            executorService = executorService,
            statsWriter = mockStatsWriter
        )
    }

    @AfterEach
    fun tearDown() {
        executorService.shutdown()
    }

    // region Span eligibility

    @Test
    fun `M aggregate span W record() { isTopLevel = true }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan()

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { isMeasured = true }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, isMeasured = true)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = server }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_SERVER)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = client }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_CLIENT)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = consumer }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_CONSUMER)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = producer }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_PRODUCER)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M skip span W record() { long-running span }`(
        @IntForgery(min = 1) fakeLongRunningVersion: Int,
        forge: Forge
    ) {
        // Given
        val (span) = forge.makeEligibleSpan(longRunningVersion = fakeLongRunningVersion)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M skip span W record() { zero duration }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(durationNano = 0L)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M skip span W record() { eventMapper returns null }`(forge: Forge) {
        // Given
        val (span, spanEvent) = forge.makeEligibleSpan()
        whenever(mockEventMapper.map(spanEvent)).thenReturn(null)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M skip all spans W record() { feature not registered }`(forge: Forge) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn null
        val (span) = forge.makeEligibleSpan()

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
        verifyNoInteractions(mockSpanEventMapper)
    }

    // endregion

    // region Bucketing

    @Test
    fun `M bucket span by aligned device end time W scheduleFlush()`(forge: Forge) {
        // Given: deviceEnd = 15s + 3s = 18s; align(18s, 10s bucket) = 10s  (18 - 18%10 = 10)
        val fakeStartTime = 15 * (fakeBucketSizeNs / 10) // 15s
        val fakeDuration = 3 * (fakeBucketSizeNs / 10) // 3s
        val expectedBucketStart = alignTimestamp(fakeBucketSizeNs, fakeStartTime + fakeDuration) // 10s
        val (span) = forge.makeEligibleSpan(startTime = fakeStartTime, durationNano = fakeDuration)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(
            now = expectedBucketStart + fakeBufferLen.toLong() * fakeBucketSizeNs,
            flushAll = false
        )

        // Then
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(expectedBucketStart)
        assertThat(buckets[0].duration).isEqualTo(fakeBucketSizeNs)
    }

    @Test
    fun `M not flush recent bucket W scheduleFlush() { now before cutoff }`(forge: Forge) {
        // Given: bucketKey = align(6*B) = 6*B; cutoff = 7.5*B - 2*B = 5.5*B → bucket 6 > 5.5 → NOT flushed
        val fakeStartTime = 5 * fakeBucketSizeNs
        val fakeDuration = fakeBucketSizeNs // deviceEnd = 6*B → align = 6*B
        val (span) = forge.makeEligibleSpan(startTime = fakeStartTime, durationNano = fakeDuration)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(
            now = 7 * fakeBucketSizeNs + fakeBucketSizeNs / 2,
            flushAll = false
        )

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M flush aged bucket W scheduleFlush() { now at cutoff boundary }`(forge: Forge) {
        // Given: bucketKey = 6*B; now = 8*B → cutoff = 6*B → 6 <= 6 → flushed
        val fakeStartTime = 5 * fakeBucketSizeNs
        val fakeDuration = fakeBucketSizeNs // deviceEnd = 6*B → align = 6*B
        val (span) = forge.makeEligibleSpan(startTime = fakeStartTime, durationNano = fakeDuration)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(
            now = (fakeBufferLen + 6).toLong() * fakeBucketSizeNs,
            flushAll = false
        )

        // Then
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(6 * fakeBucketSizeNs)
    }

    @Test
    fun `M flush all buckets W scheduleFlush() { flushAll = true }`(forge: Forge) {
        // Given: recent bucket that would not normally be aged out yet
        val fakeStartTime = 50 * fakeBucketSizeNs
        val (span) = forge.makeEligibleSpan(startTime = fakeStartTime)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = fakeStartTime, flushAll = true)

        // Then
        assertThat(captureBuckets()).hasSize(1)
    }

    @Test
    fun `M clamp late span to oldest live bucket W scheduleFlush() { oldestTs advanced }`(forge: Forge) {
        // Given: after scheduleFlush(now = nBuckets * B), oldestTs = (nBuckets - (bufferLen-1)) * B
        // After now = 30*B flush: oldestTs = (30 - 1) * B = 29 * B
        val nBuckets = 30L
        val expectedOldestTs = (nBuckets - (fakeBufferLen - 1)) * fakeBucketSizeNs // 29 * B
        testedConcentrator.scheduleFlush(now = 10 * fakeBucketSizeNs, flushAll = false)
        testedConcentrator.scheduleFlush(now = nBuckets * fakeBucketSizeNs, flushAll = false)

        // Late span: startTime=1s, duration=1s → deviceEnd=2s → align(2s,10s)=0 < oldestTs → clamps to 29*B
        // subBucketNs = B/10 = 1s; non-zero so the span is not filtered out by the duration check
        val subBucketNs = fakeBucketSizeNs / 10
        val (lateSpan) = forge.makeEligibleSpan(startTime = subBucketNs, durationNano = subBucketNs)

        // When
        testedConcentrator.record(listOf(lateSpan))
        testedConcentrator.scheduleFlush(now = 50 * fakeBucketSizeNs, flushAll = true)

        // Then
        val buckets = captureBuckets()
        assertThat(buckets).isNotEmpty()
        assertThat(buckets[0].start).isEqualTo(expectedOldestTs)
    }

    // endregion

    // region Aggregation

    @Test
    fun `M sum hits and duration W record() { multiple spans with same aggregation key }`(
        @IntForgery(min = 2, max = 10) fakeSpanCount: Int,
        @StringForgery fakeService: String,
        @StringForgery fakeOperation: String,
        @StringForgery fakeResource: String,
        forge: Forge
    ) {
        // Given
        val spans = (1..fakeSpanCount).map {
            forge.makeEligibleSpan(service = fakeService, operation = fakeOperation, resource = fakeResource).first
        }

        // When
        testedConcentrator.record(spans)
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        val group = firstGroup()
        assertThat(group.hits).isEqualTo(fakeSpanCount.toLong())
        assertThat(group.duration).isEqualTo(fakeSpanCount.toLong() * fakeBucketSizeNs)
    }

    @Test
    fun `M count topLevelHits separately W record() { mixed top-level and non-top-level spans }`(
        @StringForgery fakeService: String,
        @StringForgery fakeOperation: String,
        @StringForgery fakeResource: String,
        forge: Forge
    ) {
        // Given
        val (topLevelSpan) = forge.makeEligibleSpan(
            service = fakeService,
            operation = fakeOperation,
            resource = fakeResource
        )
        val (nonTopLevelSpan) = forge.makeEligibleSpan(
            isTopLevel = false,
            isMeasured = true,
            service = fakeService,
            operation = fakeOperation,
            resource = fakeResource
        )

        // When
        testedConcentrator.record(listOf(topLevelSpan, nonTopLevelSpan))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        val group = firstGroup()
        assertThat(group.hits).isEqualTo(2L)
        assertThat(group.topLevelHits).isEqualTo(1L)
    }

    @Test
    fun `M count errors W record() { error and non-error spans same key }`(
        @StringForgery fakeService: String,
        @StringForgery fakeOperation: String,
        @StringForgery fakeResource: String,
        forge: Forge
    ) {
        // Given
        val (okSpan) = forge.makeEligibleSpan(
            service = fakeService,
            operation = fakeOperation,
            resource = fakeResource
        )
        val (errorSpan) = forge.makeEligibleSpan(
            service = fakeService,
            operation = fakeOperation,
            resource = fakeResource,
            error = 1L
        )

        // When
        testedConcentrator.record(listOf(okSpan, errorSpan))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        val group = firstGroup()
        assertThat(group.hits).isEqualTo(2L)
        assertThat(group.errors).isEqualTo(1L)
        assertThat(group.okSummary).isNotEmpty()
        assertThat(group.errorSummary).isNotEmpty()
    }

    @Test
    fun `M create separate groups W record() { spans with different services }`(
        @StringForgery fakeService1: String,
        @StringForgery fakeService2: String,
        @StringForgery fakeResource: String,
        forge: Forge
    ) {
        // Given
        val (span1) = forge.makeEligibleSpan(service = fakeService1, resource = fakeResource)
        val (span2) = forge.makeEligibleSpan(service = fakeService2, resource = fakeResource)

        // When
        testedConcentrator.record(listOf(span1, span2))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        val groups = captureBuckets()[0].stats
        assertThat(groups).hasSize(2)
        assertThat(groups.map { it.service }).containsExactlyInAnyOrder(fakeService1, fakeService2)
    }

    @Test
    fun `M create separate groups W record() { spans with different resources }`(
        @StringForgery fakeService: String,
        @StringForgery fakeResource1: String,
        @StringForgery fakeResource2: String,
        forge: Forge
    ) {
        // Given
        val (span1) = forge.makeEligibleSpan(service = fakeService, resource = fakeResource1)
        val (span2) = forge.makeEligibleSpan(service = fakeService, resource = fakeResource2)

        // When
        testedConcentrator.record(listOf(span1, span2))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        val groups = captureBuckets()[0].stats
        assertThat(groups).hasSize(2)
        assertThat(groups.map { it.resource }).containsExactlyInAnyOrder(fakeResource1, fakeResource2)
    }

    @Test
    fun `M reflect peer tags W record() { client span with peer service tag }`(
        @StringForgery fakePeerService: String,
        forge: Forge
    ) {
        // Given
        val (span) = forge.makeEligibleSpan(
            isTopLevel = false,
            spanKind = Tags.SPAN_KIND_CLIENT,
            peerService = fakePeerService
        )

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().peerTags).isEqualTo(listOf("${Tags.PEER_SERVICE}:$fakePeerService"))
    }

    @Test
    fun `M mark isTraceRoot = TRUE W record() { root span }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isRootSpan = true)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().isTraceRoot).isEqualTo(Trilean.TRUE)
    }

    @Test
    fun `M reflect eventMapper resource rewrite W record()`(
        @StringForgery fakeRewrittenResource: String,
        forge: Forge
    ) {
        // Given
        val (span, originalSpanEvent) = forge.makeEligibleSpan()
        val rewrittenSpanEvent = originalSpanEvent.copy(resource = fakeRewrittenResource)
        whenever(mockEventMapper.map(originalSpanEvent)) doReturn rewrittenSpanEvent

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().resource).isEqualTo(fakeRewrittenResource)
    }

    @Test
    fun `M accumulate duration W record() { span with random duration }`(
        @LongForgery(min = 1L, max = 100L) fakeMultiplier: Long,
        forge: Forge
    ) {
        // Given
        val fakeDuration = fakeMultiplier * (fakeBucketSizeNs / 100)
        val (span) = forge.makeEligibleSpan(durationNano = fakeDuration)

        // When
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(now = farFuture(), flushAll = true)

        // Then
        assertThat(firstGroup().duration).isEqualTo(fakeDuration)
    }

    // endregion

    // region Flush behavior

    @Test
    fun `M not call statsWriter W scheduleFlush() { nothing aged out }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan()
        val bucketStart = alignTimestamp(fakeBucketSizeNs, span.startTime + span.durationNano)

        // When: flush with now before the bucket cutoff — bucket is too recent
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(
            now = bucketStart + (fakeBufferLen - 1).toLong() * fakeBucketSizeNs,
            flushAll = false
        )

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M not flush same bucket twice W scheduleFlush() { called twice for same window }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(startTime = 5 * fakeBucketSizeNs)
        testedConcentrator.record(listOf(span))

        val flushNow = (fakeBufferLen + 6).toLong() * fakeBucketSizeNs
        testedConcentrator.scheduleFlush(now = flushNow, flushAll = false)

        // When: second flush after the first has already drained the bucket
        testedConcentrator.scheduleFlush(now = flushNow, flushAll = false)

        // Then: write called exactly once with the span's bucket — second flush found empty buckets
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(6 * fakeBucketSizeNs)
        assertThat(buckets[0].stats[0].hits).isEqualTo(1L)
    }

    // endregion

    // region Flush conflation

    @Test
    fun `M submit only one flush task W scheduleFlush() { called twice while flush pending }`(forge: Forge) {
        // Uses a real single-thread executor so tasks actually queue so we can test conflation
        val realExecutor = Executors.newSingleThreadExecutor()
        val concentrator = makeConcentrator(realExecutor)
        try {
            // Given
            val (span) = forge.makeEligibleSpan()
            concentrator.record(listOf(span))

            // When: block the executor, then call scheduleFlush twice
            val blocker = CountDownLatch(1)
            realExecutor.submit { blocker.await() }
            // First call: CAS false→true succeeds, queues flush task (blocked behind latch).
            concentrator.scheduleFlush(now = farFuture(), flushAll = false)
            // Second call: CAS fails (flushPending already true) → coalesced, no second task queued.
            concentrator.scheduleFlush(now = farFuture(), flushAll = false)

            // Unblock the queue and drain all requests
            blocker.countDown()
            realExecutor.submit {}.get(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            realExecutor.shutdown()
        }

        // Then: only one flush task ran → write called exactly once with the span's bucket
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(6 * fakeBucketSizeNs)
        assertThat(buckets[0].stats[0].hits).isEqualTo(1L)
    }

    @Test
    fun `M preserve flushAll when coalesced W scheduleFlush() { flushAll=true merged into pending flush }`(
        forge: Forge
    ) {
        // Uses a real single-thread executor so tasks actually queue; same reasoning as above.
        val realExecutor = Executors.newSingleThreadExecutor()
        val concentrator = makeConcentrator(realExecutor)
        try {
            // Given: span in a very recent bucket that a non-force flush won't drain
            val recentBucketStart = 50 * fakeBucketSizeNs
            val (span) = forge.makeEligibleSpan(startTime = recentBucketStart)
            concentrator.record(listOf(span))

            // When: block the executor, queue a normal flush then a force flush
            val blocker = CountDownLatch(1)
            realExecutor.submit { blocker.await() }
            // First call: queues flush task that would NOT drain the recent bucket on its own.
            concentrator.scheduleFlush(now = recentBucketStart, flushAll = false)
            // Second call: coalesced (CAS fails), but sets forcePending=true so the queued task picks it up.
            concentrator.scheduleFlush(now = recentBucketStart, flushAll = true)

            // Unblock the queue and drain all requests
            blocker.countDown()
            realExecutor.submit {}.get(FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } finally {
            realExecutor.shutdown()
        }

        // Then: the queued task picked up forcePending=true and flushed the recent bucket anyway
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(51 * fakeBucketSizeNs)
        assertThat(buckets[0].stats[0].hits).isEqualTo(1L)
    }

    // endregion

    // region alignTimestamp

    @Test
    fun `M align timestamp to bucket boundary W alignTimestamp() { timestamp exactly on boundary }`(
        @LongForgery(min = 1L, max = 1000L) fakeBucketIndex: Long
    ) {
        // Given
        val fakeTimestamp = fakeBucketIndex * fakeBucketSizeNs

        // When
        val result = alignTimestamp(fakeBucketSizeNs, fakeTimestamp)

        // Then
        assertThat(result).isEqualTo(fakeTimestamp)
    }

    @Test
    fun `M align timestamp to bucket boundary W alignTimestamp() { timestamp inside bucket }`(
        @LongForgery(min = 1L, max = 1000L) fakeBucketIndex: Long,
        @LongForgery(min = 1L) fakeOffset: Long
    ) {
        // Given
        val fakeOffsetWithinBucket = fakeOffset % (fakeBucketSizeNs - 1) + 1
        val fakeTimestamp = fakeBucketIndex * fakeBucketSizeNs + fakeOffsetWithinBucket

        // When
        val result = alignTimestamp(fakeBucketSizeNs, fakeTimestamp)

        // Then
        assertThat(result).isEqualTo(fakeBucketIndex * fakeBucketSizeNs)
    }

    // endregion

    // region Helpers

    /** Creates a matched (DDSpan, SpanEvent) pair and wires the mapper stub. */
    private fun Forge.makeEligibleSpan(
        isTopLevel: Boolean = true,
        isMeasured: Boolean = false,
        isRootSpan: Boolean = false,
        spanKind: String = "",
        longRunningVersion: Int = 0,
        startTime: Long = 5 * fakeBucketSizeNs,
        durationNano: Long = fakeBucketSizeNs,
        service: String = anAlphabeticalString(),
        operation: String = anAlphabeticalString(),
        resource: String = anAlphabeticalString(),
        error: Long = 0L,
        peerService: String? = null
    ): Pair<DDSpan, SpanEvent> {
        val span = makeSpan(
            startTime = startTime,
            durationNano = durationNano,
            isTopLevel = isTopLevel,
            isMeasured = isMeasured,
            isRootSpan = isRootSpan,
            spanKindTag = spanKind,
            longRunningVersion = longRunningVersion
        )
        val spanEvent = makeSpanEvent(
            service = service,
            operation = operation,
            resource = resource,
            error = error,
            spanKind = spanKind,
            peerService = peerService
        )
        whenever(mockSpanEventMapper.map(fakeDatadogContext, span)) doReturn spanEvent
        return span to spanEvent
    }

    private fun makeSpan(
        startTime: Long,
        durationNano: Long,
        isTopLevel: Boolean = true,
        isMeasured: Boolean = false,
        isRootSpan: Boolean = false,
        spanKindTag: String = "",
        longRunningVersion: Int = 0
    ): DDSpan = mock {
        on { this.startTime } doReturn startTime
        on { this.durationNano } doReturn durationNano
        on { this.isTopLevel } doReturn isTopLevel
        on { this.isMeasured } doReturn isMeasured
        on { this.isRootSpan } doReturn isRootSpan
        on { this.longRunningVersion } doReturn longRunningVersion
        on { getTag(DatadogTracingConstants.Tags.KEY_SPAN_KIND) } doReturn spanKindTag
    }

    private fun Forge.makeSpanEvent(
        service: String = anAlphabeticalString(),
        operation: String = anAlphabeticalString(),
        resource: String = anAlphabeticalString(),
        error: Long = 0L,
        spanKind: String = "",
        peerService: String? = null
    ): SpanEvent {
        val base = getForgery<SpanEvent>()
        val extraTags = buildMap {
            if (spanKind.isNotEmpty()) put(Tags.SPAN_KIND, spanKind)
            if (peerService != null) put(Tags.PEER_SERVICE, peerService)
        }
        return base.copy(
            service = service,
            name = operation,
            resource = resource,
            error = error,
            meta = base.meta.copy(additionalProperties = extraTags)
        )
    }

    /** `now` value that puts any reasonable span bucket well past the flush cutoff. */
    private fun farFuture(): Long = 100 * fakeBucketSizeNs

    private fun makeConcentrator(executor: ExecutorService) = StatsConcentrator(
        sdkCore = mockSdkCore,
        ddSpanToSpanEventMapper = mockSpanEventMapper,
        eventMapper = mockEventMapper,
        bufferLen = fakeBufferLen,
        bucketSizeNs = fakeBucketSizeNs,
        executorService = executor,
        statsWriter = mockStatsWriter
    )

    private fun captureBuckets(): List<ClientStatsBucket> {
        val captor = argumentCaptor<List<ClientStatsBucket>>()
        verify(mockStatsWriter).write(captor.capture())
        return captor.firstValue
    }

    private fun firstGroup(): ClientGroupedStats = captureBuckets()[0].stats[0]

    // endregion

    private companion object {
        private const val FLUSH_TIMEOUT_MS = 5_000L
    }
}
