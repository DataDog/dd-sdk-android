/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.vitals.VitalReader
import com.datadog.android.rum.model.RumTimeseriesCpuEvent
import com.datadog.android.rum.model.RumTimeseriesMemoryEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TimeseriesSessionCollectorTest {

    private lateinit var testedCollector: TimeseriesSessionCollector

    @Mock
    lateinit var mockMemoryReader: VitalReader

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @StringForgery
    lateinit var fakeSessionId: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @LongForgery(min = 1_000_000_000L)
    var fakeTotalRamBytes: Long = 0L

    @DoubleForgery(min = 1.0, max = 100.0)
    var fakeCpuUsage: Double = 0.0

    @DoubleForgery(min = 1_000_000.0, max = 4_000_000_000.0)
    var fakeMemoryBytes: Double = 0.0

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        whenever(mockExecutor.schedule(any(), any(), any())) doReturn mock<ScheduledFuture<*>>()
        whenever(mockMemoryReader.readVitalData()) doReturn fakeMemoryBytes

        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor },
            compressionSampler = { false }
        )
    }

    @Test
    fun `M schedule first sample W start()`() {
        // When
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)

        // Then
        verify(mockExecutor).schedule(
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `M shutdown executor W stop()`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)

        // When
        testedCollector.stop()

        // Then
        verify(mockExecutor).shutdownNow()
    }

    @Test
    fun `M flush memory batch W stop() { buffer not empty }`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then - memory event was written (once from sample trigger + possibly once from stop flush)
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val memoryEvents = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>()
        assertThat(memoryEvents).hasSize(1)
        assertThat(memoryEvents.first().session.id).isEqualTo(fakeSessionId)
        assertThat(memoryEvents.first().application.id).isEqualTo(fakeApplicationId)
    }

    @Test
    fun `M flush cpu batch W stop() { buffer not empty }`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val cpuEvents = captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>()
        assertThat(cpuEvents).hasSize(1)
        assertThat(cpuEvents.first().session.id).isEqualTo(fakeSessionId)
        assertThat(cpuEvents.first().application.id).isEqualTo(fakeApplicationId)
    }

    @Test
    fun `M flush batch W batchSize samples collected { memory }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()

        // When - run 3 samples to trigger flush
        repeat(3) { sampleRunnable.run() }

        // Then - one flush happened on the 3rd sample
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val memoryEvents = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>()
        assertThat(memoryEvents).hasSize(1)
        assertThat(memoryEvents.first().timeseries.data).hasSize(3)
    }

    @Test
    fun `M flush batch W batchSize samples collected { cpu }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()

        // When - run 3 samples to trigger flush
        repeat(3) { sampleRunnable.run() }

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val cpuEvents = captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>()
        assertThat(cpuEvents).hasSize(1)
        assertThat(cpuEvents.first().timeseries.data).hasSize(3)
    }

    @Test
    fun `M have start less than or equal to end W flush() { memory }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        repeat(3) { sampleRunnable.run() }

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val memoryEvent = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>().first()
        assertThat(memoryEvent.timeseries.start).isLessThanOrEqualTo(memoryEvent.timeseries.end)
    }

    @Test
    fun `M have start less than or equal to end W flush() { cpu }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        repeat(3) { sampleRunnable.run() }

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val cpuEvent = captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>().first()
        assertThat(cpuEvent.timeseries.start).isLessThanOrEqualTo(cpuEvent.timeseries.end)
    }

    @Test
    fun `M write memory event with correct dataPoints W flush()`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val memoryEvent = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>().first()
        val dataPoint = memoryEvent.timeseries.data.first().dataPoint
        assertThat(dataPoint.memoryMax.toDouble())
            .isCloseTo(fakeMemoryBytes, org.assertj.core.data.Offset.offset(0.001))
        assertThat(dataPoint.memoryPercent.toDouble())
            .isCloseTo(fakeMemoryBytes / fakeTotalRamBytes * 100.0, org.assertj.core.data.Offset.offset(0.001))
    }

    @Test
    fun `M write cpu event with correct dataPoint W flush()`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val cpuEvent = captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>().first()
        assertThat(cpuEvent.timeseries.data.first().dataPoint.cpuUsage.toDouble())
            .isCloseTo(fakeCpuUsage, org.assertj.core.data.Offset.offset(0.001))
    }

    @Test
    fun `M use USER session type W start() { no override }`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val memoryEvent = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>().first()
        assertThat(memoryEvent.session.type).isEqualTo(RumTimeseriesMemoryEvent.Type.USER)
    }

    @Test
    fun `M use SYNTHETICS session type W start() { synthetics session }`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.SYNTHETICS)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(2)).write(any(), captor.capture(), any())
        val memoryEvent = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>().first()
        assertThat(memoryEvent.session.type).isEqualTo(RumTimeseriesMemoryEvent.Type.SYNTHETICS)
    }

    @Test
    fun `M not write memory sample W memory reader returns null`() {
        // Given
        whenever(mockMemoryReader.readVitalData()) doReturn null
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then - no memory event written (cpu event still written)
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        assertThat(captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>()).isEmpty()
    }

    @Test
    fun `M not write cpu sample W cpu provider returns null`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            cpuUsageProvider = { null },
            executorFactory = { mockExecutor }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then - no cpu event written (memory event still written)
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        assertThat(captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>()).isEmpty()
    }

    @Test
    fun `M write event with DEFAULT EventType W flush()`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then
        val typeCaptor = argumentCaptor<EventType>()
        verify(mockWriter, times(2)).write(any(), any(), typeCaptor.capture())
        typeCaptor.allValues.forEach {
            assertThat(it).isEqualTo(EventType.DEFAULT)
        }
    }

    @Test
    fun `M reschedule sampling W sample runs`() {
        // Given
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()

        // When
        sampleRunnable.run()

        // Then - scheduled twice: once in start(), once in sample()
        verify(mockExecutor, times(2)).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun `M not write events W stop() called without start()`() {
        // When
        testedCollector.stop()

        // Then
        verify(mockWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M write delta event W flush() { delta compression sampled, memory batch }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { null },
            executorFactory = { mockExecutor },
            compressionSampler = { true }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        repeat(3) { sampleRunnable.run() }

        // Then - 1 delta JsonObject written, no typed object event
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        assertThat(captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>()).isEmpty()

        val deltaPayload = captor.firstValue as JsonObject
        val tsJson = deltaPayload.getAsJsonObject("timeseries")
        assertThat(tsJson.get("schema").asString).isEqualTo("delta-object")
        val dataField = tsJson.get("data").asJsonObject
        assertThat(dataField.get("precision").asInt).isEqualTo(4)
        assertThat(dataField.has("ts")).isTrue()
        assertThat(dataField.has("memory_max")).isTrue()
        assertThat(dataField.has("memory_percent")).isTrue()
    }

    @Test
    fun `M write object event W flush() { object schema sampled, memory batch }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { null },
            executorFactory = { mockExecutor },
            compressionSampler = { false }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        repeat(3) { sampleRunnable.run() }

        // Then - typed object event written, no JsonObject delta
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        val typedEvents = captor.allValues.filterIsInstance<RumTimeseriesMemoryEvent>()
        assertThat(typedEvents).hasSize(1)
        assertThat(typedEvents[0].timeseries.schema).isEqualTo(RumTimeseriesMemoryEvent.Schema.OBJECT)
        assertThat(captor.allValues.filterIsInstance<JsonObject>()).isEmpty()
    }

    @Test
    fun `M write delta event W flush() { delta compression sampled, cpu batch }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor },
            compressionSampler = { true }
        )
        whenever(mockMemoryReader.readVitalData()) doReturn null
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        repeat(3) { sampleRunnable.run() }

        // Then - 1 delta JsonObject written, no typed object event
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        assertThat(captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>()).isEmpty()

        val deltaPayload = captor.firstValue as JsonObject
        val tsJson = deltaPayload.getAsJsonObject("timeseries")
        assertThat(tsJson.get("schema").asString).isEqualTo("delta-scalar")
        val dataField = tsJson.get("data").asJsonObject
        assertThat(dataField.get("precision").asInt).isEqualTo(4)
        assertThat(dataField.has("ts")).isTrue()
        assertThat(dataField.has("value")).isTrue()
    }

    @Test
    fun `M write object event W flush() { object schema sampled, cpu batch }`() {
        // Given
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 3,
            cpuUsageProvider = { fakeCpuUsage },
            executorFactory = { mockExecutor },
            compressionSampler = { false }
        )
        whenever(mockMemoryReader.readVitalData()) doReturn null
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        repeat(3) { sampleRunnable.run() }

        // Then - typed object event written, no JsonObject delta
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        val typedEvents = captor.allValues.filterIsInstance<RumTimeseriesCpuEvent>()
        assertThat(typedEvents).hasSize(1)
        assertThat(typedEvents[0].timeseries.schema).isEqualTo(RumTimeseriesCpuEvent.Schema.OBJECT)
        assertThat(captor.allValues.filterIsInstance<JsonObject>()).isEmpty()
    }

    @Test
    fun `M fall back to object event W flush() { delta sampled, single sample }`() {
        // Given — delta sampled but only 1 sample: DeltaEncoder returns null, falls back to object
        testedCollector = TimeseriesSessionCollector(
            memoryReader = mockMemoryReader,
            writer = mockWriter,
            sdkCore = mockSdkCore,
            totalRamBytes = fakeTotalRamBytes,
            batchSize = 100,
            cpuUsageProvider = { null },
            executorFactory = { mockExecutor },
            compressionSampler = { true }
        )
        testedCollector.start(fakeSessionId, fakeApplicationId, RumSessionType.USER)
        val sampleRunnable = captureScheduledRunnable()
        sampleRunnable.run()

        // When
        testedCollector.stop()

        // Then - falls back to object event, no JsonObject delta
        val captor = argumentCaptor<Any>()
        verify(mockWriter, times(1)).write(any(), captor.capture(), any())
        assertThat(captor.firstValue).isInstanceOf(RumTimeseriesMemoryEvent::class.java)
        assertThat((captor.firstValue as RumTimeseriesMemoryEvent).timeseries.schema)
            .isEqualTo(RumTimeseriesMemoryEvent.Schema.OBJECT)
    }

    private fun captureScheduledRunnable(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), any(), any())
        return captor.firstValue
    }
}
