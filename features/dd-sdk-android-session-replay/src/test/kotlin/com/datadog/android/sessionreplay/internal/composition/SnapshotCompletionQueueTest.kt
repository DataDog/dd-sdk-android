/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotCompletionQueueTest {

    @Test
    fun `M process completed capture W generation remains valid`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)
        val fakeCapture = fixture.aCapture()

        // When
        fixture.testedQueue.consume(fakeCapture)
        fixture.runQueuedTask()

        // Then
        verify(fixture.mockProcessor).process(fakeCapture)
    }

    @Test
    fun `M drop completed capture W generation expires before handoff`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)
        val fakeCapture = fixture.aCapture()

        // When
        fixture.fakeClock.nowNs = fakeCapture.generation.deadlineNs
        fixture.testedQueue.consume(fakeCapture)

        // Then
        verify(fixture.mockExecutorService, never()).execute(any())
        verify(fixture.mockProcessor, never()).process(any())
    }

    @Test
    fun `M drop queued capture W generation expires before processing`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)
        val fakeCapture = fixture.aCapture()
        fixture.testedQueue.consume(fakeCapture)

        // When
        fixture.fakeClock.nowNs = fakeCapture.generation.deadlineNs
        fixture.runQueuedTask()

        // Then
        verify(fixture.mockProcessor, never()).process(any())
    }

    @Test
    fun `M preserve capture order W multiple generations are handed off`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)
        val fakeFirstCapture = fixture.aCapture(generationId = 1L)
        val fakeSecondCapture = fixture.aCapture(generationId = 2L)

        // When
        fixture.testedQueue.consume(fakeFirstCapture)
        fixture.testedQueue.consume(fakeSecondCapture)
        fixture.runQueuedTask()

        // Then
        inOrder(fixture.mockProcessor) {
            verify(fixture.mockProcessor).process(fakeFirstCapture)
            verify(fixture.mockProcessor).process(fakeSecondCapture)
        }
        verify(fixture.mockExecutorService).execute(any())
    }

    @Test
    fun `M reject handoff and cancel executor W stop`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)

        // When
        fixture.testedQueue.stop()
        fixture.testedQueue.consume(fixture.aCapture())

        // Then
        verify(fixture.mockExecutorService).shutdownNow()
        verify(fixture.mockExecutorService, never()).execute(any())
    }

    @Test
    fun `M expire refused capture W consume { queue already stopped }`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)
        val fakeCapture = fixture.aCapture()
        fixture.testedQueue.stop()

        // When
        fixture.testedQueue.consume(fakeCapture)

        // Then
        assertThat(fakeCapture.generation.isActive())
            .describedAs("a capture the queue refuses must not stay active")
            .isFalse()
        verify(fixture.mockProcessor, never()).process(any())
    }

    @Test
    fun `M drop the oldest capture W consume { queue saturated }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fixture = QueueFixture(forge, maxQueuedCaptures = fakeMaxQueuedCaptures)
        val fakeCaptures = (0..fakeMaxQueuedCaptures).map { fixture.aCapture(generationId = it + 1L) }

        // When
        fakeCaptures.forEach(fixture.testedQueue::consume)

        // Then
        assertThat(fakeCaptures.first().generation.isActive()).isFalse()
        assertThat(fakeCaptures.drop(1)).allMatch { it.generation.isActive() }
        fixture.mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            SnapshotCompletionQueue.QUEUE_SATURATED_MESSAGE,
            onlyOnce = true,
            additionalProperties = mapOf(
                SnapshotCompletionQueue.MAX_QUEUED_CAPTURES_PROPERTY to fakeMaxQueuedCaptures
            )
        )
    }

    @Test
    fun `M process only the retained captures W drain { queue saturated }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fixture = QueueFixture(forge, maxQueuedCaptures = fakeMaxQueuedCaptures)
        val fakeCaptures = (0..fakeMaxQueuedCaptures).map { fixture.aCapture(generationId = it + 1L) }

        // When
        fakeCaptures.forEach(fixture.testedQueue::consume)
        fixture.runQueuedTask()

        // Then
        verify(fixture.mockProcessor, never()).process(fakeCaptures.first())
        fakeCaptures.drop(1).forEach { verify(fixture.mockProcessor).process(it) }
    }

    @Test
    fun `M keep one capture W consume { capacity configured below one }`(
        @IntForgery(min = -5, max = 1) fakeInvalidMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fixture = QueueFixture(forge, maxQueuedCaptures = fakeInvalidMaxQueuedCaptures)
        val fakeFirstCapture = fixture.aCapture(generationId = 1L)
        val fakeSecondCapture = fixture.aCapture(generationId = 2L)

        // When
        fixture.testedQueue.consume(fakeFirstCapture)
        fixture.testedQueue.consume(fakeSecondCapture)
        fixture.runQueuedTask()

        // Then
        assertThat(fakeFirstCapture.generation.isActive()).isFalse()
        verify(fixture.mockProcessor, never()).process(fakeFirstCapture)
        verify(fixture.mockProcessor).process(fakeSecondCapture)
    }

    @Test
    fun `M report the dropped total W stop { captures were dropped }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        @IntForgery(min = 1, max = 5) fakeDroppedCount: Int,
        forge: Forge
    ) {
        // Given
        val fixture = QueueFixture(forge, maxQueuedCaptures = fakeMaxQueuedCaptures)
        val fakeCaptureCount = fakeMaxQueuedCaptures + fakeDroppedCount
        repeat(fakeCaptureCount) { fixture.testedQueue.consume(fixture.aCapture(generationId = it + 1L)) }

        // When
        fixture.testedQueue.stop()

        // Then
        fixture.mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            SnapshotCompletionQueue.DROPPED_CAPTURES_ON_STOP_MESSAGE,
            additionalProperties = mapOf(
                SnapshotCompletionQueue.DROPPED_CAPTURE_COUNT_PROPERTY to fakeDroppedCount.toLong()
            )
        )
    }

    @Test
    fun `M not report any drop W stop { queue never saturated }`(forge: Forge) {
        // Given
        val fixture = QueueFixture(forge)
        fixture.testedQueue.consume(fixture.aCapture())

        // When
        fixture.testedQueue.stop()

        // Then
        verifyNoInteractions(fixture.mockInternalLogger)
    }

    @Test
    fun `M write mapped snapshot W acceptance is immediately before deadline`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTree = forge.aCompositionTestTree()
        val mockRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val stubWireMapper = mock<CapturedTreeWireMapper>()
        whenever(stubWireMapper.mapFullSnapshot(fakeTree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(mockRecord))
        val mockRecordWriter = mock<RecordWriter>()
        val testedProcessor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = stubRumContextProvider(fakeApplicationId, fakeSessionId, fakeTree.scope.value),
            recordWriter = mockRecordWriter,
            internalLogger = mock(),
            wireMapper = stubWireMapper
        )
        val fakeClock = FakeClock().apply { nowNs = fakeDeadlineNs - 1L }

        // When
        testedProcessor.process(
            CompletedSnapshotCapture(generation(fakeClock, deadlineNs = fakeDeadlineNs), fakeTree.snapshot)
        )

        // Then
        val recordCaptor = argumentCaptor<EnrichedRecord>()
        verify(mockRecordWriter).write(recordCaptor.capture(), any())
        assertThat(recordCaptor.firstValue).isEqualTo(
            EnrichedRecord(fakeApplicationId, fakeSessionId, fakeTree.scope.value, listOf(mockRecord))
        )
    }

    @Test
    fun `M drop mapped snapshot W acceptance is exactly at deadline`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        forge: Forge
    ) {
        assertSnapshotDroppedAt(forge, fakeDeadlineNs, fakeDeadlineNs, fakeApplicationId, fakeSessionId)
    }

    @Test
    fun `M drop mapped snapshot W acceptance is after deadline`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        @LongForgery(min = 1L, max = 1_000L) fakeOverrunNs: Long,
        forge: Forge
    ) {
        assertSnapshotDroppedAt(
            forge,
            nowNs = fakeDeadlineNs + fakeOverrunNs,
            deadlineNs = fakeDeadlineNs,
            applicationId = fakeApplicationId,
            sessionId = fakeSessionId
        )
    }

    @Test
    fun `M drop snapshot W process completed capture { RUM view changed }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeOtherViewId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTree = forge.aCompositionTestTree()
        val mockRecordWriter = mock<RecordWriter>()
        val mockWireMapper = mock<CapturedTreeWireMapper>()
        val testedProcessor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = stubRumContextProvider(fakeApplicationId, fakeSessionId, fakeOtherViewId),
            recordWriter = mockRecordWriter,
            internalLogger = mock(),
            wireMapper = mockWireMapper
        )

        // When
        testedProcessor.process(
            CompletedSnapshotCapture(generation(FakeClock(), deadlineNs = fakeDeadlineNs), fakeTree.snapshot)
        )

        // Then
        verify(mockWireMapper, never()).mapFullSnapshot(any())
        verify(mockRecordWriter, never()).write(any(), any())
    }

    @Test
    fun `M report bounded error codes W process completed capture { invalid snapshot }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeTree = forge.aCompositionTestTree()
        val fakeFailures = forge.aList(size = forge.anInt(min = 2, max = 5)) {
            CaptureValidationFailure(
                code = aValueFrom(CaptureValidationErrorCode::class.java),
                identity = fakeTree.wireframeIdentity,
                detail = anAlphabeticalString()
            )
        }
        val stubWireMapper = mock<CapturedTreeWireMapper>()
        whenever(stubWireMapper.mapFullSnapshot(fakeTree.snapshot))
            .thenReturn(CaptureWireMappingResult.Invalid(fakeFailures))
        val mockRecordWriter = mock<RecordWriter>()
        val mockInternalLogger = mock<InternalLogger>()
        val testedProcessor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = stubRumContextProvider(fakeApplicationId, fakeSessionId, fakeTree.scope.value),
            recordWriter = mockRecordWriter,
            internalLogger = mockInternalLogger,
            wireMapper = stubWireMapper
        )
        val fakeGeneration = generation(FakeClock(), deadlineNs = fakeDeadlineNs)

        // When
        testedProcessor.process(CompletedSnapshotCapture(fakeGeneration, fakeTree.snapshot))

        // Then
        verify(mockRecordWriter, never()).write(any(), any())
        assertThat(fakeGeneration.isActive()).isFalse()
        val expectedCodes = fakeFailures.map { it.code }.distinct().sorted().map(Enum<*>::name)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            DefaultSnapshotCompletionProcessor.INVALID_SNAPSHOT_MESSAGE,
            additionalProperties = mapOf(
                DefaultSnapshotCompletionProcessor.VALIDATION_ERROR_CODES_PROPERTY to expectedCodes,
                DefaultSnapshotCompletionProcessor.VALIDATION_FAILURE_COUNT_PROPERTY to fakeFailures.size
            )
        )
    }

    private fun assertSnapshotDroppedAt(
        forge: Forge,
        nowNs: Long,
        deadlineNs: Long,
        applicationId: String,
        sessionId: String
    ) {
        val fakeTree = forge.aCompositionTestTree()
        val mockRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val stubWireMapper = mock<CapturedTreeWireMapper>()
        whenever(stubWireMapper.mapFullSnapshot(fakeTree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(mockRecord))
        val mockRecordWriter = mock<RecordWriter>()
        val testedProcessor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = stubRumContextProvider(applicationId, sessionId, fakeTree.scope.value),
            recordWriter = mockRecordWriter,
            internalLogger = mock(),
            wireMapper = stubWireMapper
        )
        val fakeClock = FakeClock().apply { this.nowNs = nowNs }

        testedProcessor.process(
            CompletedSnapshotCapture(generation(fakeClock, deadlineNs = deadlineNs), fakeTree.snapshot)
        )

        verify(mockRecordWriter, never()).write(any(), any())
    }

    private fun stubRumContextProvider(
        applicationId: String,
        sessionId: String,
        viewId: String
    ): RumContextProvider = mock<RumContextProvider>().apply {
        whenever(getRumContext()).thenReturn(SessionReplayRumContext(applicationId, sessionId, viewId))
    }

    private class QueueFixture(
        private val forge: Forge,
        maxQueuedCaptures: Int = SnapshotCompletionQueue.DEFAULT_MAX_QUEUED_CAPTURES
    ) {
        val mockExecutorService = mock<ExecutorService>()
        val mockProcessor = mock<SnapshotCompletionProcessor>()
        val mockInternalLogger = mock<InternalLogger>()
        val fakeClock = FakeClock()
        val testedQueue = SnapshotCompletionQueue(
            executorService = mockExecutorService,
            processor = mockProcessor,
            internalLogger = mockInternalLogger,
            maxQueuedCaptures = maxQueuedCaptures
        )

        fun aCapture(generationId: Long = 1L): CompletedSnapshotCapture = CompletedSnapshotCapture(
            generation(fakeClock, generationId, fakeClock.nowNs + forge.aLong(min = 1L, max = 1_000_000L)),
            forge.aCompositionTestTree().snapshot
        )

        fun runQueuedTask() {
            val captor = argumentCaptor<Runnable>()
            verify(mockExecutorService, times(1)).execute(captor.capture())
            captor.firstValue.run()
        }
    }

    private companion object {
        fun generation(
            clock: CaptureTimeProvider,
            id: Long = 1L,
            deadlineNs: Long
        ) = CaptureGenerationContext(id, 0L, deadlineNs, clock)
    }

    private class FakeClock : CaptureTimeProvider {
        var nowNs = 0L

        override fun elapsedRealtimeNanos(): Long = nowNs
    }
}
