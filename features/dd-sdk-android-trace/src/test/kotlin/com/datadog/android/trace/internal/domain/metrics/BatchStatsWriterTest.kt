/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.trace.assertj.MsgPackAssert
import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.msgpack.core.MessagePack
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BatchStatsWriterTest {

    private lateinit var testedWriter: BatchStatsWriter

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @LongForgery(min = 0L)
    var fakeBucketStart: Long = 0L

    @LongForgery(min = 1L)
    var fakeBucketDuration: Long = 0L

    @StringForgery
    lateinit var fakeRuntimeID: String

    @BoolForgery
    var fakeForced: Boolean = false

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn mockFeatureScope
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockFeatureScope.withWriteContext(eq(emptySet()), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockEventBatchWriter.write(any(), anyOrNull(), any())) doReturn true

        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID)
    }

    // region write

    @Test
    fun `M write payload to batch writer W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        verify(mockEventBatchWriter).write(any(), eq(null), eq(EventType.DEFAULT))
    }

    @Test
    fun `M encode context env in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data)).hasField("Stats[0].Env", fakeDatadogContext.env)
    }

    @Test
    fun `M encode context version in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data))
            .hasField("Stats[0].Version", fakeDatadogContext.version)
    }

    @Test
    fun `M encode context service in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data))
            .hasField("Stats[0].Service", fakeDatadogContext.service)
    }

    @Test
    fun `M encode context sdkVersion as tracer version in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data))
            .hasField("Stats[0].TracerVersion", fakeDatadogContext.sdkVersion)
    }

    @Test
    fun `M use sequence number 0 on first call W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data)).hasField("Stats[0].Sequence", 0L)
    }

    @Test
    fun `M increment sequence number on each call W write() { multiple calls }`(
        @IntForgery(min = 2, max = 10) callCount: Int
    ) {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        repeat(callCount) { testedWriter.write(fakeBuckets, fakeForced) }

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(callCount)).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        captor.allValues.forEachIndexed { index, event ->
            MsgPackAssert.assertThat(gunzip(event.data)).hasField("Stats[0].Sequence", index.toLong())
        }
    }

    @Test
    fun `M encode runtimeID in payload W write()`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data)).hasField("Stats[0].RuntimeID", fakeRuntimeID)
    }

    @Test
    fun `M do nothing W write() { feature not registered }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn null
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        verify(mockEventBatchWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M not write anything W write() { all buckets have empty group lists }`() {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        verify(mockEventBatchWriter, never()).write(any(), any(), any())
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not increment sequence number W write() { all buckets have empty group lists }`() {
        // Given
        val fakeEmptyBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, emptyList()))
        val fakeNonEmptyBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeEmptyBuckets, fakeForced)
        testedWriter.write(fakeNonEmptyBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data)).hasField("Stats[0].Sequence", 0L)
    }

    // endregion

    // region write - splitting

    @Test
    fun `M write a single unsplit batch W write() { total groups within cap }`(
        @IntForgery(min = 3, max = 10) fakeCap: Int
    ) {
        // Given
        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID, maxGroupsPerBatch = fakeCap)
        val fakeGroups = (0 until fakeCap).map { fakeGroup() }
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, fakeGroups))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(1)).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        MsgPackAssert.assertThat(gunzip(captor.firstValue.data)).hasField("SplitPayload", false)
    }

    @Test
    fun `M split into multiple batches W write() { total groups exceed cap }`() {
        // Given
        val fakeCap = 2
        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID, maxGroupsPerBatch = fakeCap)
        val fakeGroups = (0 until 5).map { fakeGroup(hits = it.toLong(), errors = 0L) }
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, fakeGroups))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(3)).write(captor.capture(), eq(null), eq(EventType.DEFAULT))

        val decodedGroupCounts = captor.allValues.map { event ->
            val decoded = gunzip(event.data)
            val batch = MsgPackAssert.assertThat(decoded)
            batch.hasField("SplitPayload", true)
            decodeBatchGroupHits(decoded)
        }
        assertThat(decodedGroupCounts.sumOf { it.size }).isEqualTo(5)
        assertThat(decodedGroupCounts.flatten().sorted()).isEqualTo((0 until 5).map { it.toLong() })
        assertThat(decodedGroupCounts.all { it.size <= fakeCap }).isTrue()
    }

    @Test
    fun `M preserve bucket start and duration across split batches W write()`(
        @LongForgery(min = 1L) fakeOtherBucketDuration: Long
    ) {
        // Given
        val fakeCap = 2
        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID, maxGroupsPerBatch = fakeCap)
        val fakeGroups = (0 until 3).map { fakeGroup() }
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeOtherBucketDuration, fakeGroups))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(2)).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        captor.allValues.forEach { event ->
            val decoded = gunzip(event.data)
            MsgPackAssert.assertThat(decoded).hasField("Stats[0].Stats[0].Start", fakeBucketStart)
            MsgPackAssert.assertThat(decoded).hasField("Stats[0].Stats[0].Duration", fakeOtherBucketDuration)
        }
    }

    @Test
    fun `M share sequence number across batches W write() { split }`() {
        // Given
        val fakeCap = 2
        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID, maxGroupsPerBatch = fakeCap)
        val fakeGroups = (0 until 5).map { fakeGroup() }
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, fakeGroups))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter, times(3)).write(captor.capture(), eq(null), eq(EventType.DEFAULT))
        captor.allValues.forEach { event ->
            MsgPackAssert.assertThat(gunzip(event.data)).hasField("Stats[0].Sequence", 0L)
        }
    }

    @Test
    fun `M send flush metric once W write() { split into multiple batches }`() {
        // Given
        val fakeCap = 2
        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID, maxGroupsPerBatch = fakeCap)
        val fakeGroups = (0 until 5).map { fakeGroup(hits = 1L, errors = 0L) }
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, fakeGroups))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger, times(1)).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        assertThat(captor.firstValue[BatchStatsWriter.KEY_GROUPS_COUNT]).isEqualTo(5)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_SPANS_COUNT]).isEqualTo(5L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_GROUPS_COUNT]).isEqualTo(0)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_SPANS_COUNT]).isEqualTo(0L)
    }

    @Test
    fun `M send flush metric with dropped counts W write() { one batch write fails }`() {
        // Given
        val fakeCap = 2
        testedWriter = BatchStatsWriter(mockSdkCore, fakeRuntimeID, maxGroupsPerBatch = fakeCap)
        val fakeGroups = (0 until 5).map { fakeGroup(hits = 1L, errors = 1L) }
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, fakeGroups))
        whenever(mockEventBatchWriter.write(any(), anyOrNull(), any()))
            .doReturn(true, true, false)

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        verify(mockEventBatchWriter, times(3)).write(any(), eq(null), eq(EventType.DEFAULT))
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger, times(1)).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        // 2 successful batches of 2 groups each = 4 groups succeeded, last batch of 1 group dropped
        assertThat(captor.firstValue[BatchStatsWriter.KEY_GROUPS_COUNT]).isEqualTo(4)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_SPANS_COUNT]).isEqualTo(4L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_ERRORS_COUNT]).isEqualTo(4L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_BUCKETS_COUNT]).isEqualTo(1)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_GROUPS_COUNT]).isEqualTo(1)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_SPANS_COUNT]).isEqualTo(1L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_ERRORS_COUNT]).isEqualTo(1L)
    }

    @Test
    fun `M send flush metric with everything dropped W write() { all batch writes fail }`() {
        // Given
        val fakeGroup = fakeGroup(hits = 2L, errors = 1L)
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup)))
        whenever(mockEventBatchWriter.write(any(), anyOrNull(), any())) doReturn false

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger, times(1)).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        assertThat(captor.firstValue[BatchStatsWriter.KEY_BUCKETS_COUNT]).isEqualTo(0)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_GROUPS_COUNT]).isEqualTo(0)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_BUCKETS_COUNT]).isEqualTo(1)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_GROUPS_COUNT]).isEqualTo(1)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_SPANS_COUNT]).isEqualTo(2L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_ERRORS_COUNT]).isEqualTo(1L)
    }

    // endregion

    // region flush telemetry

    @Test
    fun `M send flush metric after writing to storage W write()`() {
        // Given
        val fakeGroup = fakeGroup(hits = 3L, errors = 1L)
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup)))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        assertThat(captor.firstValue[BatchStatsWriter.KEY_METRIC_TYPE]).isEqualTo(BatchStatsWriter.VALUE_METRIC_TYPE)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_BUCKETS_COUNT]).isEqualTo(1)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_GROUPS_COUNT]).isEqualTo(1)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_SPANS_COUNT]).isEqualTo(3L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_ERRORS_COUNT]).isEqualTo(1L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_FORCED]).isEqualTo(fakeForced)
    }

    @Test
    fun `M sum groups, hits and errors across buckets W write()`() {
        // Given
        val group1 = fakeGroup(hits = 3L, errors = 1L)
        val group2 = fakeGroup(hits = 5L, errors = 2L)
        val group3 = fakeGroup(hits = 2L, errors = 0L)
        val fakeBuckets = listOf(
            ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(group1, group2)),
            ClientStatsBucket(fakeBucketStart + fakeBucketDuration, fakeBucketDuration, listOf(group3))
        )

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        assertThat(captor.firstValue[BatchStatsWriter.KEY_METRIC_TYPE]).isEqualTo(BatchStatsWriter.VALUE_METRIC_TYPE)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_BUCKETS_COUNT]).isEqualTo(2)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_GROUPS_COUNT]).isEqualTo(3)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_SPANS_COUNT]).isEqualTo(10L)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_ERRORS_COUNT]).isEqualTo(3L)
    }

    @Test
    fun `M forward forced flag W write()`(
        @BoolForgery fakeMetricForced: Boolean
    ) {
        // Given
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeMetricForced)

        // Then
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        assertThat(captor.firstValue[BatchStatsWriter.KEY_FORCED]).isEqualTo(fakeMetricForced)
    }

    @Test
    fun `M not send flush metric W write() { feature not registered }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.TRACING_CLIENT_STATS_FEATURE_NAME)) doReturn null
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send flush metric with all dropped W write() { batch write fails }`() {
        // Given
        whenever(mockEventBatchWriter.write(any(), anyOrNull(), any())) doReturn false
        val fakeBuckets = listOf(ClientStatsBucket(fakeBucketStart, fakeBucketDuration, listOf(fakeGroup())))

        // When
        testedWriter.write(fakeBuckets, fakeForced)

        // Then
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mockInternalLogger).logMetric(
            messageBuilder = argThat { invoke() == BatchStatsWriter.METRIC_MESSAGE },
            additionalProperties = captor.capture(),
            samplingRate = eq(15.0f),
            creationSampleRate = eq(null)
        )
        assertThat(captor.firstValue[BatchStatsWriter.KEY_BUCKETS_COUNT]).isEqualTo(0)
        assertThat(captor.firstValue[BatchStatsWriter.KEY_DROPPED_BUCKETS_COUNT]).isEqualTo(1)
    }

    // endregion

    private fun gunzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(bytes.inputStream()).use { it.copyTo(output) }
        return output.toByteArray()
    }

    private fun decodeBatchGroupHits(batchBytes: ByteArray): List<Long> {
        val json = MessagePack.newDefaultUnpacker(batchBytes).use { it.unpackValue().toJson() }
        val envelope = JsonParser.parseString(json).asJsonObject
        val bucket = envelope.getAsJsonArray("Stats")[0].asJsonObject.getAsJsonArray("Stats")[0].asJsonObject
        return bucket.getAsJsonArray("Stats").map { it.asJsonObject["Hits"].asLong }
    }

    private fun fakeGroup(hits: Long = 0L, errors: Long = 0L) = ClientGroupedStats(
        service = "service",
        name = "name",
        resource = "resource",
        httpStatusCode = 200,
        type = "type",
        spanKind = "kind",
        isTraceRoot = Trilean.TRUE,
        hits = hits,
        errors = errors,
        duration = 0L,
        topLevelHits = 0L,
        okSummary = ByteArray(0),
        errorSummary = ByteArray(0),
        isSynthetic = false,
        peerTags = emptyList(),
        serviceSource = ""
    )
}
