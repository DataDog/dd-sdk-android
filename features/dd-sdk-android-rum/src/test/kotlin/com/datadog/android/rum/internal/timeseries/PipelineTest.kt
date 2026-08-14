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
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.timeseries.factory.EventFactory
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.TimeseriesAttributes
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.forge.aThrowable
import com.google.gson.JsonObject
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class PipelineTest {

    @Forgery
    lateinit var fakeRumContext: RumContext

    @Mock
    lateinit var mockReader: DataPointsReader<Double>

    @Mock
    lateinit var mockEventFactory: EventFactory<Double, JsonObject>

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockDataWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockInsightsCollector: InsightsCollector

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery(min = 2, max = 10)
    var fakeBufferSize: Int = 0

    @StringForgery
    lateinit var fakeAttributeKey: String

    @StringForgery
    lateinit var fakeAttributeValue: String

    private var fakeCustomAttributes: Map<String, Any?> = emptyMap()

    private lateinit var buffer: Buffer<Double>
    private lateinit var testedPipeline: Pipeline<Double>

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
                .invoke(mockDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            it.getArgument<(EventBatchWriter) -> Unit>(0).invoke(mockEventBatchWriter)
        }
        whenever(mockDataWriter.write(any(), any(), any())) doReturn true
        whenever(mockEventFactory.eventName) doReturn "view.cpu"

        fakeCustomAttributes = mapOf(fakeAttributeKey to fakeAttributeValue)
        buffer = Buffer(fakeBufferSize)
        testedPipeline = Pipeline(
            sdkCore = mockSdkCore,
            reader = mockReader,
            buffer = buffer,
            eventFactory = mockEventFactory,
            dataWriter = mockDataWriter,
            customAttributes = { fakeCustomAttributes },
            insightsCollector = mockInsightsCollector
        )
    }

    @Test
    fun `M expose reader intervalMs W intervalMs`(@LongForgery(min = 1L) fakeIntervalMs: Long) {
        // Given
        whenever(mockReader.intervalMs) doReturn fakeIntervalMs

        // When / Then
        assertThat(testedPipeline.intervalMs).isEqualTo(fakeIntervalMs)
    }

    // region execute()

    @Test
    fun `M append sample to buffer W execute()`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        whenever(mockReader.read()) doReturn fakePoint

        // When
        testedPipeline.execute(fakeRumContext)

        // Then
        assertThat(buffer.drain()).containsExactly(fakePoint)
    }

    @Test
    fun `M not add to buffer W execute() { reader returns null }`() {
        // Given
        whenever(mockReader.read()) doReturn null

        // When
        testedPipeline.execute(fakeRumContext)

        // Then
        assertThat(buffer.drain()).isEmpty()
    }

    @Test
    fun `M not hold the lock while reading W execute()`(forge: Forge) {
        // Given — a concurrent flush() races the reader and must not wait for the read to finish
        var flushedWhileReading = false
        whenever(mockReader.read()) doAnswer {
            val flushed = CountDownLatch(1)
            Thread {
                testedPipeline.flush(fakeRumContext)
                flushed.countDown()
            }.start()
            flushedWhileReading = flushed.await(CONCURRENT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            forge.getForgery<DataPoint<Double>>()
        }

        // When
        testedPipeline.execute(fakeRumContext)

        // Then
        assertThat(flushedWhileReading)
            .describedAs("flush() completed while reader.read() was still running")
            .isTrue()
    }

    // endregion

    // region flush()

    @Test
    fun `M flush W execute() { buffer fills to capacity }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeTimeseriesName = "view.cpu"
        val fakeJson = fakeTimeseriesJson(fakeTimeseriesName)
        whenever(mockReader.read()) doReturn fakePoint
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeJson
        whenever(mockEventFactory.eventName) doReturn fakeTimeseriesName
        repeat(fakeBufferSize - 1) { testedPipeline.execute(fakeRumContext) }

        // When
        testedPipeline.execute(fakeRumContext)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verify(mockInsightsCollector).onTimeseries(fakeTimeseriesName)
        assertThat(buffer.drain()).isEmpty()
    }

    @Test
    fun `M not flush W execute() { buffer not full }`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()

        // When
        repeat(fakeBufferSize - 1) { testedPipeline.execute(fakeRumContext) }

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M not flush W execute() { event factory returns null }`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn null
        repeat(fakeBufferSize - 1) { testedPipeline.execute(fakeRumContext) }

        // When
        testedPipeline.execute(fakeRumContext)

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    // endregion

    // region flush()

    @Test
    fun `M write partial buffer W flush() { buffer has data }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeTimeseriesName = "view.memory"
        val fakeJson = fakeTimeseriesJson(fakeTimeseriesName)
        whenever(mockReader.read()) doReturn fakePoint
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeJson
        whenever(mockEventFactory.eventName) doReturn fakeTimeseriesName
        val sampleCount = fakeBufferSize - 1
        repeat(sampleCount) { testedPipeline.execute(fakeRumContext) }

        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        val captor = argumentCaptor<List<DataPoint<Double>>>()
        verify(mockEventFactory).create(any(), eq(fakeRumContext), captor.capture(), any())
        assertThat(captor.firstValue).hasSize(sampleCount).allMatch { it == fakePoint }
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verify(mockInsightsCollector).onTimeseries(fakeTimeseriesName)
        assertThat(buffer.drain()).isEmpty()
    }

    @Test
    fun `M drain buffer before write context callback W flush()`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { Unit }
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn fakePoint
        testedPipeline.execute(fakeRumContext)

        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        assertThat(buffer.drain()).isEmpty()
        verifyNoInteractions(mockEventFactory)
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M pass pre-flush points to event factory W execute() { point added before callback }`(
        forge: Forge
    ) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeNextPoint = forge.getForgery<DataPoint<Double>>()
        val callbackCaptor = argumentCaptor<(DatadogContext, EventWriteScope) -> Unit>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { Unit }
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn fakePoint
        testedPipeline.execute(fakeRumContext)
        testedPipeline.flush(fakeRumContext)
        whenever(mockReader.read()) doReturn fakeNextPoint

        // When
        testedPipeline.execute(fakeRumContext)
        verify(mockRumFeatureScope).withWriteContext(any(), callbackCaptor.capture())
        callbackCaptor.firstValue.invoke(mockDatadogContext, mockEventWriteScope)

        // Then
        val dataPointsCaptor = argumentCaptor<List<DataPoint<Double>>>()
        verify(mockEventFactory).create(eq(mockDatadogContext), eq(fakeRumContext), dataPointsCaptor.capture(), any())
        assertThat(dataPointsCaptor.firstValue).containsExactly(fakePoint)
        assertThat(buffer.drain()).containsExactly(fakeNextPoint)
    }

    @Test
    fun `M pass contexts to event factory W flush()`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        testedPipeline.execute(fakeRumContext)

        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        verify(mockEventFactory).create(eq(mockDatadogContext), eq(fakeRumContext), any(), any())
    }

    @Test
    fun `M resolve custom attributes before write context callback W flush()`(forge: Forge) {
        // Given — attributes must be read while flushing, not when the deferred callback creates the event:
        // by then the RUM monitor owning them may already be gone.
        val callbackCaptor = argumentCaptor<(DatadogContext, EventWriteScope) -> Unit>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { Unit }
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedPipeline.execute(fakeRumContext)
        val expectedAttributes = fakeCustomAttributes

        // When
        testedPipeline.flush(fakeRumContext)
        fakeCustomAttributes = emptyMap()
        verify(mockRumFeatureScope).withWriteContext(any(), callbackCaptor.capture())
        callbackCaptor.firstValue.invoke(mockDatadogContext, mockEventWriteScope)

        // Then
        verify(mockEventFactory).create(any(), any(), any(), eq(expectedAttributes))
    }

    @Test
    fun `M request event creation feature contexts W flush()`(forge: Forge) {
        // Given
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeTimeseriesJson("view.cpu")
        testedPipeline.execute(fakeRumContext)

        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        verify(mockRumFeatureScope).withWriteContext(
            eq(setOf(Feature.SESSION_REPLAY_FEATURE_NAME, Feature.TRACING_FEATURE_NAME)),
            any()
        )
    }

    @Test
    fun `M not write W flush() { buffer empty }`() {
        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M log error W flush() { event factory throws }`(forge: Forge) {
        // Given
        val fakeThrowable = forge.aThrowable()
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactory.create(any(), any(), any(), any())) doThrow fakeThrowable
        testedPipeline.execute(fakeRumContext)

        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = "Timeseries event creation failed",
            throwable = fakeThrowable
        )
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M log error W flush() { dataWriter throws }`(forge: Forge) {
        // Given
        val fakeThrowable = forge.aThrowable()
        val fakeJson = fakeTimeseriesJson("view.cpu")
        val writeBlockCaptor = argumentCaptor<(EventBatchWriter) -> Unit>()
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeJson
        whenever(mockDataWriter.write(any(), any(), any())) doThrow fakeThrowable
        whenever(mockEventWriteScope.invoke(any())) doAnswer { Unit }
        testedPipeline.execute(fakeRumContext)

        // When
        testedPipeline.flush(fakeRumContext)
        verify(mockEventWriteScope).invoke(writeBlockCaptor.capture())
        writeBlockCaptor.firstValue.invoke(mockEventBatchWriter)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = "Timeseries flush failed",
            throwable = fakeThrowable
        )
        verifyNoInteractions(mockInsightsCollector)
    }

    @Test
    fun `M not notify insights W flush() { dataWriter returns false }`(forge: Forge) {
        // Given
        val fakeJson = fakeTimeseriesJson("view.cpu")
        whenever(mockReader.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactory.create(any(), any(), any(), any())) doReturn fakeJson
        whenever(mockDataWriter.write(any(), any(), any())) doReturn false
        testedPipeline.execute(fakeRumContext)

        // When
        testedPipeline.flush(fakeRumContext)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verifyNoInteractions(mockInsightsCollector)
    }

    // endregion

    // region concurrency

    @Test
    fun `M not lose or duplicate points W execute() { concurrent samplers }`() {
        // Given — every read() yields a unique point, every drained batch is recorded
        val counter = AtomicLong()
        val batches = CopyOnWriteArrayList<List<DataPoint<Double>>>()
        stubUniqueReads(counter)
        stubBatchRecording(batches)

        // When
        runConcurrently { testedPipeline.execute(fakeRumContext) }
        val batchesBeforeFlush = batches.toList()
        testedPipeline.flush(fakeRumContext)

        // Then
        assertThat(batchesBeforeFlush.map { it.size })
            .describedAs("batches drained by execute() are always exactly one buffer worth")
            .containsOnly(fakeBufferSize)
        assertThat(batches.flatten().map { it.timestampNs }.sorted())
            .isEqualTo((1L..counter.get()).toList())
    }

    @Test
    fun `M not lose or duplicate points W execute() and flush() { concurrent }`() {
        // Given
        val counter = AtomicLong()
        val batches = CopyOnWriteArrayList<List<DataPoint<Double>>>()
        stubUniqueReads(counter)
        stubBatchRecording(batches)

        // When — one thread keeps flushing while the others keep sampling
        runConcurrently { threadIndex ->
            if (threadIndex == 0) {
                testedPipeline.flush(fakeRumContext)
            } else {
                testedPipeline.execute(fakeRumContext)
            }
        }
        testedPipeline.flush(fakeRumContext)

        // Then
        assertThat(batches.flatten().map { it.timestampNs }.sorted())
            .isEqualTo((1L..counter.get()).toList())
    }

    @Test
    fun `M eventually capture point W flush() races execute() { flush between read() and add() }`(
        forge: Forge
    ) {
        // Given — reader.read() blocks until released, forcing execute() to sit between
        // its (unsynchronized) read() and the synchronized buffer add()
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        whenever(mockReader.read()) doAnswer {
            readStarted.countDown()
            releaseRead.await(CONCURRENT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            fakePoint
        }
        val batches = CopyOnWriteArrayList<List<DataPoint<Double>>>()
        stubBatchRecording(batches)
        val executor = Executors.newSingleThreadExecutor()

        // When
        executor.execute { testedPipeline.execute(fakeRumContext) }
        readStarted.await(CONCURRENT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        testedPipeline.flush(fakeRumContext) // races execute(): read() is in flight, not yet added
        releaseRead.countDown()
        executor.shutdown()
        check(executor.awaitTermination(CONCURRENT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "execute() did not complete in time"
        }
        testedPipeline.flush(fakeRumContext) // point survives for the next flush

        // Then
        assertThat(batches.flatten()).containsExactly(fakePoint)
    }

    // endregion

    private fun stubUniqueReads(counter: AtomicLong) {
        whenever(mockReader.read()) doAnswer { DataPoint(counter.incrementAndGet(), 0.0) }
    }

    private fun stubBatchRecording(batches: MutableList<List<DataPoint<Double>>>) {
        whenever(mockEventFactory.create(any(), any(), any(), any())) doAnswer { invocation ->
            batches.add(invocation.getArgument(2))
            fakeTimeseriesJson("view.cpu")
        }
    }

    private fun runConcurrently(task: (threadIndex: Int) -> Unit) {
        val executor = Executors.newFixedThreadPool(CONCURRENT_THREADS)
        val start = CountDownLatch(1)
        val done = CountDownLatch(CONCURRENT_THREADS)
        repeat(CONCURRENT_THREADS) { threadIndex ->
            executor.execute {
                start.await()
                repeat(ITERATIONS_PER_THREAD) { task(threadIndex) }
                done.countDown()
            }
        }
        start.countDown()
        val finished = done.await(CONCURRENT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        executor.shutdownNow()
        check(finished) { "concurrent tasks did not complete in time" }
    }

    private fun fakeTimeseriesJson(name: String): JsonObject = JsonObject().apply {
        add(
            TimeseriesAttributes.KEY_TIMESERIES,
            JsonObject().apply {
                addProperty(TimeseriesAttributes.KEY_NAME, name)
            }
        )
    }

    private companion object {
        const val CONCURRENT_FLUSH_TIMEOUT_MS = 2000L
        const val CONCURRENT_THREADS = 4
        const val ITERATIONS_PER_THREAD = 200
    }
}
