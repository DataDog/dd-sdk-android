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
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
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
    lateinit var mockExecutor: ScheduledExecutorService

    @Mock
    lateinit var mockScheduledFuture: ScheduledFuture<*>

    @Mock
    lateinit var mockReaderA: DataPointsReader<Double>

    @Mock
    lateinit var mockReaderB: DataPointsReader<Double>

    @Mock
    lateinit var mockSerializerA: JsonSerializer<Double>

    @Mock
    lateinit var mockSerializerB: JsonSerializer<Double>

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

    @BeforeEach
    fun `set up`() {
        whenever(mockReaderA.intervalMs) doReturn fakeIntervalAMs
        whenever(mockReaderB.intervalMs) doReturn fakeIntervalBMs
        whenever(mockExecutor.schedule(any<Runnable>(), any(), any())) doReturn mockScheduledFuture
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer { inv ->
            inv.getArgument<(DatadogContext, EventWriteScope) -> Unit>(inv.arguments.lastIndex)
                .invoke(mock(), mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer { inv ->
            inv.getArgument<(EventBatchWriter) -> Unit>(0).invoke(mockEventBatchWriter)
        }

        bufferA = Buffer(fakeBufferSize)
        bufferB = Buffer(fakeBufferSize)
        pipelineA = Pipeline(mockSdkCore, mockReaderA, bufferA, mockSerializerA, mockDataWriter, mockInternalLogger)
        pipelineB = Pipeline(mockSdkCore, mockReaderB, bufferB, mockSerializerB, mockDataWriter, mockInternalLogger)

        testedTimeseries = createCollector(RumViewType.FOREGROUND)
    }

    @Test
    fun `M schedule one runnable per pipeline W onSessionStart()`() {
        // When
        testedTimeseries.onSessionStart()

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not write events W onSessionStop() { never started }`() {
        // When
        testedTimeseries.onSessionStop()

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
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
    fun `M not write events W onSessionStart()+onSessionStop() { buffers empty }`() {
        // Given
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onSessionStop()

        // Then
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M log error and continue W onSessionStop() { serializer throws on pipelineA }`(forge: Forge) {
        // Given
        val fakeError = RuntimeException("serializer failure")
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockReaderB.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializerA.serialize(any(), any())) doThrow fakeError
        whenever(mockSerializerB.serialize(any(), any())) doReturn fakeJson
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        val runnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        runnableA.run()
        runnableB.run()

        // When
        testedTimeseries.onSessionStop()

        // Then — error logged for pipelineA, pipelineB still flushed
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Timeseries serialization failed",
            fakeError
        )
        verify(mockDataWriter).write(any(), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M flush partial buffer W onSessionStop() { buffer below batch size }`(forge: Forge) {
        // Given
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializerA.serialize(any(), any())) doReturn fakeJson
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        runnableA.run()

        // When
        testedTimeseries.onSessionStop()

        // Then
        verify(mockSerializerA).serialize(any(), any())
        verify(mockDataWriter).write(any(), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M flush batch W sample tick fills buffer { single pipeline }`(forge: Forge) {
        // Given
        val element = forge.getForgery<DataPoint<Double>>()
        val dataPoints = List(fakeBufferSize) { element }
        val fakeJson = JsonObject().apply { addProperty("a", "1") }
        whenever(mockReaderA.read()) doReturn element
        whenever(mockSerializerA.serialize(any(), eq(dataPoints))) doReturn fakeJson

        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockSerializerA).serialize(any(), any())
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
        runnableA.run()

        // Then
        verify(mockSerializerA, never()).serialize(any(), any())
        verify(mockDataWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M skip writer W sample tick { serializer returns null }`(forge: Forge) {
        // Given - fill the buffer so Pipeline.drain() reaches the serializer; serializer returns null
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializerA.serialize(any(), any())) doReturn null
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockSerializerA).serialize(any(), any())
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
    fun `M not throw W onSessionStop() { called twice }`() {
        // Given
        testedTimeseries.onSessionStart()

        // When + Then — must not throw
        testedTimeseries.onSessionStop()
        testedTimeseries.onSessionStop()
    }

    @Test
    fun `M continue scheduling W sample tick { reader throws }`() {
        // Given
        val fakeError = RuntimeException("reader failure")
        whenever(mockReaderA.read()) doThrow fakeError
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then — 1 from start() + 1 rescheduled via finally after exception
        verify(mockExecutor, times(2))
            .schedule(
                any<Runnable>(),
                eq(fakeIntervalAMs),
                eq(TimeUnit.MILLISECONDS)
            )
    }

    @Test
    fun `M log error W sample tick { reader throws }`() {
        // Given
        val fakeError = RuntimeException("reader failure")
        whenever(mockReaderA.read()) doThrow fakeError
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
    }

    @Test
    fun `M log error and reschedule W sample tick { buffer drain throws }`(forge: Forge) {
        // Given — drain() throws outside Pipeline's own try/catch, so it must be
        // caught by DefaultTimeseriesCollector' sampling try/catch instead.
        val fakeError = RuntimeException("drain failure")
        val mockBuffer = mock<Buffer<Double>>()
        whenever(mockBuffer.isFull()) doReturn true
        whenever(mockBuffer.drain()) doThrow fakeError
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        val pipeline =
            Pipeline(mockSdkCore, mockReaderA, mockBuffer, mockSerializerA, mockDataWriter, mockInternalLogger)
        val timeseries = createCollector(RumViewType.FOREGROUND, listOf(pipeline))
        timeseries.onSessionStart()
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
    fun `M log error and reschedule W sample tick { serializer throws }`(forge: Forge) {
        // Given — serializer throws inside Pipeline's own try/catch: Pipeline logs it itself
        // and the tick completes normally, so the sampling chain reschedules as usual.
        val fakeError = RuntimeException("serializer failure")
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockSerializerA.serialize(any(), any())) doThrow fakeError
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        // times(1) default on verifyLog also confirms this is the only ERROR log with that
        // throwable — i.e. DefaultTimeseriesCollector' own sampling catch never fired here.
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Timeseries serialization failed",
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
    fun `M not reschedule W stopped { sample tick skips try block }`() {
        // Given
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseries.onSessionStop()

        // When
        runnableA.run()

        // Then — no additional schedule after the one from start()
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(fakeIntervalAMs),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["NONE", "BACKGROUND"])
    fun `M not schedule W onSessionStart() { initial view is not foreground }`(
        initialViewType: RumViewType
    ) {
        // Given
        val timeseries = createCollector(initialViewType)

        // When
        timeseries.onSessionStart()

        // Then
        verify(mockExecutor, never()).schedule(any<Runnable>(), any(), any())
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["FOREGROUND", "APPLICATION_LAUNCH"])
    fun `M neither flush nor schedule suspend W onViewTypeUpdate() { stays foreground }`(
        nextViewType: RumViewType,
        forge: Forge
    ) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A

        // When
        testedTimeseries.onViewTypeUpdate(nextViewType)

        // Then — no suspend is even scheduled, so nothing can flush later either
        verify(mockExecutor, never())
            .schedule(any<Runnable>(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        verifyNoInteractions(mockSerializerA)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M not flush W onViewTypeUpdate() { suspend not fired yet }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A

        // When — leaving the foreground only schedules the suspend, it does not flush inline
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)

        // Then
        verify(mockExecutor)
            .schedule(any<Runnable>(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        verifyNoInteractions(mockSerializerA)
        verifyNoInteractions(mockDataWriter)
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["NONE", "BACKGROUND"])
    fun `M flush partial buffer W pending suspend fires { left foreground }`(
        nextViewType: RumViewType,
        forge: Forge
    ) {
        // Given
        val fakeSample = forge.getForgery<DataPoint<Double>>()
        val fakeJson = JsonObject().apply { addProperty("fake-key", "fake-value") }
        whenever(mockReaderA.read()) doReturn fakeSample
        whenever(mockSerializerA.serialize(any(), eq(listOf(fakeSample)))) doReturn fakeJson
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A
        testedTimeseries.onViewTypeUpdate(nextViewType)

        // When
        runScheduledSuspend()

        // Then — the batch buffered while in the foreground still reaches the writer
        verify(mockSerializerA).serialize(any(), eq(listOf(fakeSample)))
        verify(mockDataWriter).write(mockEventBatchWriter, fakeJson, EventType.DEFAULT)
        // pipeline B never sampled, so its empty buffer must not produce an event
        verify(mockSerializerB, never()).serialize(any(), any())
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["NONE", "BACKGROUND"])
    fun `M flush W onSessionStop() { left foreground, suspend pending }`(
        nextViewType: RumViewType,
        forge: Forge
    ) {
        // Given
        val fakeSample = forge.getForgery<DataPoint<Double>>()
        whenever(mockReaderA.read()) doReturn fakeSample
        whenever(mockSerializerA.serialize(any(), any())) doReturn JsonObject()
        testedTimeseries.onSessionStart()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run() // buffers one sample for pipeline A
        testedTimeseries.onViewTypeUpdate(nextViewType)

        // When — the session is stopped before the pending suspend had a chance to flush
        testedTimeseries.onSessionStop()

        // Then
        verify(mockSerializerA).serialize(any(), eq(listOf(fakeSample)))
    }

    @Test
    fun `M not flush twice W pending suspend fires { session already stopped }`() {
        // Given
        val mockBuffer = mock<Buffer<Double>>()
        val pipeline =
            Pipeline(mockSdkCore, mockReaderA, mockBuffer, mockSerializerA, mockDataWriter, mockInternalLogger)
        val timeseries = createCollector(RumViewType.FOREGROUND, listOf(pipeline))
        timeseries.onSessionStart()
        timeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        timeseries.onSessionStop() // drains the buffer synchronously

        // When
        runScheduledSuspend()

        // Then — the pending suspend targets a stale generation and must not drain again
        verify(mockBuffer).drain()
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class, names = ["NONE", "BACKGROUND"])
    fun `M skip sample and reschedule W sample tick { foreground exit is pending }`(
        nextViewType: RumViewType
    ) {
        // Given
        testedTimeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseries.onViewTypeUpdate(nextViewType)

        // When — the suspend has not fired yet, so the chain is still alive
        runnableA.run()

        // Then
        verify(mockReaderA, never()).read()
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M suspend chain W sample tick { suspend fired after background }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
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

    @Test
    fun `M cancel pending suspend W onViewTypeUpdate() { foreground re-entered }`() {
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.NONE)

        // When
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)

        // Then
        verify(mockScheduledFuture).cancel(false)
    }

    @Test
    fun `M schedule suspend once W onViewTypeUpdate() { repeated non-foreground updates }`() {
        // Given
        testedTimeseries.onSessionStart()

        // When
        testedTimeseries.onViewTypeUpdate(RumViewType.NONE)
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        testedTimeseries.onViewTypeUpdate(RumViewType.NONE)

        // Then
        verify(mockExecutor).schedule(
            any<Runnable>(),
            eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M keep sampling chain W stale suspend fires { foreground re-entered then left }`() {
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.NONE)
        val staleSuspend = captureScheduledSuspendRunnable()
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)
        val currentRunnableA = captureLastScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)

        // When — the suspend from the first exit fires against a generation that moved on
        staleSuspend.run()
        currentRunnableA.run()

        // Then — nothing flushed, and the live chain keeps rescheduling
        verify(mockDataWriter, never()).write(any(), any(), any())
        verify(mockReaderA, never()).read()
        verify(mockExecutor, times(3))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M resume scheduling W onViewTypeUpdate() { background to foreground }`() {
        // Given — the deferred suspend fired, so the next tick dies without rescheduling
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        runScheduledSuspend()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        verify(mockExecutor, times(1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))

        // When — app returns to foreground
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)

        // Then — scheduling resumed, one new schedule per pipeline
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample nor reschedule W stale tick fires { new generation is running }`() {
        // Regression: a tick from the generation that was alive before the suspend must
        // self-terminate on the generation check, even though the state is running again.
        //
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        runScheduledSuspend()
        val staleRunnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        // Resume: a fresh generation is running; A and B each get a new schedule
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)

        // When — B's stale tick fires
        staleRunnableB.run()

        // Then — stale tick neither samples nor produces an extra schedule
        verify(mockReaderB, never()).read()
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    private fun createCollector(
        initialViewType: RumViewType,
        pipelines: List<Pipeline<*>> = listOf(pipelineA, pipelineB)
    ) = DefaultTimeseriesCollector(
        pipelines = pipelines,
        internalLogger = mockInternalLogger,
        scheduledExecutorService = mockExecutor,
        currentViewType = initialViewType
    )

    private fun runScheduledSuspend() {
        captureScheduledSuspendRunnable().run()
    }

    private fun captureScheduledSuspendRunnable(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, atLeastOnce())
            .schedule(captor.capture(), eq(DefaultTimeseriesCollector.SUSPEND_DELAY_MS), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }

    private fun captureLastScheduledRunnableForInterval(intervalMs: Long): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor, atLeastOnce()).schedule(captor.capture(), eq(intervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.lastValue
    }

    private fun captureScheduledRunnableForInterval(intervalMs: Long): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), eq(intervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.firstValue
    }
}
