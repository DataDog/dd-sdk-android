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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
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
internal class RumSessionScopeTimeseriesTest {

    private lateinit var testedTimeseries: RumSessionScopeTimeseries

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

    @LongForgery(min = 1L, max = 60_000L)
    var fakeIntervalAMs: Long = 0L

    @LongForgery(min = 60_001L, max = 120_000L)
    var fakeIntervalBMs: Long = 0L

    @IntForgery(min = 2, max = 16)
    var fakeBufferSize: Int = 0

    @BeforeEach
    fun `set up`() {
        whenever(mockReaderA.intervalMs) doReturn fakeIntervalAMs
        whenever(mockReaderB.intervalMs) doReturn fakeIntervalBMs
        whenever(mockExecutor.schedule(any<Runnable>(), any(), any())) doReturn mock<ScheduledFuture<*>>()
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

        testedTimeseries = RumSessionScopeTimeseries(
            pipelines = listOf(pipelineA, pipelineB),
            internalLogger = mockInternalLogger,
            collectInBackground = false,
            scheduledExecutorService = mockExecutor
        )
        // Seed FOREGROUND so sampling tests don't suspend on first tick
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)
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
    fun `M not start after stop W onSessionStart() { called after onSessionStop() }`() {
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onSessionStop()

        // When
        testedTimeseries.onSessionStart()

        // Then — no additional schedules beyond the original start
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
            RumSessionScopeTimeseries.ERROR_SAMPLING_FAILED,
            fakeError
        )
    }

    @Test
    fun `M log error and reschedule W sample tick { buffer drain throws }`(forge: Forge) {
        // Given — drain() throws outside Pipeline's own try/catch, so it must be
        // caught by RumSessionScopeTimeseries' sampling try/catch instead.
        val fakeError = RuntimeException("drain failure")
        val mockBuffer = mock<Buffer<Double>>()
        whenever(mockBuffer.isFull()) doReturn true
        whenever(mockBuffer.drain()) doThrow fakeError
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        val pipeline =
            Pipeline(mockSdkCore, mockReaderA, mockBuffer, mockSerializerA, mockDataWriter, mockInternalLogger)
        val timeseries = RumSessionScopeTimeseries(
            pipelines = listOf(pipeline),
            internalLogger = mockInternalLogger,
            collectInBackground = false,
            scheduledExecutorService = mockExecutor
        )
        timeseries.onViewTypeUpdate(RumViewType.FOREGROUND)
        timeseries.onSessionStart()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            RumSessionScopeTimeseries.ERROR_SAMPLING_FAILED,
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
        // throwable — i.e. RumSessionScopeTimeseries' own sampling catch never fired here.
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
            RumSessionScopeTimeseries.ERROR_SAMPLING_FAILED,
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

    @Test
    fun `M suspend chain W sample tick { background, collectInBackground = false }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
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
    fun `M sample W sample tick { background, collectInBackground = true }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        val bgTimeseries = RumSessionScopeTimeseries(
            pipelines = listOf(pipelineA, pipelineB),
            internalLogger = mockInternalLogger,
            collectInBackground = true,
            scheduledExecutorService = mockExecutor
        )
        bgTimeseries.onSessionStart()
        bgTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        runnableA.run()

        // Then — sample taken even in background
        verify(mockReaderA).read()
    }

    @Test
    fun `M resume scheduling W onViewTypeUpdate() { background → foreground }`() {
        // Given
        testedTimeseries.onSessionStart()
        // Transition to background: suspends chains
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        runnableA.run() // State is already SUSPENDED; tick exits without reschedule
        val schedulesAfterSuspend = 1 // only the one from start()

        // When — app returns to foreground
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)

        // Then — scheduling resumed (one new schedule per pipeline)
        verify(mockExecutor, times(schedulesAfterSuspend + 1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample W stale tick fires after resume { old generation tick self-terminates }`(forge: Forge) {
        // Regression: when pipeline B's tick (long interval) is still pending at the moment
        // resumeSampling() fires, it must self-terminate rather than duplicate the chain.
        //
        // Given
        whenever(mockReaderB.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseries.onSessionStart()

        // Pipeline A suspends the instance (short interval fires first in background)
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        val oldRunnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        val oldRunnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        oldRunnableA.run() // State is already SUSPENDED (set by onViewTypeUpdate); B's tick is still queued

        // Resume creates a new generation and schedules fresh ticks for both pipelines
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)

        // When — B's old (stale) runnable fires despite the new generation
        oldRunnableB.run()

        // Then — stale tick must not sample and must not reschedule
        verify(mockReaderB, never()).read()
        // Total B schedules: 1 from start() + 1 from resumeSampling = 2; old tick adds nothing
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample or reschedule W stale tick fires { different generation, state is RUNNING }`() {
        // A stale tick (old generation) must self-terminate via the generation check
        // even when state is currently RUNNING. Ticks never write state.
        //
        // Given
        testedTimeseries.onSessionStart()
        testedTimeseries.onViewTypeUpdate(RumViewType.BACKGROUND)
        val oldRunnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        // Resume: state = RUNNING, gen = 2; A and B each get a new schedule
        testedTimeseries.onViewTypeUpdate(RumViewType.FOREGROUND)

        // When — B's stale gen-1 tick fires while state is RUNNING
        oldRunnableB.run()

        // Then — stale tick neither samples nor produces an extra schedule
        verify(mockReaderB, never()).read()
        // B schedules: 1 from start() + 1 from resume = 2; stale tick adds nothing
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    private fun captureScheduledRunnableForInterval(intervalMs: Long): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), eq(intervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.firstValue
    }
}
