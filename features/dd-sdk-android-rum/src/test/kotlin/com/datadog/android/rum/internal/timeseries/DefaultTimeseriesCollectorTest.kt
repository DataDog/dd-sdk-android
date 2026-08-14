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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultTimeseriesCollectorTest {

    private lateinit var testedTimeseries: DefaultTimeseriesCollector

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
    lateinit var mockScheduledFuture: ScheduledFuture<*>

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

    private fun fakeRumContextOf(viewType: RumViewType) = fakeRumContext.copy(viewType = viewType)

    private fun createTimeseries(
        initialViewType: RumViewType,
        pipelines: List<Pipeline<*>> = listOf(pipelineA, pipelineB)
    ) = DefaultTimeseriesCollector(
        pipelines = pipelines,
        internalLogger = mockInternalLogger,
        scheduledExecutorService = mockExecutor,
        rumContext = fakeRumContextOf(initialViewType)
    )

    // Single foreground pipeline wired to reader/event factory A, with a buffer the test controls.
    private fun createTimeseriesWithBuffer(buffer: Buffer<Double>) = createTimeseries(
        RumViewType.FOREGROUND,
        listOf(Pipeline(mockSdkCore, mockReaderA, buffer, mockEventFactoryA, mockDataWriter, { emptyMap() }))
    )

    @BeforeEach
    fun `set up`() {
        whenever(mockReaderA.intervalMs) doReturn fakeIntervalAMs
        whenever(mockReaderB.intervalMs) doReturn fakeIntervalBMs
        whenever(mockExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture
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
        pipelineA = Pipeline(mockSdkCore, mockReaderA, bufferA, mockEventFactoryA, mockDataWriter, { emptyMap() })
        pipelineB = Pipeline(mockSdkCore, mockReaderB, bufferB, mockEventFactoryB, mockDataWriter, { emptyMap() })

        testedTimeseries = createTimeseries(RumViewType.FOREGROUND)
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["FOREGROUND", "APPLICATION_LAUNCH"]
    )
    fun `M schedule one runnable per pipeline W onSessionStart() { initial view is foreground }`(
        initialViewType: RumViewType
    ) {
        // Given
        testedTimeseries = createTimeseries(initialViewType)

        // When
        testedTimeseries.onSessionStart()

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["NONE", "BACKGROUND"]
    )
    fun `M not schedule W onSessionStart() { initial view is not foreground }`(
        initialViewType: RumViewType
    ) {
        // Given
        testedTimeseries = createTimeseries(initialViewType)

        // When
        testedTimeseries.onSessionStart()

        // Then
        verify(mockExecutor, never()).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun `M not schedule W onRumContextUpdate() { none to background }`() {
        // Given
        testedTimeseries = createTimeseries(RumViewType.NONE)
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))

        // Then — neither a sampling chain nor a suspend: the app never left the foreground
        verify(mockExecutor, never()).schedule(any<Runnable>(), any(), any())
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["FOREGROUND", "APPLICATION_LAUNCH"]
    )
    fun `M resume scheduling W onRumContextUpdate() { none to foreground }`(
        foregroundViewType: RumViewType
    ) {
        // Given
        testedTimeseries = createTimeseries(RumViewType.NONE)
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(foregroundViewType))

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not shut down shared executor W onSessionStop()`() {
        // Given
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onSessionStop()

        // Then — executor is owned by the SDK core and reused across components
        verify(mockExecutor, never()).shutdown()
        verify(mockExecutor, never()).shutdownNow()
    }

    @Test
    fun `M not write events W onSessionStop() { nothing sampled }`() {
        // Given
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onSessionStop()

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M log error and flush remaining pipelines W onSessionStop() { pipeline flush throws }`(forge: Forge) {
        // Given — drain() throws outside Pipeline's own try/catch, so the failure surfaces in
        // DefaultTimeseriesCollector.flushPipelines() and must not skip the remaining pipelines.
        val fakeError = RuntimeException("drain failure")
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        val mockBuffer = mock<Buffer<Double>>()
        whenever(mockBuffer.drain()) doThrow fakeError
        whenever(mockReaderB.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryB.create(any(), any(), any(), any())) doReturn fakeJson
        val failingPipeline =
            Pipeline(mockSdkCore, mockReaderA, mockBuffer, mockEventFactoryA, mockDataWriter, { emptyMap() })
        testedTimeseries = createTimeseries(RumViewType.FOREGROUND, listOf(failingPipeline, pipelineB))
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalBMs).run() // buffers one sample for pipeline B

        // When
        testedTimeseries.onSessionStop()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            DefaultTimeseriesCollector.ERROR_FLUSH_FAILED,
            fakeError
        )
        verify(mockDataWriter).write(any(), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M flush partial buffer W onSessionStop() { buffer below capacity }`(forge: Forge) {
        // Given
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryA.create(any(), any(), any(), any())) doReturn fakeJson
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        runnableA.run()

        // When
        testedTimeseries.onSessionStop()

        // Then
        verify(mockEventFactoryA).create(any(), any(), any(), any())
        verify(mockDataWriter).write(any(), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M write batch W sample tick { buffer reaches capacity }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeDataPoints = List(fakeBufferSize) { fakePoint }
        val fakeJson = JsonObject().apply { addProperty("a", "1") }
        whenever(mockReaderA.read()) doReturn fakePoint
        whenever(mockEventFactoryA.create(any(), any(), eq(fakeDataPoints), any())) doReturn fakeJson

        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(fakeDataPoints),
            any()
        )
        verify(mockDataWriter).write(any(), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M reschedule sample W sample tick runs`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then - 1 schedule from start() + 1 reschedule from sample tick
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not write events W sample tick { buffer not full }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize - 1) { runnableA.run() }

        // Then
        verify(mockEventFactoryA, never()).create(any(), any(), any(), any())
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M not start twice W onSessionStart() { called twice }`() {
        // When
        testedTimeseries.onSessionStart()
        testedTimeseries.onSessionStart()

        // Then — executor.schedule called exactly once per pipeline (not doubled)
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
        testedTimeseries = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onSessionStop()
        testedTimeseries.onSessionStop()

        // Then — the second call sees the state already STOPPED and never reaches the pipelines
        verify(mockBuffer).drain()
    }

    @Test
    fun `M log error and reschedule W sample tick { reader throws }`() {
        // Given
        val fakeError = RuntimeException("reader failure")
        whenever(mockReaderA.read()) doThrow fakeError
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then — 1 schedule from start() + 1 rescheduled via finally after the exception
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
        // Given — drain() throws outside Pipeline's own try/catch, so it must be
        // caught by DefaultTimeseriesCollector's sampling try/catch instead.
        val fakeError = RuntimeException("drain failure")
        val mockBuffer = mock<Buffer<Double>>()
        whenever(mockBuffer.isFull()) doReturn true
        whenever(mockBuffer.drain()) doThrow fakeError
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseries.onSessionStart()
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
        // Given — event factory throws inside Pipeline's own try/catch: Pipeline logs it itself
        // and the tick completes normally, so the sampling chain reschedules as usual.
        val fakeError = RuntimeException("event factory failure")
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryA.create(any(), any(), any(), any())) doThrow fakeError
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        // verifyLog matches on `same(fakeError)`, so its default times(1) also proves the
        // collector's own sampling catch never logged the same throwable a second time.
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Timeseries event creation failed",
            fakeError
        )
        verify(mockDataWriter, never()).write(any(), any(), any())
        verify(mockExecutor, times(fakeBufferSize + 1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M log error and reschedule W sample tick { write context resolution throws }`(forge: Forge) {
        // Given — withWriteContext() itself throws (context/scope resolution failure), outside
        // Pipeline's own try/catch, so it propagates up to the sampling try/catch.
        val fakeError = RuntimeException("write context resolution failure")
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doThrow fakeError
        testedTimeseries.onSessionStart()
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
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseries.onSessionStop()

        // When
        runnableA.run()

        // Then — sample skipped AND no additional schedule after the one from start()
        verify(mockReaderA, never()).read()
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalAMs),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["FOREGROUND", "APPLICATION_LAUNCH"]
    )
    fun `M neither flush nor schedule suspend W onRumContextUpdate() { stays foreground }`(
        nextViewType: RumViewType,
        forge: Forge
    ) {
        // Given
        val fakeNextContext = fakeRumContextOf(nextViewType).copy(viewId = forge.getForgery<UUID>().toString())
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A

        // When
        testedTimeseries.onRumContextUpdate(fakeNextContext)

        // Then — no suspend is even scheduled, so nothing can flush later either
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
        whenever(mockEventFactoryA.create(any(), eq(fakeNextContext), any(), any())) doReturn JsonObject()
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeNextContext)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockEventFactoryA).create(any(), eq(fakeNextContext), eq(List(fakeBufferSize) { fakePoint }), any())
    }

    @Test
    fun `M suspend chain W sample tick { suspend fired after background }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))
        runScheduledSuspend()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then — sample skipped AND no reschedule (chain suspended)
        verify(mockReaderA, never()).read()
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalAMs),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["NONE", "BACKGROUND"]
    )
    fun `M flush partial buffer with last foreground context W pending suspend fires { left foreground }`(
        nextViewType: RumViewType,
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
                eq(listOf(fakeSample)),
                any()
            )
        ) doReturn fakeJson
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(nextViewType))

        // When
        runScheduledSuspend()

        // Then — the batch is stamped with the view the user was looking at, not the new one
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(listOf(fakeSample)),
            any()
        )
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        verify(mockEventFactoryB, never()).create(any(), any(), any(), any())
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["NONE", "BACKGROUND"]
    )
    fun `M flush with last foreground context W onSessionStop() { left foreground, suspend pending }`(
        nextViewType: RumViewType,
        forge: Forge
    ) {
        // Given
        val fakeSample = forge.getForgery<DataPoint<Double>>()
        whenever(mockReaderA.read()) doReturn fakeSample
        whenever(mockEventFactoryA.create(any(), any(), any(), any())) doReturn JsonObject()
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(nextViewType))

        // When — the session is stopped before the pending suspend had a chance to flush
        testedTimeseries.onSessionStop()

        // Then — same attribution as the suspend path, not the background context
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(listOf(fakeSample)),
            any()
        )
    }

    @Test
    fun `M not flush W onRumContextUpdate() { suspend not fired yet }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A

        // When — leaving the foreground only schedules the suspend, it does not flush inline
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))

        // Then
        verify(mockExecutor)
            .schedule(any<Runnable>(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockDataWriter)
    }

    @ParameterizedTest
    @EnumSource(
        value = RumViewType::class,
        names = ["NONE", "BACKGROUND"]
    )
    fun `M skip sample and reschedule W sample tick { foreground exit is pending }`(
        nextViewType: RumViewType
    ) {
        // Given
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(nextViewType))

        // When
        runnableA.run()

        // Then
        verify(mockReaderA, never()).read()
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M keep sampling chain W stale suspend fires { foreground re-entered then left }`() {
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.NONE))
        val staleSuspend = captureScheduledSuspendRunnable()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))
        val currentRunnableA = captureLastScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))

        // When
        staleSuspend.run()
        currentRunnableA.run()

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
        verify(mockReaderA, never()).read()
        verify(mockExecutor, times(3))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M cancel pending suspend W onRumContextUpdate() { foreground re-entered }`() {
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.NONE))

        // When
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))

        // Then
        verify(mockScheduledFuture).cancel(false)
    }

    @Test
    fun `M schedule suspend once W onRumContextUpdate() { repeated non-foreground updates }`() {
        // Given
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.NONE))
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.NONE))

        // Then
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not flush twice W pending suspend fires { session already stopped }`() {
        // Given
        val mockBuffer = mock<Buffer<Double>>()
        testedTimeseries = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))
        testedTimeseries.onSessionStop() // drains the buffer synchronously

        // When
        runScheduledSuspend()

        // Then — the pending suspend loses the CAS against STOPPED and does nothing
        verify(mockBuffer).drain()
    }

    @Test
    fun `M resume scheduling W onRumContextUpdate() { background to foreground }`() {
        // Given — the deferred suspend fired, so the next tick dies without rescheduling
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))
        runScheduledSuspend()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        verify(mockExecutor, times(1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))

        // When — app returns to foreground
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))

        // Then — scheduling resumed, one new schedule per pipeline
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample nor reschedule W stale tick fires { new generation is running }`() {
        // Regression: a tick from the generation that was alive before the suspend must
        // self-terminate on the generation check, even though the state is RUNNING again.
        //
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.BACKGROUND))
        runScheduledSuspend()
        val staleRunnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        // Resume: state = RUNNING, generation = 2; A and B each get a fresh schedule
        testedTimeseries.onRumContextUpdate(fakeRumContextOf(RumViewType.FOREGROUND))

        // When — B's stale generation-1 tick fires while state is RUNNING
        staleRunnableB.run()

        // Then — stale tick neither samples nor produces an extra schedule
        verify(mockReaderB, never()).read()
        // B schedules: 1 from start() + 1 from the resume = 2; the stale tick adds nothing
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

    private fun captureLastScheduledRunnableForInterval(intervalMs: Long): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, atLeastOnce()).schedule(captor.capture(), eq(intervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }
}
