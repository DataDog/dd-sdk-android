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
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.timeseries.factory.EventFactory
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultTimeseriesCollectorTest {

    private lateinit var testedTimeseriesCollector: DefaultTimeseriesCollector

    @Mock
    lateinit var mockDataWriter: DataWriter<Any>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockReaderA: DataPointsReader<Double>

    @Mock
    lateinit var mockReaderB: DataPointsReader<Double>

    @Mock
    lateinit var mockEventFactoryA: EventFactory<Double, JsonObject>

    @Mock
    lateinit var mockEventFactoryB: EventFactory<Double, JsonObject>

    private lateinit var bufferA: Buffer<Double>
    private lateinit var bufferB: Buffer<Double>

    private lateinit var pipelineA: Pipeline<Double>
    private lateinit var pipelineB: Pipeline<Double>

    @LongForgery(min = 1_000L, max = 60_000L)
    var fakeIntervalAMs: Long = 0L

    @LongForgery(min = 60_001L, max = 120_000L)
    var fakeIntervalBMs: Long = 0L

    @IntForgery(min = 2, max = 16)
    var fakeBufferSize: Int = 0

    @Forgery
    lateinit var fakeRumContext: RumContext

    @Forgery
    lateinit var fakeSessionType: RumSessionType

    private fun fakeRumContextOf(viewType: RumViewType) = fakeRumContext.copy(viewType = viewType)

    private fun createTimeseries(
        pipelines: List<Pipeline<*>> = listOf(
            pipelineA,
            pipelineB
        )
    ): DefaultTimeseriesCollector {
        val mockPipelinesFactory: PipelineFactory = mock()
        whenever(mockPipelinesFactory.create(any())) doReturn pipelines
        return DefaultTimeseriesCollector(
            internalLogger = mockInternalLogger,
            pipelinesFactory = mockPipelinesFactory,
            scheduledExecutorService = mockExecutor
        )
    }

    // Single foreground pipeline wired to reader/event factory A, with a buffer the test controls.
    private fun createTimeseriesWithBuffer(buffer: Buffer<Double>) = createTimeseries(
        listOf(Pipeline(mockSdkCore, mockReaderA, buffer, mockEventFactoryA, mockDataWriter))
    )

    /** Marks the app as resumed with a foreground context, without starting any session. */
    private fun DefaultTimeseriesCollector.resumeInForeground(
        viewType: RumViewType = RumViewType.FOREGROUND
    ) {
        onRumContextUpdate(fakeRumContextOf(viewType))
        onResumed()
    }

    @BeforeEach
    fun `set up`() {
        whenever(mockReaderA.intervalMs) doReturn fakeIntervalAMs
        whenever(mockReaderB.intervalMs) doReturn fakeIntervalBMs
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { inv ->
            inv.getArgument<(DatadogContext, EventWriteScope) -> Unit>(inv.arguments.lastIndex)
                .invoke(mockDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer { inv ->
            inv.getArgument<(EventBatchWriter) -> Unit>(0).invoke(mockEventBatchWriter)
        }
        whenever(mockEventFactoryA.eventName) doReturn ""
        whenever(mockEventFactoryB.eventName) doReturn ""

        bufferA = Buffer(fakeBufferSize)
        bufferB = Buffer(fakeBufferSize)
        pipelineA = Pipeline(mockSdkCore, mockReaderA, bufferA, mockEventFactoryA, mockDataWriter)
        pipelineB = Pipeline(mockSdkCore, mockReaderB, bufferB, mockEventFactoryB, mockDataWriter)

        testedTimeseriesCollector = createTimeseries()
        testedTimeseriesCollector.resumeInForeground()
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["FOREGROUND", "APPLICATION_LAUNCH"]
    )
    fun `M schedule one runnable per pipeline W onSessionStart() { app resumed with foreground view }`(
        viewType: RumViewType
    ) {
        // Given
        val collector = createTimeseries()
        collector.resumeInForeground(viewType)

        // When
        collector.onSessionStart(fakeSessionType)

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not schedule W onSessionStart() { app not resumed }`() {
        // Given
        val collector = createTimeseries()
        collector.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))

        // When
        collector.onSessionStart(fakeSessionType)

        // Then
        verifyNoInteractions(mockExecutor)
    }

    @Test
    fun `M not schedule W onSessionStart() { no foreground context recorded yet }`() {
        // Given
        val collector = createTimeseries()
        collector.onResumed()

        // When
        collector.onSessionStart(fakeSessionType)

        // Then
        verifyNoInteractions(mockExecutor)
    }

    @Test
    fun `M not schedule W onRumContextUpdate() { session not started, app not resumed }`() {
        // Given
        val collector = createTimeseries()

        // When
        collector.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))

        // Then
        verifyNoInteractions(mockExecutor)
    }

    @Test
    fun `M resume scheduling W onResumed() { session already started, no context yet }`() {
        // Given
        val collector = createTimeseries()
        collector.onSessionStart(fakeSessionType)
        collector.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))
        verify(mockExecutor, never()).schedule(any<Runnable>(), any(), any())

        // When
        collector.onResumed()

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M do nothing W onSessionStop() { session never started }`() {
        // When
        assertDoesNotThrow { testedTimeseriesCollector.onSessionStop() }

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockEventFactoryB)
    }

    @Test
    fun `M not shut down shared executor W onSessionStop()`() {
        // Given
        testedTimeseriesCollector.onSessionStart(fakeSessionType)

        // When
        testedTimeseriesCollector.onSessionStop()

        // Then
        verify(mockExecutor, never()).shutdown()
        verify(mockExecutor, never()).shutdownNow()
    }

    @Test
    fun `M not write events W onSessionStop() { nothing sampled }`() {
        // Given
        testedTimeseriesCollector.onSessionStart(fakeSessionType)

        // When
        testedTimeseriesCollector.onSessionStop()

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M log error and flush remaining pipelines W onSessionStop() { pipeline flush throws }`(forge: Forge) {
        // Given
        val fakeError = RuntimeException("drain failure")
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        val mockBuffer = mock<Buffer<Double>>()
        whenever(mockBuffer.drain()) doThrow fakeError
        whenever(mockReaderB.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryB.create(any(), any(), any())) doReturn fakeJson
        val failingPipeline =
            Pipeline(mockSdkCore, mockReaderA, mockBuffer, mockEventFactoryA, mockDataWriter)
        testedTimeseriesCollector = createTimeseries(listOf(failingPipeline, pipelineB))
        testedTimeseriesCollector.resumeInForeground()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        captureScheduledRunnableForInterval(fakeIntervalBMs).run()

        // When
        testedTimeseriesCollector.onSessionStop()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            DefaultTimeseriesCollector.ERROR_FLUSH_FAILED,
            fakeError
        )
        verify(mockDataWriter).write(eq(mockEventBatchWriter), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M flush partial buffer W onSessionStop() { buffer below capacity }`(forge: Forge) {
        // Given
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryA.create(any(), any(), any())) doReturn fakeJson
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        runnableA.run()

        // When
        testedTimeseriesCollector.onSessionStop()

        // Then
        verify(mockEventFactoryA).create(any(), any(), any())
        verify(mockDataWriter).write(eq(mockEventBatchWriter), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M write batch W sample tick { buffer reaches capacity }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeDataPoints = List(fakeBufferSize) { fakePoint }
        val fakeJson = JsonObject().apply { addProperty("a", "1") }
        whenever(mockReaderA.read()) doReturn fakePoint
        whenever(mockEventFactoryA.create(any(), any(), eq(fakeDataPoints))) doReturn fakeJson

        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(fakeDataPoints)
        )
        verify(mockDataWriter).write(eq(mockEventBatchWriter), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M reschedule sample W sample tick runs`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not write events W sample tick { buffer not full }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize - 1) { runnableA.run() }

        // Then
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M not start twice W onSessionStart() { called twice }`() {
        // When
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        testedTimeseriesCollector.onSessionStart(fakeSessionType)

        // Then
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalAMs),
            eq(TimeUnit.MILLISECONDS)
        )
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalBMs),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M flush only once W onSessionStop() { called twice }`() {
        // Given
        val mockBuffer = mock<Buffer<Double>>()
        testedTimeseriesCollector = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseriesCollector.resumeInForeground()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)

        // When
        testedTimeseriesCollector.onSessionStop()
        testedTimeseriesCollector.onSessionStop()

        // Then
        verify(mockBuffer).drain()
    }

    @Test
    fun `M log error and reschedule W sample tick { reader throws }`() {
        // Given
        val fakeError = RuntimeException("reader failure")
        whenever(mockReaderA.read()) doThrow fakeError
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            DefaultTimeseriesCollector.ERROR_SAMPLING_FAILED,
            fakeError
        )
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M log error and reschedule W sample tick { buffer drain throws }`(forge: Forge) {
        // Given
        val fakeError = RuntimeException("drain failure")
        val mockBuffer = mock<Buffer<Double>>()
        whenever(mockBuffer.isFull()) doReturn true
        whenever(mockBuffer.drain()) doThrow fakeError
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseriesCollector.resumeInForeground()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            DefaultTimeseriesCollector.ERROR_SAMPLING_FAILED,
            fakeError
        )
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M log error and reschedule W sample tick { event factory throws }`(forge: Forge) {
        // Given
        val fakeError = RuntimeException("event factory failure")
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryA.create(any(), any(), any())) doThrow fakeError
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Timeseries event creation failed",
            fakeError
        )
        verifyNoInteractions(mockDataWriter)
        verify(mockExecutor, times(fakeBufferSize + 1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M log error and reschedule W sample tick { write context resolution throws }`(forge: Forge) {
        // Given
        val fakeError = RuntimeException("write context resolution failure")
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doThrow fakeError
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            DefaultTimeseriesCollector.ERROR_SAMPLING_FAILED,
            fakeError
        )
        verify(mockExecutor, times(fakeBufferSize + 1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample nor reschedule W sample tick { session stopped }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseriesCollector.onSessionStop()

        // When
        runnableA.run()

        // Then
        verify(mockReaderA, never()).read()
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalAMs),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M neither flush nor schedule suspend W onRumContextUpdate() { stays foreground, no pause }`(
        forge: Forge
    ) {
        // Given
        val fakeNextContext = fakeRumContextOf(
            RumViewType.FOREGROUND
        ).copy(viewId = forge.getForgery<UUID>().toString())
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()

        // When
        testedTimeseriesCollector.onRumContextUpdate(fakeNextContext)

        // Then
        verify(mockExecutor, never())
            .schedule(any<Runnable>(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M use updated context W sample tick fills buffer { foreground view changed }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeNextContext = fakeRumContextOf(RumViewType.FOREGROUND).copy(
            viewId = forge.getForgery<UUID>().toString()
        )
        whenever(mockReaderA.read()) doReturn fakePoint
        whenever(mockEventFactoryA.create(any(), eq(fakeNextContext), any())) doReturn JsonObject()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        testedTimeseriesCollector.onRumContextUpdate(fakeNextContext)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockEventFactoryA).create(any(), eq(fakeNextContext), eq(List(fakeBufferSize) { fakePoint }))
    }

    @Test
    fun `M suspend chain W sample tick { suspend fired after paused }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseriesCollector.onPaused()
        runScheduledSuspend()

        // When
        runnableA.run()

        // Then
        verify(mockReaderA, never()).read()
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalAMs),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M flush partial buffer with last foreground context W pending suspend fires { paused }`(
        forge: Forge
    ) {
        // Given
        val fakeSample = forge.getForgery<DataPoint<Double>>()
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        whenever(mockReaderA.read()) doReturn fakeSample
        whenever(
            mockEventFactoryA.create(
                eq(mockDatadogContext),
                eq(fakeRumContextOf(RumViewType.FOREGROUND)),
                eq(listOf(fakeSample))
            )
        ) doReturn fakeJson
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        testedTimeseriesCollector.onPaused()

        // When
        runScheduledSuspend()

        // Then
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(listOf(fakeSample))
        )
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verifyNoInteractions(mockEventFactoryB)
    }

    @Test
    fun `M flush with the context captured when leaving foreground W suspend fires { newer context }`(
        forge: Forge
    ) {
        // Given
        val fakeForegroundContext = fakeRumContextOf(RumViewType.FOREGROUND)
        val mockPipeline = mock<Pipeline<Double>>()
        val testedCollector = createTimeseries(listOf(mockPipeline))
        testedCollector.onRumContextUpdate(fakeForegroundContext)
        testedCollector.onResumed()
        testedCollector.onSessionStart(fakeSessionType)

        // When
        testedCollector.onPaused()
        testedCollector.onRumContextUpdate(
            fakeForegroundContext.copy(viewId = forge.getForgery<UUID>().toString())
        )
        runScheduledSuspend()

        // Then
        verify(mockPipeline).flush(fakeForegroundContext)
    }

    @Test
    fun `M flush with last foreground context W onSessionStop() { paused, suspend pending }`(forge: Forge) {
        // Given
        val fakeSample = forge.getForgery<DataPoint<Double>>()
        whenever(mockReaderA.read()) doReturn fakeSample
        whenever(mockEventFactoryA.create(any(), any(), any())) doReturn JsonObject()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        testedTimeseriesCollector.onPaused()

        // When
        testedTimeseriesCollector.onSessionStop()

        // Then
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(listOf(fakeSample))
        )
    }

    @Test
    fun `M schedule suspend but not flush yet W onPaused()`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()

        // When
        testedTimeseriesCollector.onPaused()

        // Then
        verify(mockExecutor)
            .schedule(any<Runnable>(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M schedule suspend once W onPaused() { called repeatedly }`() {
        // Given
        testedTimeseriesCollector.onSessionStart(fakeSessionType)

        // When
        testedTimeseriesCollector.onPaused()
        testedTimeseriesCollector.onPaused()

        // Then
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not flush W pending suspend fires { onResumed() before it fired }`() {
        // Given
        val mockPipeline = mock<Pipeline<Double>>()
        val testedCollector = createTimeseries(listOf(mockPipeline))
        testedCollector.resumeInForeground()
        testedCollector.onSessionStart(fakeSessionType)
        testedCollector.onPaused()
        val suspendRunnable = captureScheduledSuspendRunnable()

        // When
        testedCollector.onResumed()
        suspendRunnable.run()

        // Then
        verify(mockPipeline, never()).flush(any())
    }

    @Test
    fun `M not flush twice W pending suspend fires { session already stopped }`() {
        // Given
        val mockBuffer = mock<Buffer<Double>>()
        testedTimeseriesCollector = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseriesCollector.resumeInForeground()
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        testedTimeseriesCollector.onPaused()
        testedTimeseriesCollector.onSessionStop()

        // When
        runScheduledSuspend()

        // Then
        verify(mockBuffer).drain()
    }

    @Test
    fun `M resume scheduling W onResumed() { after paused }`() {
        // Given
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        testedTimeseriesCollector.onPaused()
        runScheduledSuspend()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        verify(mockExecutor, times(1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))

        // When
        testedTimeseriesCollector.onResumed()

        // Then
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample nor reschedule W stale tick fires { new generation is running }`() {
        // Given
        testedTimeseriesCollector.onSessionStart(fakeSessionType)
        testedTimeseriesCollector.onPaused()
        runScheduledSuspend()
        val staleRunnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        testedTimeseriesCollector.onResumed()

        // When
        staleRunnableB.run()

        // Then
        verify(mockReaderB, never()).read()
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    private fun runScheduledSuspend() {
        captureScheduledSuspendRunnable().run()
    }

    private fun captureScheduledSuspendRunnable(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, atLeastOnce())
            .schedule(captor.capture(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }

    private fun captureScheduledRunnableForInterval(intervalMs: Long): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), eq(intervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.firstValue
    }
}
