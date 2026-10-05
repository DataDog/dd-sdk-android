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
import com.datadog.android.rum.internal.timeseries.collector.DefaultTimeseriesCollector
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
import org.assertj.core.api.Assertions.assertThat
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

    private fun fakeRumContextOf(
        viewType: RumViewType,
        sessionId: String = fakeRumContext.sessionId
    ) = fakeRumContext.copy(viewType = viewType, sessionId = sessionId)

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
        listOf(
            Pipeline(
                mockSdkCore,
                mockReaderA,
                buffer,
                mockEventFactoryA,
                mockDataWriter,
                internalLogger = mockInternalLogger
            )
        )
    )

    private fun DefaultTimeseriesCollector.startInForeground() {
        onStarted()
    }

    private fun DefaultTimeseriesCollector.startSession(
        sessionId: String = fakeRumContext.sessionId,
        sessionType: RumSessionType = fakeSessionType,
        viewType: RumViewType = RumViewType.FOREGROUND
    ) {
        onSessionStart(sessionId, sessionType)
        onRumContextUpdate(fakeRumContextOf(viewType, sessionId))
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
        pipelineA = Pipeline(
            mockSdkCore,
            mockReaderA,
            bufferA,
            mockEventFactoryA,
            mockDataWriter,
            internalLogger = mockInternalLogger
        )
        pipelineB = Pipeline(
            mockSdkCore,
            mockReaderB,
            bufferB,
            mockEventFactoryB,
            mockDataWriter,
            internalLogger = mockInternalLogger
        )

        testedTimeseriesCollector = createTimeseries()
        testedTimeseriesCollector.startInForeground()
    }

    @ParameterizedTest
    @EnumSource(value = RumViewType::class)
    fun `M schedule one runnable per pipeline W onSessionStart() { app started with RUM context }`(
        viewType: RumViewType
    ) {
        // Given
        val collector = createTimeseries()
        collector.startInForeground()

        // When
        collector.startSession(viewType = viewType)

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not start collection W onResumed() { process not started }`() {
        // Given
        val collector = createTimeseries()
        collector.startSession()

        // When
        collector.onResumed()

        // Then
        verifyNoInteractions(mockExecutor)
    }

    @Test
    fun `M flush previous pipelines W onSessionStart() { session already active }`() {
        // Given
        val mockPreviousPipeline = mock<Pipeline<Double>>()
        val mockNextPipeline = mock<Pipeline<Double>>()
        val mockPipelinesFactory: PipelineFactory = mock()
        whenever(mockPipelinesFactory.create(any()))
            .thenReturn(listOf(mockPreviousPipeline), listOf(mockNextPipeline))
        val collector = DefaultTimeseriesCollector(
            internalLogger = mockInternalLogger,
            pipelinesFactory = mockPipelinesFactory,
            scheduledExecutorService = mockExecutor
        )
        collector.startInForeground()
        collector.startSession()
        val fakeNextSessionId = "${fakeRumContext.sessionId}-next"

        // When — another session starts before this one was explicitly stopped
        // (see RumApplicationScope's MULTIPLE_ACTIVE_SESSIONS_ERROR)
        collector.startSession(sessionId = fakeNextSessionId)

        // Then
        verify(mockPreviousPipeline).flush(fakeRumContextOf(RumViewType.FOREGROUND))
        assertThat(collector.pipelines).containsExactly(mockNextPipeline)
    }

    @Test
    fun `M keep current session active W onSessionStop() { obsolete session }`() {
        // Given
        val fakePreviousSessionId = fakeRumContext.sessionId
        val fakeCurrentSessionId = "$fakePreviousSessionId-current"
        val mockPreviousPipeline = mock<Pipeline<Double>>()
        val mockCurrentPipeline = mock<Pipeline<Double>>()
        val mockPipelinesFactory: PipelineFactory = mock()
        whenever(mockPipelinesFactory.create(any()))
            .thenReturn(listOf(mockPreviousPipeline), listOf(mockCurrentPipeline))
        val collector = DefaultTimeseriesCollector(
            internalLogger = mockInternalLogger,
            pipelinesFactory = mockPipelinesFactory,
            scheduledExecutorService = mockExecutor
        )
        collector.startInForeground()
        collector.startSession(sessionId = fakePreviousSessionId)
        collector.startSession(sessionId = fakeCurrentSessionId)

        // When
        collector.onSessionStop(fakePreviousSessionId)

        // Then
        verify(mockCurrentPipeline, never()).flush(any())
        collector.onSessionStop(fakeCurrentSessionId)
        verify(mockCurrentPipeline).flush(
            fakeRumContextOf(RumViewType.FOREGROUND, fakeCurrentSessionId)
        )
    }

    @Test
    fun `M keep current context W onRumContextUpdate() { obsolete session }`(forge: Forge) {
        // Given
        val fakePreviousSessionId = fakeRumContext.sessionId
        val fakeCurrentSessionId = "$fakePreviousSessionId-current"
        val fakeCurrentContext = fakeRumContextOf(RumViewType.FOREGROUND, fakeCurrentSessionId)
        val fakeObsoleteContext = fakeRumContextOf(RumViewType.FOREGROUND, fakePreviousSessionId).copy(
            viewId = forge.getForgery<UUID>().toString()
        )
        val mockPreviousPipeline = mock<Pipeline<Double>>()
        val mockCurrentPipeline = mock<Pipeline<Double>>()
        val mockPipelinesFactory: PipelineFactory = mock()
        whenever(mockPipelinesFactory.create(any()))
            .thenReturn(listOf(mockPreviousPipeline), listOf(mockCurrentPipeline))
        val collector = DefaultTimeseriesCollector(
            internalLogger = mockInternalLogger,
            pipelinesFactory = mockPipelinesFactory,
            scheduledExecutorService = mockExecutor
        )
        collector.startInForeground()
        collector.startSession(sessionId = fakePreviousSessionId)
        collector.startSession(sessionId = fakeCurrentSessionId)

        // When
        collector.onRumContextUpdate(fakeObsoleteContext)
        collector.onSessionStop(fakeCurrentSessionId)

        // Then
        verify(mockCurrentPipeline).flush(fakeCurrentContext)
    }

    @Test
    fun `M wait for current context W onSessionStart() { previous context is cached }`() {
        // Given
        val fakeCurrentSessionId = "${fakeRumContext.sessionId}-current"
        testedTimeseriesCollector.startSession()

        // When
        testedTimeseriesCollector.onSessionStart(fakeCurrentSessionId, fakeSessionType)

        // Then
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor).schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))

        // When
        testedTimeseriesCollector.onRumContextUpdate(
            fakeRumContextOf(RumViewType.FOREGROUND, fakeCurrentSessionId)
        )

        // Then
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M do nothing W onSessionStop() { session never started }`() {
        // When
        assertDoesNotThrow { testedTimeseriesCollector.onSessionStop(fakeRumContext.sessionId) }

        // Then
        verifyNoInteractions(mockDataWriter)
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockEventFactoryB)
    }

    @Test
    fun `M not shut down executor nor write events W onSessionStop() { nothing sampled }`() {
        // Given
        testedTimeseriesCollector.startSession()

        // When
        testedTimeseriesCollector.onSessionStop(fakeRumContext.sessionId)

        // Then
        verify(mockExecutor, never()).shutdown()
        verify(mockExecutor, never()).shutdownNow()
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
            Pipeline(
                mockSdkCore,
                mockReaderA,
                mockBuffer,
                mockEventFactoryA,
                mockDataWriter,
                internalLogger = mockInternalLogger
            )
        testedTimeseriesCollector = createTimeseries(listOf(failingPipeline, pipelineB))
        testedTimeseriesCollector.startInForeground()
        testedTimeseriesCollector.startSession()
        captureScheduledRunnableForInterval(fakeIntervalBMs).run()

        // When
        testedTimeseriesCollector.onSessionStop(fakeRumContext.sessionId)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            Pipeline.ERROR_FLUSH_FAILED,
            fakeError,
            onlyOnce = true
        )
        verify(mockDataWriter).write(eq(mockEventBatchWriter), eq(fakeJson), eq(EventType.DEFAULT))
    }

    @Test
    fun `M flush partial buffer W onSessionStop() { buffer below capacity }`(forge: Forge) {
        // Given
        val fakeJson = JsonObject().apply { addProperty("k", "v") }
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryA.create(any(), any(), any())) doReturn fakeJson
        testedTimeseriesCollector.startSession()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        runnableA.run()

        // When
        testedTimeseriesCollector.onSessionStop(fakeRumContext.sessionId)

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

        testedTimeseriesCollector.startSession()
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
    fun `M not write events W sample tick { buffer not full }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.startSession()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize - 1) { runnableA.run() }

        // Then
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M log error and reschedule W sample tick { event factory throws }`(forge: Forge) {
        // Given
        val fakeError = RuntimeException("event factory failure")
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        whenever(mockEventFactoryA.create(any(), any(), any())) doThrow fakeError
        testedTimeseriesCollector.startSession()
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
    fun `M use updated context W sample tick fills buffer { foreground view changed }`(forge: Forge) {
        // Given
        val fakePoint = forge.getForgery<DataPoint<Double>>()
        val fakeNextContext = fakeRumContextOf(RumViewType.FOREGROUND).copy(
            viewId = forge.getForgery<UUID>().toString()
        )
        whenever(mockReaderA.read()) doReturn fakePoint
        whenever(mockEventFactoryA.create(any(), eq(fakeNextContext), any())) doReturn JsonObject()
        testedTimeseriesCollector.startSession()
        testedTimeseriesCollector.onRumContextUpdate(fakeNextContext)
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        repeat(fakeBufferSize) { runnableA.run() }

        // Then
        verify(mockEventFactoryA).create(any(), eq(fakeNextContext), eq(List(fakeBufferSize) { fakePoint }))
    }

    @Test
    fun `M keep collection running W onPaused() + sample tick { process remains started }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.startSession()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)

        // When
        testedTimeseriesCollector.onPaused()
        runnableA.run()

        // Then
        verify(mockReaderA).read()
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor, never())
            .schedule(any<Runnable>(), eq(DefaultTimeseriesCollector.BACKGROUND_TRANSITION_DELAY), any())
    }

    @Test
    fun `M suspend chain W sample tick { suspend fired after stopped }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.startSession()
        val runnableA = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseriesCollector.onStopped()
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
    fun `M flush partial buffer with last foreground context W pending suspend fires { stopped }`(
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
        testedTimeseriesCollector.startSession()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        testedTimeseriesCollector.onStopped()

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
        testedCollector.onStarted()
        testedCollector.startSession()

        // When
        testedCollector.onStopped()
        testedCollector.onRumContextUpdate(
            fakeForegroundContext.copy(viewId = forge.getForgery<UUID>().toString())
        )
        runScheduledSuspend()

        // Then
        verify(mockPipeline).flush(fakeForegroundContext)
    }

    @Test
    fun `M flush outgoing pipelines with outgoing context W suspend fires { session renewed while stopped }`() {
        // Given
        val fakePreviousSessionId = fakeRumContext.sessionId
        val fakeNextSessionId = "$fakePreviousSessionId-next"
        val fakePreviousContext = fakeRumContextOf(RumViewType.FOREGROUND, fakePreviousSessionId)
        val mockPreviousPipeline = mock<Pipeline<Double>>()
        val mockNextPipeline = mock<Pipeline<Double>>()
        val mockPipelinesFactory: PipelineFactory = mock()
        whenever(mockPipelinesFactory.create(any()))
            .thenReturn(listOf(mockPreviousPipeline), listOf(mockNextPipeline))
        val collector = DefaultTimeseriesCollector(
            internalLogger = mockInternalLogger,
            pipelinesFactory = mockPipelinesFactory,
            scheduledExecutorService = mockExecutor
        )
        collector.startInForeground()
        collector.startSession(sessionId = fakePreviousSessionId)
        collector.onStopped()

        // When — the session is stopped and renewed while the background transition is still pending,
        // replacing collector.pipelines before the delayed suspend runs
        collector.onSessionStop(fakePreviousSessionId)
        collector.startSession(sessionId = fakeNextSessionId)
        runScheduledSuspend()

        // Then — the pending suspend flushes the outgoing session's own pipelines with its own context
        // (once from onSessionStop, once from the pending suspend flushing its pipelines snapshot),
        // never the new session's pipelines tagged with the outgoing context
        verify(mockPreviousPipeline, times(2)).flush(fakePreviousContext)
        verify(mockNextPipeline, never()).flush(any())
    }

    @Test
    fun `M flush with last foreground context W onSessionStop() { stopped, suspend pending }`(forge: Forge) {
        // Given
        val fakeSample = forge.getForgery<DataPoint<Double>>()
        whenever(mockReaderA.read()) doReturn fakeSample
        whenever(mockEventFactoryA.create(any(), any(), any())) doReturn JsonObject()
        testedTimeseriesCollector.startSession()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        testedTimeseriesCollector.onStopped()

        // When
        testedTimeseriesCollector.onSessionStop(fakeRumContext.sessionId)

        // Then
        verify(mockEventFactoryA).create(
            eq(mockDatadogContext),
            eq(fakeRumContextOf(RumViewType.FOREGROUND)),
            eq(listOf(fakeSample))
        )
    }

    @Test
    fun `M schedule suspend but not flush yet W onStopped()`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.startSession()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()

        // When
        testedTimeseriesCollector.onStopped()

        // Then
        verify(mockExecutor)
            .schedule(
                any<Runnable>(),
                eq(DefaultTimeseriesCollector.BACKGROUND_TRANSITION_DELAY),
                eq(TimeUnit.MILLISECONDS)
            )
        verifyNoInteractions(mockEventFactoryA)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M not flush twice W pending suspend fires { session already stopped }`() {
        // Given
        val mockBuffer = mock<Buffer<Double>>()
        testedTimeseriesCollector = createTimeseriesWithBuffer(mockBuffer)
        testedTimeseriesCollector.startInForeground()
        testedTimeseriesCollector.startSession()
        testedTimeseriesCollector.onStopped()
        testedTimeseriesCollector.onSessionStop(fakeRumContext.sessionId)

        // When
        runScheduledSuspend()

        // Then
        verify(mockBuffer).drain()
    }

    @Test
    fun `M not invoke pending suspend flush W suspend fires { onSessionStop already closed the gate }`() {
        // Given
        val mockPipeline = mock<Pipeline<Double>>()
        val testedCollector = createTimeseries(listOf(mockPipeline))
        testedCollector.startInForeground()
        testedCollector.startSession()
        testedCollector.onStopped()

        // When — onSessionStop closes the gate (sessionActive: true -> false) right away, so by the
        // time the debounced suspend runs, setForeground(false) is no longer an open -> closed
        // transition and Gate.setForeground's onUpdated callback is never invoked
        testedCollector.onSessionStop(fakeRumContext.sessionId)
        verify(mockPipeline, times(1)).flush(fakeRumContextOf(RumViewType.FOREGROUND))
        runScheduledSuspend()

        // Then
        verify(mockPipeline, times(1)).flush(fakeRumContextOf(RumViewType.FOREGROUND))
    }

    @Test
    fun `M keep collection running W onStarted() + sample tick { stopped transition pending }`(forge: Forge) {
        // Given
        whenever(mockReaderA.read()) doReturn forge.getForgery<DataPoint<Double>>()
        testedTimeseriesCollector.startSession()
        val sampleRunnable = captureScheduledRunnableForInterval(fakeIntervalAMs)
        testedTimeseriesCollector.onStopped()
        val suspendRunnable = captureScheduledSuspendRunnable()

        // When
        testedTimeseriesCollector.onStarted()
        suspendRunnable.run()
        sampleRunnable.run()

        // Then — the stale suspendRunnable is not explicitly cancelled, it just no-ops on a stale generation
        verify(mockReaderA).read()
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M resume scheduling W onStarted() { after stopped }`() {
        // Given
        testedTimeseriesCollector.startSession()
        testedTimeseriesCollector.onStopped()
        runScheduledSuspend()
        captureScheduledRunnableForInterval(fakeIntervalAMs).run()
        verify(mockExecutor, times(1))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))

        // When
        testedTimeseriesCollector.onStarted()

        // Then
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalAMs), eq(TimeUnit.MILLISECONDS))
        verify(mockExecutor, times(2))
            .schedule(any<Runnable>(), eq(fakeIntervalBMs), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `M not sample nor reschedule W stale tick fires { new generation is running }`() {
        // Given
        testedTimeseriesCollector.startSession()
        testedTimeseriesCollector.onStopped()
        runScheduledSuspend()
        val staleRunnableB = captureScheduledRunnableForInterval(fakeIntervalBMs)
        testedTimeseriesCollector.onStarted()

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
            .schedule(
                captor.capture(),
                eq(DefaultTimeseriesCollector.BACKGROUND_TRANSITION_DELAY),
                eq(TimeUnit.MILLISECONDS)
            )
        return captor.lastValue
    }

    private fun captureScheduledRunnableForInterval(intervalMs: Long): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(captor.capture(), eq(intervalMs), eq(TimeUnit.MILLISECONDS))
        return captor.firstValue
    }
}
