/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultSnapshotCompletionProcessorTest {

    @Test
    fun `M emit a mutation on the second cycle W process { nothing changed }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        val fullRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val incrementalRecord = mock<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>()
        whenever(fixture.wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(fullRecord))
        whenever(fixture.wireMapper.mapMutation(any(), eq(tree.snapshot)))
            .thenReturn(CaptureWireMappingResult.Success(incrementalRecord))

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then
        verify(fixture.wireMapper).mapFullSnapshot(tree.snapshot)
        verify(fixture.wireMapper).mapMutation(any(), eq(tree.snapshot))
        verify(fixture.writer).write(
            eq(
                EnrichedRecord(
                    fakeApplicationId,
                    fakeSessionId,
                    tree.scope.value,
                    openingRecords(tree.snapshot) + fullRecord
                )
            ),
            any()
        )
        verify(fixture.writer).write(
            eq(EnrichedRecord(fakeApplicationId, fakeSessionId, tree.scope.value, listOf(incrementalRecord))),
            any()
        )
    }

    @Test
    fun `M emit both mutation records W process { layer and wireframe content both changed }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        val layerMutationRecord = mock<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>()
        val wireframeMutationRecord = mock<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>()
        whenever(fixture.wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(mock()))
        whenever(fixture.wireMapper.mapMutation(any(), eq(tree.snapshot)))
            .thenReturn(CaptureWireMappingResult.Success(layerMutationRecord))
        whenever(fixture.wireMapper.mapWireframeMutation(eq(tree.snapshot), any()))
            .thenReturn(wireframeMutationRecord)

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then: both the layer-structure mutation and the independently-computed wireframe-content
        // mutation are written together, in the same cycle - neither one forces a full snapshot.
        verify(fixture.writer).write(
            eq(
                EnrichedRecord(
                    fakeApplicationId,
                    fakeSessionId,
                    tree.scope.value,
                    listOf(layerMutationRecord, wireframeMutationRecord)
                )
            ),
            any()
        )
    }

    @Test
    fun `M emit only the layer mutation W process { nothing changed at the wireframe level }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        val layerMutationRecord = mock<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>()
        whenever(fixture.wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(mock()))
        whenever(fixture.wireMapper.mapMutation(any(), eq(tree.snapshot)))
            .thenReturn(CaptureWireMappingResult.Success(layerMutationRecord))
        // mapWireframeMutation left unstubbed - a Mockito mock returns null by default, exactly
        // like the real implementation would when nothing changed at the wireframe level.

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then
        verify(fixture.writer).write(
            eq(EnrichedRecord(fakeApplicationId, fakeSessionId, tree.scope.value, listOf(layerMutationRecord))),
            any()
        )
    }

    @Test
    fun `M emit a full snapshot W process { new RUM view since last accepted }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val firstTree = compositionTestTree(scopeValue = "view-a")
        val secondTree = compositionTestTree(scopeValue = "view-b")
        val fixture = Fixture(firstTree.scope.value, fakeApplicationId, fakeSessionId)
        whenever(fixture.wireMapper.mapFullSnapshot(any()))
            .thenReturn(CaptureWireMappingResult.Success(mock()))

        // When
        fixture.processor.process(fixture.capture(firstTree.snapshot))
        fixture.setCurrentRumView(secondTree.scope.value, fakeApplicationId, fakeSessionId)
        fixture.processor.process(fixture.capture(secondTree.snapshot))

        // Then
        verify(fixture.wireMapper).mapFullSnapshot(firstTree.snapshot)
        verify(fixture.wireMapper).mapFullSnapshot(secondTree.snapshot)
        verify(fixture.wireMapper, never()).mapMutation(any(), any())
    }

    @Test
    fun `M emit a full snapshot W process { periodic checkpoint elapsed }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        whenever(fixture.wireMapper.mapFullSnapshot(any()))
            .thenReturn(CaptureWireMappingResult.Success(mock()))

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.timeProvider.elapsedNs += FULL_SNAPSHOT_INTERVAL_NS
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then
        verify(fixture.wireMapper, times(2)).mapFullSnapshot(tree.snapshot)
        verify(fixture.wireMapper, never()).mapMutation(any(), any())
    }

    @Test
    fun `M emit a full snapshot W process { orientation changed since last accepted }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        whenever(fixture.wireMapper.mapFullSnapshot(any()))
            .thenReturn(CaptureWireMappingResult.Success(mock()))

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.orientationProvider.orientation = fixture.orientationProvider.orientation + 1
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then
        verify(fixture.wireMapper, times(2)).mapFullSnapshot(tree.snapshot)
        verify(fixture.wireMapper, never()).mapMutation(any(), any())
    }

    @Test
    fun `M emit a viewport resize record ahead of the full snapshot W process { orientation changed }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given: mirrors legacy RecordedDataProcessor, which sends both a ViewportResizeData record
        // and a full snapshot on an orientation change - the composition pipeline already forced the
        // full snapshot, it was just missing this record.
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        val firstFullRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val secondFullRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        whenever(fixture.wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(
                CaptureWireMappingResult.Success(firstFullRecord),
                CaptureWireMappingResult.Success(secondFullRecord)
            )

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.orientationProvider.orientation = fixture.orientationProvider.orientation + 1
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then: no viewport record on the first (baseline) cycle - there is no prior orientation to
        // have changed from.
        verify(fixture.writer).write(
            eq(
                EnrichedRecord(
                    fakeApplicationId,
                    fakeSessionId,
                    tree.scope.value,
                    openingRecords(tree.snapshot) + firstFullRecord
                )
            ),
            any()
        )
        // Then: the second cycle's orientation change gets a ViewportResizeData record, sized from
        // the current snapshot's root bounds, ahead of the (still forced) full snapshot.
        val bounds = tree.snapshot.root?.bounds
        checkNotNull(bounds)
        verify(fixture.writer).write(
            eq(
                EnrichedRecord(
                    fakeApplicationId,
                    fakeSessionId,
                    tree.scope.value,
                    listOf(
                        MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
                            timestamp = tree.snapshot.timestamp,
                            data = MobileSegment.MobileIncrementalData.ViewportResizeData(
                                width = bounds.width,
                                height = bounds.height
                            )
                        ),
                        secondFullRecord
                    )
                )
            ),
            any()
        )
    }

    @Test
    fun `M retry as a full snapshot W process { computed mutation fails validation }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        val fullRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        whenever(fixture.wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(fullRecord))
        whenever(fixture.wireMapper.mapMutation(any(), eq(tree.snapshot)))
            .thenReturn(CaptureWireMappingResult.Invalid(emptyList()))

        // When
        fixture.processor.process(fixture.capture(tree.snapshot))
        fixture.processor.process(fixture.capture(tree.snapshot))

        // Then
        verify(fixture.writer).write(
            eq(
                EnrichedRecord(
                    fakeApplicationId,
                    fakeSessionId,
                    tree.scope.value,
                    openingRecords(tree.snapshot) + fullRecord
                )
            ),
            any()
        )
        verify(fixture.writer).write(
            eq(EnrichedRecord(fakeApplicationId, fakeSessionId, tree.scope.value, listOf(fullRecord))),
            any()
        )
    }

    @Test
    fun `M leave retained state untouched W process { generation fails to accept }`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val fullRecord = mock<MobileSegment.MobileRecord.MobileFullSnapshotRecord>()
        val resizedLayer = tree.layer.copy(bounds = tree.layer.bounds.copy(width = 30, height = 40))
        val differentSnapshot = tree.snapshot.copy(layers = listOf(resizedLayer))
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId)
        whenever(fixture.wireMapper.mapFullSnapshot(tree.snapshot))
            .thenReturn(CaptureWireMappingResult.Success(fullRecord))
        whenever(fixture.wireMapper.mapMutation(any(), eq(tree.snapshot)))
            .thenReturn(CaptureWireMappingResult.Success(mock()))

        // When: first cycle is accepted; the second cycle carries different content but its
        // generation is already past its deadline and must fail to accept.
        fixture.processor.process(fixture.capture(tree.snapshot, deadlineNs = 10L))
        fixture.clock.nowNs = 10L
        fixture.processor.process(fixture.capture(differentSnapshot, deadlineNs = 10L))
        fixture.clock.nowNs = 0L
        fixture.processor.process(fixture.capture(tree.snapshot, deadlineNs = 10L))

        // Then: every diff (the rejected second cycle and the accepted third one) is computed
        // against the first accepted snapshot as base - proving the rejected cycle never updated
        // retained state - and the third cycle's own mutation is the fully-unchanged one, since it
        // is diffing the same content against itself, not against the rejected cycle's content.
        val mutationCaptor = argumentCaptor<CapturedMutationSet>()
        verify(fixture.wireMapper, times(2)).mapMutation(mutationCaptor.capture(), eq(tree.snapshot))
        assertThat(mutationCaptor.allValues.last()).isEqualTo(
            CapturedMutationSet(timestamp = tree.snapshot.timestamp, scope = tree.scope)
        )
        verify(fixture.writer, times(1)).write(
            eq(
                EnrichedRecord(
                    fakeApplicationId,
                    fakeSessionId,
                    tree.scope.value,
                    openingRecords(tree.snapshot) + fullRecord
                )
            ),
            any()
        )
    }

    @Test
    fun `M report visible embedded content slots only after a successful write W process()`(
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeVisibleSlotId: String,
        @StringForgery fakeHiddenSlotId: String
    ) {
        // Given
        val tree = compositionTestTree()
        val embeddedContentSlotRegistry: EmbeddedContentSlotRegistry = mock()
        val fixture = Fixture(tree.scope.value, fakeApplicationId, fakeSessionId, embeddedContentSlotRegistry)
        val visibleEmbedded = CapturedWireframe.EmbeddedContent(
            identity = tree.factory.embeddedContentWireframe(tree.layer.identity),
            bounds = tree.layer.bounds,
            slotId = fakeVisibleSlotId,
            isVisible = true
        )
        val hiddenEmbedded = CapturedWireframe.EmbeddedContent(
            identity = tree.factory.embeddedContentWireframe(tree.layer.identity),
            bounds = tree.layer.bounds,
            slotId = fakeHiddenSlotId,
            isVisible = false
        )
        val snapshotWithSlots = tree.snapshot.copy(
            wireframes =
            tree.snapshot.wireframes + visibleEmbedded + hiddenEmbedded
        )
        whenever(fixture.wireMapper.mapFullSnapshot(snapshotWithSlots))
            .thenReturn(CaptureWireMappingResult.Success(mock()))
        // Simulates the write actually succeeding - the fixture's plain mock RecordWriter never
        // invokes onSuccess on its own.
        whenever(fixture.writer.write(any(), any())).doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(1).invoke()
        }

        // When
        fixture.processor.process(fixture.capture(snapshotWithSlots))

        // Then: only the visible slot is reported - the hidden one is excluded, and the reported
        // timestamp/viewId come from this snapshot/RUM context, not some other source.
        verify(embeddedContentSlotRegistry).onPlaceholdersWritten(
            tree.scope.value,
            snapshotWithSlots.timestamp,
            setOf(fakeVisibleSlotId)
        )
    }

    private fun openingRecords(snapshot: CapturedFullSnapshot): List<MobileSegment.MobileRecord> {
        val bounds = snapshot.root?.bounds
        return listOf(
            MobileSegment.MobileRecord.MetaRecord(
                timestamp = snapshot.timestamp,
                data = MobileSegment.Data1(width = bounds?.width ?: 0L, height = bounds?.height ?: 0L)
            ),
            MobileSegment.MobileRecord.FocusRecord(
                timestamp = snapshot.timestamp,
                data = MobileSegment.Data2(hasFocus = true)
            )
        )
    }

    private class FakeOrientationProvider(var orientation: Int = 1) : OrientationProvider {
        override fun currentOrientation(): Int = orientation
    }

    private class FakeTimeProvider(var elapsedNs: Long = 0L) : TimeProvider {
        override fun getDeviceTimestampMillis(): Long = 0L
        override fun getServerTimestampMillis(): Long = 0L
        override fun getDeviceElapsedTimeNanos(): Long = elapsedNs
        override fun getServerOffsetNanos(): Long = 0L
        override fun getServerOffsetMillis(): Long = 0L
        override fun getDeviceElapsedRealtimeMillis(): Long = 0L
        override fun getDeviceUptimeMillis(): Long = 0L
    }

    private class FakeClock(var nowNs: Long = 0L) : CaptureTimeProvider {
        override fun elapsedRealtimeNanos(): Long = nowNs
    }

    private class Fixture(
        initialViewId: String,
        applicationId: String,
        sessionId: String,
        embeddedContentSlotRegistry: EmbeddedContentSlotRegistry? = null
    ) {
        val writer: RecordWriter = mock()
        val wireMapper: CapturedTreeWireMapper = mock()
        val timeProvider = FakeTimeProvider()
        val orientationProvider = FakeOrientationProvider()
        val clock = FakeClock()
        val rumContextProvider: RumContextProvider = mock()
        val processor = DefaultSnapshotCompletionProcessor(
            rumContextProvider = rumContextProvider,
            recordWriter = writer,
            internalLogger = mock<InternalLogger>(),
            timeProvider = timeProvider,
            orientationProvider = orientationProvider,
            wireMapper = wireMapper,
            embeddedContentSlotRegistry = embeddedContentSlotRegistry
        )

        init {
            setCurrentRumView(initialViewId, applicationId, sessionId)
        }

        fun setCurrentRumView(viewId: String, applicationId: String, sessionId: String) {
            whenever(rumContextProvider.getRumContext())
                .thenReturn(SessionReplayRumContext(applicationId, sessionId, viewId))
        }

        fun capture(snapshot: CapturedFullSnapshot, deadlineNs: Long = Long.MAX_VALUE / 2): CompletedSnapshotCapture =
            CompletedSnapshotCapture(CaptureGenerationContext(1L, 0L, deadlineNs, clock), snapshot)
    }

    private companion object {
        const val FULL_SNAPSHOT_INTERVAL_NS = 3_000_000_000L
    }
}
