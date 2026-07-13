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
import com.datadog.android.event.EventMapper
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.privacy.TrackingConsent
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockExecutorService: ScheduledExecutorService

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

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
        whenever(mockExecutorService.execute(any())) doAnswer { it.getArgument<Runnable>(0).run() }

        testedConcentrator = StatsConcentrator(
            sdkCore = mockSdkCore,
            ddSpanToSpanEventMapper = mockSpanEventMapper,
            eventMapper = mockEventMapper,
            bufferLen = fakeBufferLen,
            bucketSizeNs = fakeBucketSizeNs,
            executorService = mockExecutorService,
            statsWriter = mockStatsWriter,
            timeProvider = mockTimeProvider,
            initialConsent = TrackingConsent.GRANTED,
            startPeriodicFlush = false
        )
    }

    // region Periodic flush scheduling

    @Test
    fun `M schedule periodic flush on executor W init() { startPeriodicFlush = true }`() {
        // When
        StatsConcentrator(
            sdkCore = mockSdkCore,
            ddSpanToSpanEventMapper = mockSpanEventMapper,
            eventMapper = mockEventMapper,
            bufferLen = fakeBufferLen,
            bucketSizeNs = fakeBucketSizeNs,
            executorService = mockExecutorService,
            statsWriter = mockStatsWriter,
            timeProvider = mockTimeProvider,
            initialConsent = TrackingConsent.GRANTED,
            startPeriodicFlush = true
        )

        // Then
        verify(mockExecutorService).schedule(any<Runnable>(), eq(30L), eq(TimeUnit.SECONDS))
    }

    // endregion

    // region Span eligibility

    @Test
    fun `M aggregate span W record() { isTopLevel = true }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan()

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { isMeasured = true }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, isMeasured = true)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = server }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_SERVER)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = client }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_CLIENT)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = consumer }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_CONSUMER)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(firstGroup().hits).isEqualTo(1L)
    }

    @Test
    fun `M aggregate span W record() { eligible span kind = producer }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isTopLevel = false, spanKind = Tags.SPAN_KIND_PRODUCER)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M skip span W record() { zero duration }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(durationNano = 0L)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(expectedBucketStart + fakeBufferLen.toLong() * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = false)

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
        stubNow(7 * fakeBucketSizeNs + fakeBucketSizeNs / 2)
        testedConcentrator.scheduleFlush(flushAll = false)

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
        stubNow((fakeBufferLen + 6).toLong() * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = false)

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
        stubNow(fakeStartTime)
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(captureBuckets()).hasSize(1)
    }

    @Test
    fun `M clamp late span to oldest live bucket W scheduleFlush() { oldestTs advanced }`(forge: Forge) {
        // Given: after scheduleFlush(now = nBuckets * B), oldestTs = (nBuckets - (bufferLen-1)) * B
        // After now = 30*B flush: oldestTs = (30 - 1) * B = 29 * B
        val nBuckets = 30L
        val expectedOldestTs = (nBuckets - (fakeBufferLen - 1)) * fakeBucketSizeNs // 29 * B
        stubNow(10 * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = false)
        stubNow(nBuckets * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = false)

        // Late span: startTime=1s, duration=1s → deviceEnd=2s → align(2s,10s)=0 < oldestTs → clamps to 29*B
        // subBucketNs = B/10 = 1s; non-zero so the span is not filtered out by the duration check
        val subBucketNs = fakeBucketSizeNs / 10
        val (lateSpan) = forge.makeEligibleSpan(startTime = subBucketNs, durationNano = subBucketNs)

        // When
        testedConcentrator.record(listOf(lateSpan))
        stubNow(50 * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        val buckets = captureBuckets()
        assertThat(buckets).isNotEmpty()
        assertThat(buckets[0].start).isEqualTo(expectedOldestTs)
    }

    @Test
    fun `M apply server time offset to bucket start W scheduleFlush()`(
        @LongForgery fakeServerOffsetNanos: Long,
        forge: Forge
    ) {
        // Given
        val fakeStartTime = 5 * fakeBucketSizeNs
        val (span) = forge.makeEligibleSpan(startTime = fakeStartTime)
        val expectedBucketStart = alignTimestamp(fakeBucketSizeNs, fakeStartTime + fakeBucketSizeNs)
        whenever(mockTimeProvider.getServerOffsetNanos()) doReturn fakeServerOffsetNanos

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(expectedBucketStart + fakeServerOffsetNanos)
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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(firstGroup().peerTags).isEqualTo(listOf("${Tags.PEER_SERVICE}:$fakePeerService"))
    }

    @Test
    fun `M mark isTraceRoot = TRUE W record() { root span }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(isRootSpan = true)

        // When
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(farFuture())
        testedConcentrator.scheduleFlush(flushAll = true)

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
        stubNow(bucketStart + (fakeBufferLen - 1).toLong() * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = false)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M not flush same bucket twice W scheduleFlush() { called twice for same window }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan(startTime = 5 * fakeBucketSizeNs)
        testedConcentrator.record(listOf(span))

        stubNow((fakeBufferLen + 6).toLong() * fakeBucketSizeNs)
        testedConcentrator.scheduleFlush(flushAll = false)

        // When: second flush after the first has already drained the bucket
        testedConcentrator.scheduleFlush(flushAll = false)

        // Then: write called exactly once with the span's bucket — second flush found empty buckets
        val buckets = captureBuckets()
        assertThat(buckets).hasSize(1)
        assertThat(buckets[0].start).isEqualTo(6 * fakeBucketSizeNs)
        assertThat(buckets[0].stats[0].hits).isEqualTo(1L)
    }

    @Test
    fun `M mark write as not forced W scheduleFlush() { flushAll = false }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan()
        stubNow(farFuture())
        testedConcentrator.record(listOf(span))

        // When
        testedConcentrator.scheduleFlush(flushAll = false)

        // Then
        verify(mockStatsWriter).write(any(), eq(false))
    }

    @Test
    fun `M mark write as forced W scheduleFlush() { flushAll = true }`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan()
        stubNow(farFuture())
        testedConcentrator.record(listOf(span))

        // When
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        verify(mockStatsWriter).write(any(), eq(true))
    }

    // endregion

    // region stop

    @Test
    fun `M submit flush all task W stop()`(forge: Forge) {
        // Given
        val (span) = forge.makeEligibleSpan()
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())

        // When
        testedConcentrator.stop()

        // Then
        verify(mockStatsWriter).write(any(), any())
    }

    @Test
    fun `M not reschedule periodic flush W stop() { periodic flush fires after stop }`() {
        // Given: create concentrator with live scheduling and capture the first scheduled runnable
        val runnableCaptor = argumentCaptor<Runnable>()
        val concentrator = StatsConcentrator(
            sdkCore = mockSdkCore,
            ddSpanToSpanEventMapper = mockSpanEventMapper,
            eventMapper = mockEventMapper,
            bufferLen = fakeBufferLen,
            bucketSizeNs = fakeBucketSizeNs,
            executorService = mockExecutorService,
            statsWriter = mockStatsWriter,
            timeProvider = mockTimeProvider,
            initialConsent = TrackingConsent.GRANTED,
            startPeriodicFlush = true
        )
        verify(mockExecutorService).schedule(runnableCaptor.capture(), any(), any())

        // When: stop then fire the already-scheduled periodic flush callback
        concentrator.stop()
        runnableCaptor.firstValue.run()

        // Then: schedule was called exactly once (initial), never re-scheduled after stop
        verify(mockExecutorService, times(1)).schedule(any<Runnable>(), any(), any())
    }

    // endregion

    // region drainAndFlush

    @Test
    fun `M shut down executor W drainAndFlush()`() {
        // Given
        whenever(mockExecutorService.submit(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
            CompletableFuture.completedFuture(Unit)
        }

        // When
        testedConcentrator.drainAndFlush()

        // Then
        verify(mockExecutorService).shutdown()
        verify(mockExecutorService).awaitTermination(any(), any())
    }

    @Test
    fun `M flush all buckets synchronously W drainAndFlush()`(forge: Forge) {
        // Given
        whenever(mockExecutorService.submit(any())) doAnswer {
            it.getArgument<Runnable>(0).run()
            CompletableFuture.completedFuture(Unit)
        }

        val (span) = forge.makeEligibleSpan()
        testedConcentrator.record(listOf(span))
        stubNow(farFuture())

        // When
        testedConcentrator.drainAndFlush()

        // Then
        assertThat(captureBuckets()).hasSize(1)
    }

    @Test
    fun `M run queued tasks W drainAndFlush() { tasks queued in real ScheduledThreadPoolExecutor }`() {
        // Given
        val taskRan = AtomicBoolean(false)
        val realExecutor = ScheduledThreadPoolExecutor(1)
        val concentrator = StatsConcentrator(
            sdkCore = mockSdkCore,
            ddSpanToSpanEventMapper = mockSpanEventMapper,
            eventMapper = mockEventMapper,
            bufferLen = fakeBufferLen,
            bucketSizeNs = fakeBucketSizeNs,
            executorService = realExecutor,
            statsWriter = mockStatsWriter,
            timeProvider = mockTimeProvider,
            initialConsent = TrackingConsent.GRANTED,
            startPeriodicFlush = false
        )
        realExecutor.execute { taskRan.set(true) }

        // When
        concentrator.drainAndFlush()

        // Then
        assertThat(taskRan.get()).isTrue()
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

    // region Consent

    @Test
    fun `M drop all spans W record() { initialConsent = NOT_GRANTED }`(forge: Forge) {
        // Given
        val concentrator = makeConcentrator(initialConsent = TrackingConsent.NOT_GRANTED)
        val (span) = forge.makeEligibleSpan()

        // When
        concentrator.record(listOf(span))
        concentrator.scheduleFlush(flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M aggregate spans W record() { initialConsent = PENDING }`(forge: Forge) {
        // Given
        val concentrator = makeConcentrator(initialConsent = TrackingConsent.PENDING)
        val (span) = forge.makeEligibleSpan()

        // When
        concentrator.record(listOf(span))
        concentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(captureBuckets()).hasSize(1)
    }

    @Test
    fun `M discard buffered data W onConsentUpdated() { GRANTED to NOT_GRANTED }`(
        forge: Forge
    ) {
        // Given: record a span while consent is GRANTED
        val (span) = forge.makeEligibleSpan()
        testedConcentrator.record(listOf(span))

        // When: consent is revoked
        testedConcentrator.onConsentUpdated(TrackingConsent.NOT_GRANTED)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M drop spans recorded after consent revoked W onConsentUpdated() { GRANTED to NOT_GRANTED }`(
        forge: Forge
    ) {
        // Given: flush any existing data, then revoke consent
        testedConcentrator.onConsentUpdated(TrackingConsent.NOT_GRANTED)
        val (span) = forge.makeEligibleSpan()

        // When: record a span after revocation
        testedConcentrator.record(listOf(span))
        testedConcentrator.scheduleFlush(flushAll = true)

        // Then
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M not flush buffered data W onConsentUpdated() { GRANTED to PENDING }`(forge: Forge) {
        // Given: record a span while consent is GRANTED
        val (span) = forge.makeEligibleSpan()
        testedConcentrator.record(listOf(span))

        // When: consent moves to PENDING — both are recording states, no boundary flush needed
        testedConcentrator.onConsentUpdated(TrackingConsent.PENDING)

        // Then: no flush triggered by the consent change itself
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M not flush buffered data W onConsentUpdated() { PENDING to GRANTED }`(forge: Forge) {
        // Given
        val concentrator = makeConcentrator(initialConsent = TrackingConsent.PENDING)
        val (span) = forge.makeEligibleSpan()
        concentrator.record(listOf(span))

        // When
        concentrator.onConsentUpdated(TrackingConsent.GRANTED)

        // Then: no flush triggered by the consent change itself
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M discard buffered data W onConsentUpdated() { PENDING to NOT_GRANTED }`(forge: Forge) {
        // Given: record a span while consent is PENDING
        val concentrator = makeConcentrator(initialConsent = TrackingConsent.PENDING)
        val (span) = forge.makeEligibleSpan()
        concentrator.record(listOf(span))

        // When: consent is revoked
        concentrator.onConsentUpdated(TrackingConsent.NOT_GRANTED)

        // Then: buffered data is discarded, not written
        verifyNoInteractions(mockStatsWriter)
    }

    @Test
    fun `M resume recording W onConsentUpdated() { NOT_GRANTED to GRANTED }`(forge: Forge) {
        // Given: start with no consent
        val concentrator = makeConcentrator(initialConsent = TrackingConsent.NOT_GRANTED)
        val (span) = forge.makeEligibleSpan()

        // When: grant consent then record
        concentrator.onConsentUpdated(TrackingConsent.GRANTED)
        concentrator.record(listOf(span))
        concentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(captureBuckets()).hasSize(1)
    }

    @Test
    fun `M resume recording W onConsentUpdated() { NOT_GRANTED to PENDING }`(forge: Forge) {
        // Given: start with no consent
        val concentrator = makeConcentrator(initialConsent = TrackingConsent.NOT_GRANTED)
        val (span) = forge.makeEligibleSpan()

        // When: update to pending then record
        concentrator.onConsentUpdated(TrackingConsent.PENDING)
        concentrator.record(listOf(span))
        concentrator.scheduleFlush(flushAll = true)

        // Then
        assertThat(captureBuckets()).hasSize(1)
    }

    // endregion

    // region Helpers

    private fun makeConcentrator(initialConsent: TrackingConsent) = StatsConcentrator(
        sdkCore = mockSdkCore,
        ddSpanToSpanEventMapper = mockSpanEventMapper,
        eventMapper = mockEventMapper,
        bufferLen = fakeBufferLen,
        bucketSizeNs = fakeBucketSizeNs,
        executorService = mockExecutorService,
        statsWriter = mockStatsWriter,
        timeProvider = mockTimeProvider,
        initialConsent = initialConsent,
        startPeriodicFlush = false
    )

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

    /** `now` in nanoseconds that puts any reasonable span bucket well past the flush cutoff. */
    private fun farFuture(): Long = 100 * fakeBucketSizeNs

    private fun stubNow(nowNs: Long) {
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn nowNs / 1_000_000
    }

    private fun captureBuckets(): List<ClientStatsBucket> {
        val captor = argumentCaptor<List<ClientStatsBucket>>()
        verify(mockStatsWriter).write(captor.capture(), any())
        return captor.firstValue
    }

    private fun firstGroup(): ClientGroupedStats = captureBuckets()[0].stats[0]

    // endregion
}
