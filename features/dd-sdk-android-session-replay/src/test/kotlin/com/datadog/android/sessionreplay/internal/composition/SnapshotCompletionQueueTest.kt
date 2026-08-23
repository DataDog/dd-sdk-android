/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
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
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotCompletionQueueTest {

    @Test
    fun `M process completed capture W generation remains valid`() {
        // Given
        val fixture = QueueFixture()
        val capture = fixture.capture(expiresAtNs = 10)

        // When
        fixture.queue.consume(capture)
        fixture.runQueuedTask()

        // Then
        verify(fixture.processor).process(capture)
    }

    @Test
    fun `M drop completed capture W generation expires before handoff`() {
        // Given
        val fixture = QueueFixture()
        fixture.clock.nowNs = 10

        // When
        fixture.queue.consume(fixture.capture(expiresAtNs = 10))

        // Then
        verify(fixture.executorService, never()).execute(any())
        verify(fixture.processor, never()).process(any())
    }

    @Test
    fun `M drop queued capture W generation expires before processing`() {
        // Given
        val fixture = QueueFixture()
        fixture.queue.consume(fixture.capture(expiresAtNs = 10))

        // When
        fixture.clock.nowNs = 10
        fixture.runQueuedTask()

        // Then
        verify(fixture.processor, never()).process(any())
    }

    @Test
    fun `M preserve capture order W multiple generations are handed off`() {
        // Given
        val fixture = QueueFixture()
        val first = fixture.capture(generationId = 1, expiresAtNs = 10)
        val second = fixture.capture(generationId = 2, expiresAtNs = 10)

        // When
        fixture.queue.consume(first)
        fixture.queue.consume(second)
        fixture.runQueuedTask()

        // Then
        inOrder(fixture.processor) {
            verify(fixture.processor).process(first)
            verify(fixture.processor).process(second)
        }
        verify(fixture.executorService).execute(any())
    }

    @Test
    fun `M reject handoff and cancel executor W stop`() {
        // Given
        val fixture = QueueFixture()

        // When
        fixture.queue.stop()
        fixture.queue.consume(fixture.capture(expiresAtNs = 10))

        // Then
        verify(fixture.executorService).shutdownNow()
        verify(fixture.executorService, never()).execute(any())
    }

    @Test
    fun `M write mapped snapshot W acceptance is immediately before deadline`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val record = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val wireMapper = mock<CapturedTreeWireMapper>()
        whenever(wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(record))
        val writer = mock<RecordWriter>()
        val rumContext = SessionReplayRumContext(
            applicationId = fakeApplicationId,
            sessionId = fakeSessionId,
            viewId = tree.scope.value
        )
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(rumContext)
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = writer,
            internalLogger = mock(),
            wireMapper = wireMapper
        )
        val clock = FakeClock().apply { nowNs = 9L }

        // When
        processor.process(CompletedSnapshotCapture(generation(clock, deadlineNs = 10L), tree.snapshot))

        // Then: this is the first snapshot for this view, so it must open with the Meta (viewport
        // size) and Focus records a player following this rrweb-derived protocol requires before
        // any wireframe content - see DefaultSnapshotCompletionProcessor's own doc comment.
        val recordCaptor = argumentCaptor<EnrichedRecord>()
        verify(writer).write(recordCaptor.capture(), any())
        assertThat(recordCaptor.firstValue).isEqualTo(
            EnrichedRecord(
                fakeApplicationId,
                fakeSessionId,
                tree.scope.value,
                listOf(
                    MobileSegment.MobileRecord.MetaRecord(
                        timestamp = tree.snapshot.timestamp,
                        data = MobileSegment.Data1(width = tree.root.bounds.width, height = tree.root.bounds.height)
                    ),
                    MobileSegment.MobileRecord.FocusRecord(
                        timestamp = tree.snapshot.timestamp,
                        data = MobileSegment.Data2(hasFocus = true)
                    ),
                    record
                )
            )
        )
    }

    @Test
    fun `M drain the resource data queue W processing a capture`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given: any resource bytes this generation's pixel captures resolved were already
        // queued upstream, in PixelFallbackSnapshotProcessor/ResourceResolver, well before this
        // processor ever sees the capture - nothing actually persists or uploads them, though,
        // until something calls DataQueueHandler.tryToConsumeItems(). Verified regardless of the
        // wire-mapping outcome below (Success here), since resource queueing already happened
        // independently of it.
        val tree = compositionTestTree()
        val wireMapper = mock<CapturedTreeWireMapper>()
        whenever(wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(mock()))
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(fakeApplicationId, fakeSessionId, tree.scope.value)
        )
        val resourceDataQueueHandler = mock<DataQueueHandler>()
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = mock(),
            internalLogger = mock(),
            wireMapper = wireMapper,
            resourceDataQueueHandler = resourceDataQueueHandler
        )
        val clock = FakeClock().apply { nowNs = 9L }

        // When
        processor.process(CompletedSnapshotCapture(generation(clock, deadlineNs = 10L), tree.snapshot))

        // Then
        verify(resourceDataQueueHandler).tryToConsumeItems()
    }

    @Test
    fun `M drain the resource data queue W the rum context is invalid`() {
        // Given: even when this capture's own generation gets dropped outright (an early-return
        // path, before any wire-mapping is even attempted), any resources it already queued must
        // still be drained - they were spent regardless of what happens to this specific capture.
        val tree = compositionTestTree()
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())
        val resourceDataQueueHandler = mock<DataQueueHandler>()
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = mock(),
            internalLogger = mock(),
            resourceDataQueueHandler = resourceDataQueueHandler
        )
        val clock = FakeClock().apply { nowNs = 9L }

        // When
        processor.process(CompletedSnapshotCapture(generation(clock, deadlineNs = 10L), tree.snapshot))

        // Then
        verify(resourceDataQueueHandler).tryToConsumeItems()
    }

    @Test
    fun `M write only the mapped snapshot W second capture for the same view`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val firstRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val secondRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val wireMapper = mock<CapturedTreeWireMapper>()
        whenever(wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(firstRecord))
            .thenReturn(CaptureWireMappingResult.Success(secondRecord))
        val writer = mock<RecordWriter>()
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(fakeApplicationId, fakeSessionId, tree.scope.value)
        )
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = writer,
            internalLogger = mock(),
            wireMapper = wireMapper
        )
        val clock = FakeClock().apply { nowNs = 9L }
        processor.process(CompletedSnapshotCapture(generation(clock, deadlineNs = 10L), tree.snapshot))

        // When: a second capture completes for the exact same RUM view.
        processor.process(CompletedSnapshotCapture(generation(clock, id = 2L, deadlineNs = 10L), tree.snapshot))

        // Then: no repeated Meta/Focus records - the view never changed.
        val recordCaptor = argumentCaptor<EnrichedRecord>()
        verify(writer, times(2)).write(recordCaptor.capture(), any())
        assertThat(recordCaptor.secondValue).isEqualTo(
            EnrichedRecord(fakeApplicationId, fakeSessionId, tree.scope.value, listOf(secondRecord))
        )
    }

    @Test
    fun `M write a ViewEndRecord for the previous view W a new view's snapshot completes`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeOtherViewId: String
    ) {
        // Given: a first snapshot completes for "rum-view" ...
        val tree = compositionTestTree()
        val otherTree = compositionTestTree(scopeValue = fakeOtherViewId)
        val record = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val otherRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val wireMapper = mock<CapturedTreeWireMapper>()
        whenever(wireMapper.mapFullSnapshot(tree.snapshot)).thenReturn(CaptureWireMappingResult.Success(record))
        whenever(wireMapper.mapFullSnapshot(otherTree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(otherRecord))
        val writer = mock<RecordWriter>()
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(fakeApplicationId, fakeSessionId, tree.scope.value)
        )
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = writer,
            internalLogger = mock(),
            wireMapper = wireMapper
        )
        val clock = FakeClock().apply { nowNs = 9L }
        processor.process(CompletedSnapshotCapture(generation(clock, deadlineNs = 10L), tree.snapshot))

        // When: ... then a new view ("fakeOtherViewId") completes its own first snapshot.
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(fakeApplicationId, fakeSessionId, fakeOtherViewId)
        )
        processor.process(CompletedSnapshotCapture(generation(clock, id = 2L, deadlineNs = 10L), otherTree.snapshot))

        // Then: the previous view is closed out, in its own record, before the new view opens.
        val recordCaptor = argumentCaptor<EnrichedRecord>()
        verify(writer, times(3)).write(recordCaptor.capture(), any())
        assertThat(recordCaptor.secondValue).isEqualTo(
            EnrichedRecord(
                fakeApplicationId,
                fakeSessionId,
                tree.scope.value,
                listOf(MobileSegment.MobileRecord.ViewEndRecord(otherTree.snapshot.timestamp))
            )
        )
    }

    @Test
    fun `M drop mapped snapshot W acceptance is exactly at deadline`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        assertSnapshotDroppedAt(nowNs = 10L, applicationId = fakeApplicationId, sessionId = fakeSessionId)
    }

    @Test
    fun `M drop mapped snapshot W acceptance is after deadline`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        assertSnapshotDroppedAt(nowNs = 11L, applicationId = fakeApplicationId, sessionId = fakeSessionId)
    }

    @Test
    fun `M drop snapshot W process completed capture { RUM view changed }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeOtherViewId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val writer = mock<RecordWriter>()
        val wireMapper = mock<CapturedTreeWireMapper>()
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(fakeApplicationId, fakeSessionId, fakeOtherViewId)
        )
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = writer,
            internalLogger = mock(),
            wireMapper = wireMapper
        )

        // When
        processor.process(
            CompletedSnapshotCapture(generation(FakeClock(), deadlineNs = 10L), tree.snapshot)
        )

        // Then
        verify(wireMapper, never()).mapFullSnapshot(any())
        verify(writer, never()).write(any(), any())
    }

    private class QueueFixture {
        val executorService = mock<ExecutorService>()
        val processor = mock<SnapshotCompletionProcessor>()
        val clock = FakeClock()
        val queue = SnapshotCompletionQueue(
            executorService = executorService,
            processor = processor,
            internalLogger = mock<InternalLogger>()
        )

        fun capture(
            generationId: Long = 1,
            expiresAtNs: Long
        ): CompletedSnapshotCapture = CompletedSnapshotCapture(
            generation(clock, generationId, expiresAtNs),
            compositionTestTree().snapshot
        )

        fun runQueuedTask() {
            val captor = argumentCaptor<Runnable>()
            verify(executorService).execute(captor.capture())
            captor.firstValue.run()
        }
    }

    private fun assertSnapshotDroppedAt(nowNs: Long, applicationId: String, sessionId: String) {
        val tree = compositionTestTree()
        val record = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val wireMapper = mock<CapturedTreeWireMapper>()
        whenever(wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(record))
        val writer = mock<RecordWriter>()
        val rumContextProvider = mock<RumContextProvider>()
        whenever(rumContextProvider.getRumContext()).thenReturn(
            SessionReplayRumContext(applicationId, sessionId, tree.scope.value)
        )
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = writer,
            internalLogger = mock(),
            wireMapper = wireMapper
        )
        val clock = FakeClock().apply { this.nowNs = nowNs }

        processor.process(
            CompletedSnapshotCapture(generation(clock, deadlineNs = 10L), tree.snapshot)
        )

        verify(writer, never()).write(any(), any())
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
