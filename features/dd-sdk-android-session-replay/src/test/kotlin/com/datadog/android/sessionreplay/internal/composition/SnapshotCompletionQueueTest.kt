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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class SnapshotCompletionQueueTest {

    @Test
    fun `M process completed capture W consume() { generation remains valid }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeCapture = fakeFixture.aCapture()

        // When
        fakeFixture.testedQueue.consume(fakeCapture)
        fakeFixture.runQueuedTask()

        // Then
        verify(fakeFixture.mockProcessor).process(fakeCapture)
    }

    @Test
    fun `M drop completed capture W consume() { generation expires before handoff }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeCapture = fakeFixture.aCapture()

        // When
        fakeFixture.fakeClock.nowNs = fakeCapture.generation.deadlineNs
        fakeFixture.testedQueue.consume(fakeCapture)

        // Then
        verifyNoInteractions(fakeFixture.mockExecutorService, fakeFixture.mockProcessor)
    }

    @Test
    fun `M drop queued capture W runQueuedTask() { generation expires before processing }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeCapture = fakeFixture.aCapture()
        fakeFixture.testedQueue.consume(fakeCapture)

        // When
        fakeFixture.fakeClock.nowNs = fakeCapture.generation.deadlineNs
        fakeFixture.runQueuedTask()

        // Then
        verifyNoInteractions(fakeFixture.mockProcessor)
    }

    @Test
    fun `M preserve capture order W consume() { multiple generations are handed off }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeFirstCapture = fakeFixture.aCapture(generationId = 1L)
        val fakeSecondCapture = fakeFixture.aCapture(generationId = 2L)

        // When
        fakeFixture.testedQueue.consume(fakeFirstCapture)
        fakeFixture.testedQueue.consume(fakeSecondCapture)
        fakeFixture.runQueuedTask()

        // Then
        inOrder(fakeFixture.mockProcessor) {
            verify(fakeFixture.mockProcessor).process(fakeFirstCapture)
            verify(fakeFixture.mockProcessor).process(fakeSecondCapture)
        }
        verify(fakeFixture.mockExecutorService).execute(isA())
    }

    @Test
    fun `M cancel completion executor W stop()`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)

        // When
        fakeFixture.testedQueue.stop()

        // Then
        verify(fakeFixture.mockExecutorService).shutdownNow()
    }

    @Test
    fun `M reject handoff W consume() { completion queue stopped }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        fakeFixture.testedQueue.stop()

        // When
        fakeFixture.testedQueue.consume(fakeFixture.aCapture())

        // Then
        verify(fakeFixture.mockExecutorService, never()).execute(any())
    }

    @Test
    fun `M expire refused capture W consume { queue already stopped }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeCapture = fakeFixture.aCapture()
        fakeFixture.testedQueue.stop()

        // When
        fakeFixture.testedQueue.consume(fakeCapture)

        // Then
        assertThat(fakeCapture.generation.isActive())
            .describedAs("a capture the queue refuses must not stay active")
            .isFalse()
        verifyNoInteractions(fakeFixture.mockProcessor)
    }

    @Test
    fun `M drop the oldest capture W consume { queue saturated }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = QueueFixture(forge, maxQueuedCaptures = fakeMaxQueuedCaptures)
        val fakeCaptures = (0..fakeMaxQueuedCaptures).map { fakeFixture.aCapture(generationId = it + 1L) }

        // When
        fakeCaptures.forEach(fakeFixture.testedQueue::consume)

        // Then
        assertThat(fakeCaptures.first().generation.isActive()).isFalse()
        assertThat(fakeCaptures.drop(1)).allMatch { it.generation.isActive() }
        fakeFixture.mockInternalLogger.verifyLog(
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
    fun `M process only the retained captures W runQueuedTask() { queue saturated }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = QueueFixture(forge, maxQueuedCaptures = fakeMaxQueuedCaptures)
        val fakeCaptures = (0..fakeMaxQueuedCaptures).map { fakeFixture.aCapture(generationId = it + 1L) }

        // When
        fakeCaptures.forEach(fakeFixture.testedQueue::consume)
        fakeFixture.runQueuedTask()

        // Then
        verify(fakeFixture.mockProcessor, never()).process(fakeCaptures.first())
        fakeCaptures.drop(1).forEach { verify(fakeFixture.mockProcessor).process(it) }
    }

    @Test
    fun `M keep one capture W consume { capacity configured below one }`(
        @IntForgery(min = -5, max = 1) fakeInvalidMaxQueuedCaptures: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = QueueFixture(forge, maxQueuedCaptures = fakeInvalidMaxQueuedCaptures)
        val fakeFirstCapture = fakeFixture.aCapture(generationId = 1L)
        val fakeSecondCapture = fakeFixture.aCapture(generationId = 2L)

        // When
        fakeFixture.testedQueue.consume(fakeFirstCapture)
        fakeFixture.testedQueue.consume(fakeSecondCapture)
        fakeFixture.runQueuedTask()

        // Then
        assertThat(fakeFirstCapture.generation.isActive()).isFalse()
        verify(fakeFixture.mockProcessor, never()).process(fakeFirstCapture)
        verify(fakeFixture.mockProcessor).process(fakeSecondCapture)
    }

    @Test
    fun `M report the dropped total W stop { captures were dropped }`(
        @IntForgery(min = 1, max = 4) fakeMaxQueuedCaptures: Int,
        @IntForgery(min = 1, max = 5) fakeDroppedCount: Int,
        forge: Forge
    ) {
        // Given
        val fakeFixture = QueueFixture(forge, maxQueuedCaptures = fakeMaxQueuedCaptures)
        val fakeCaptureCount = fakeMaxQueuedCaptures + fakeDroppedCount
        repeat(fakeCaptureCount) { fakeFixture.testedQueue.consume(fakeFixture.aCapture(generationId = it + 1L)) }

        // When
        fakeFixture.testedQueue.stop()

        // Then
        fakeFixture.mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            SnapshotCompletionQueue.DROPPED_CAPTURES_ON_STOP_MESSAGE,
            additionalProperties = mapOf(
                SnapshotCompletionQueue.DROPPED_CAPTURE_COUNT_PROPERTY to fakeDroppedCount.toLong()
            )
        )
    }

    @Test
    fun `M report an error and keep draining W consume { processor throws }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeThrowingCapture = fakeFixture.aCapture(generationId = 1L)
        val fakeFailure = IllegalStateException("boom")
        doThrow(fakeFailure).whenever(fakeFixture.mockProcessor).process(fakeThrowingCapture)
        val fakeFollowingCapture = fakeFixture.aCapture(generationId = 2L)

        // When
        fakeFixture.testedQueue.consume(fakeThrowingCapture)
        fakeFixture.testedQueue.consume(fakeFollowingCapture)
        fakeFixture.runQueuedTask()

        // Then
        verify(fakeFixture.mockProcessor).process(fakeFollowingCapture)
        fakeFixture.mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Composition snapshot completion processing threw an exception",
            fakeFailure
        )
    }

    @Test
    fun `M expire the in-flight capture W runQueuedTask() { stop, called during processing }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        val fakeCapture = fakeFixture.aCapture()
        doAnswer { fakeFixture.testedQueue.stop() }.whenever(fakeFixture.mockProcessor).process(fakeCapture)

        // When
        fakeFixture.testedQueue.consume(fakeCapture)
        fakeFixture.runQueuedTask()

        // Then
        assertThat(fakeCapture.generation.isActive())
            .describedAs("stop() must expire the capture that was mid-processing when it was called")
            .isFalse()
    }

    @Test
    fun `M not report any drop W stop { queue never saturated }`(forge: Forge) {
        // Given
        val fakeFixture = QueueFixture(forge)
        fakeFixture.testedQueue.consume(fakeFixture.aCapture())

        // When
        fakeFixture.testedQueue.stop()

        // Then
        verifyNoInteractions(fakeFixture.mockInternalLogger)
    }

    @Test
    fun `M write mapped snapshot W process() { acceptance is immediately before deadline }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeFixture = CompletionFixture(
            forge,
            fakeApplicationId,
            fakeSessionId,
            nowNs = fakeDeadlineNs - 1L,
            deadlineNs = fakeDeadlineNs
        )

        // When
        fakeFixture.testedProcessor.process(fakeFixture.fakeCapture)

        // Then
        val recordCaptor = argumentCaptor<EnrichedRecord>()
        verify(fakeFixture.mockRecordWriter).write(recordCaptor.capture(), isA())
        assertThat(recordCaptor.firstValue).isEqualTo(
            EnrichedRecord(
                fakeApplicationId,
                fakeSessionId,
                fakeFixture.fakeTree.scope.value,
                listOf(fakeFixture.mockRecord)
            )
        )
    }

    @Test
    fun `M drop mapped snapshot W process() { acceptance is exactly at deadline }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeFixture = CompletionFixture(
            forge,
            fakeApplicationId,
            fakeSessionId,
            nowNs = fakeDeadlineNs,
            deadlineNs = fakeDeadlineNs
        )

        // When
        fakeFixture.testedProcessor.process(fakeFixture.fakeCapture)

        // Then
        verifyNoInteractions(fakeFixture.mockRecordWriter)
    }

    @Test
    fun `M drop mapped snapshot W process() { acceptance is after deadline }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeDeadlineNs: Long,
        @LongForgery(min = 1L, max = 1_000L) fakeOverrunNs: Long,
        forge: Forge
    ) {
        // Given
        val fakeFixture = CompletionFixture(
            forge,
            fakeApplicationId,
            fakeSessionId,
            nowNs = fakeDeadlineNs + fakeOverrunNs,
            deadlineNs = fakeDeadlineNs
        )

        // When
        fakeFixture.testedProcessor.process(fakeFixture.fakeCapture)

        // Then
        verifyNoInteractions(fakeFixture.mockRecordWriter)
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
            rumContextProvider = mockRumContextProvider(fakeApplicationId, fakeSessionId, fakeOtherViewId),
            recordWriter = mockRecordWriter,
            internalLogger = mock(),
            wireMapper = mockWireMapper
        )

        // When
        testedProcessor.process(
            CompletedSnapshotCapture(generation(FakeClock(), deadlineNs = fakeDeadlineNs), fakeTree.snapshot)
        )

        // Then
        verifyNoInteractions(mockWireMapper, mockRecordWriter)
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
        val mockWireMapper = mock<CapturedTreeWireMapper>()
        whenever(mockWireMapper.mapFullSnapshot(fakeTree.snapshot))
            .thenReturn(CaptureWireMappingResult.Invalid(fakeFailures))
        val mockRecordWriter = mock<RecordWriter>()
        val mockInternalLogger = mock<InternalLogger>()
        val testedProcessor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = mockRumContextProvider(fakeApplicationId, fakeSessionId, fakeTree.scope.value),
            recordWriter = mockRecordWriter,
            internalLogger = mockInternalLogger,
            wireMapper = mockWireMapper
        )
        val fakeGeneration = generation(FakeClock(), deadlineNs = fakeDeadlineNs)

        // When
        testedProcessor.process(CompletedSnapshotCapture(fakeGeneration, fakeTree.snapshot))

        // Then
        verifyNoInteractions(mockRecordWriter)
        assertThat(fakeGeneration.isActive()).isFalse()
        val expectedCodes = fakeFailures.asSequence().map { it.code }.distinct().sorted().map(Enum<*>::name).toList()
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

    private inner class CompletionFixture(
        forge: Forge,
        applicationId: String,
        sessionId: String,
        nowNs: Long,
        deadlineNs: Long
    ) {
        val fakeTree = forge.aCompositionTestTree()
        val mockRecord: MobileSegment.MobileRecord.MobileFullSnapshotRecord = mock()
        val mockRecordWriter: RecordWriter = mock()
        private val mockWireMapper: CapturedTreeWireMapper = mock {
            on { mapFullSnapshot(fakeTree.snapshot) } doReturn CaptureWireMappingResult.Success(mockRecord)
        }
        private val fakeClock = FakeClock().apply { this.nowNs = nowNs }
        val fakeCapture = CompletedSnapshotCapture(generation(fakeClock, deadlineNs = deadlineNs), fakeTree.snapshot)
        val testedProcessor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = mockRumContextProvider(applicationId, sessionId, fakeTree.scope.value),
            recordWriter = mockRecordWriter,
            internalLogger = mock(),
            wireMapper = mockWireMapper
        )
    }

    private fun mockRumContextProvider(
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
