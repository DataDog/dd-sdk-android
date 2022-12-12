/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.RecordWriter
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.MobileIncrementalData
import com.datadog.android.sessionreplay.recorder.Node
import com.datadog.android.sessionreplay.recorder.OrientationChanged
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.utils.TimeProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.lang.NullPointerException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RecordedDataProcessorTest {

    @Mock
    lateinit var mockWriter: RecordWriter

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockMutationResolver: MutationResolver

    @Mock
    lateinit var mockNodeFlattener: NodeFlattener

    @LongForgery
    var fakeTimestamp: Long = 0L

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    lateinit var testedProcessor: RecordedDataProcessor

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockNodeFlattener.flattenNode(any()))
            .thenReturn(forge.aList { forge.getForgery() })
        whenever(mockMutationResolver.resolveMutations(any(), any()))
            .thenReturn(forge.getForgery())
        whenever(mockExecutorService.submit(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }
        whenever(mockTimeProvider.getDeviceTimestamp()).thenReturn(fakeTimestamp)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        testedProcessor = RecordedDataProcessor(
            mockRumContextProvider,
            mockTimeProvider,
            mockExecutorService,
            mockWriter,
            mockMutationResolver,
            mockNodeFlattener
        )
    }

    @Test
    fun `M send to the writer as EnrichedRecord W process { snapshot }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSingleLevelSnapshot()

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(3)
    }

    // region FullSnapshot

    @Test
    fun `M send FullSnapshot W process`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSingleLevelSnapshot()
        val fakeFlattenedSnapshot = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }

        // When
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot)).thenReturn(fakeFlattenedSnapshot)
        testedProcessor.processScreenSnapshot(fakeSnapshot)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val fullSnapshotRecord = captor.firstValue.records[2]
            as MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(fakeFlattenedSnapshot)
    }

    @Test
    fun `M send FullSnapshot W process { new view }`(forge: Forge) {
        // Given
        val fakeRumContext2 = forge.getForgery<SessionReplayRumContext>()
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext)
            .thenReturn(fakeRumContext2)
        val fakeSnapshotView1 = forge.aSingleLevelSnapshot()
        val fakeSnapshotView2 = forge.aSingleLevelSnapshot()
        val fakeFlattenedSnapshotView1 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeFlattenedSnapshotView2 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        whenever(mockNodeFlattener.flattenNode(fakeSnapshotView1))
            .thenReturn(fakeFlattenedSnapshotView1)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshotView2))
            .thenReturn(fakeFlattenedSnapshotView2)

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshotView1)
        testedProcessor.processScreenSnapshot(fakeSnapshotView2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        // one for first view, one for first view end and one for new view
        verify(mockWriter, times(3)).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.thirdValue.applicationId).isEqualTo(fakeRumContext2.applicationId)
        assertThat(captor.thirdValue.sessionId).isEqualTo(fakeRumContext2.sessionId)
        assertThat(captor.thirdValue.viewId).isEqualTo(fakeRumContext2.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        assertThat(captor.thirdValue.records.size).isEqualTo(3)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
        assertThat(captor.secondValue.records[0])
            .isInstanceOf(MobileSegment.MobileRecord.ViewEndRecord::class.java)
        assertThat(captor.thirdValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    @Test
    fun `M send FullSnapshot W process { same view, full snapshot window reached }`(
        forge: Forge
    ) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeFlattenedSnapshot1 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeFlattenedSnapshot2 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot1)).thenReturn(fakeFlattenedSnapshot1)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot2)).thenReturn(fakeFlattenedSnapshot2)
        testedProcessor.processScreenSnapshot(fakeSnapshot1)
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(RecordedDataProcessor.FULL_SNAPSHOT_INTERVAL_IN_NS))

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val fullSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(fakeFlattenedSnapshot2)
    }

    @Test
    fun `M send IncrementalRecord W process { same view, full snapshot window not reached }`(
        forge: Forge
    ) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeFlattenedSnapshot1 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeFlattenedSnapshot2 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeMutationData: MobileSegment.MobileIncrementalData.MobileMutationData =
            forge.getForgery()
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot1)).thenReturn(fakeFlattenedSnapshot1)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot2)).thenReturn(fakeFlattenedSnapshot2)
        whenever(
            mockMutationResolver.resolveMutations(
                fakeFlattenedSnapshot1,
                fakeFlattenedSnapshot2
            )
        ).thenReturn(fakeMutationData)
        testedProcessor.processScreenSnapshot(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val fullSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
        assertThat(fullSnapshotRecord.data).isEqualTo(fakeMutationData)
    }

    @Test
    fun `M send MetaRecord first W process { snapshot on a new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val rootWireframe = MobileSegment.Wireframe.ShapeWireframe(
            0,
            0,
            0,
            fakeRootWidth,
            fakeRootHeight
        )
        val fakeSnapshot = Node(wireframe = rootWireframe)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot)).thenReturn(listOf(rootWireframe))

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val metaRecord = captor.firstValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeRootHeight)
        assertThat(metaRecord.data.width).isEqualTo(fakeRootWidth)
    }

    @Test
    fun `M send FocusRecord second W process { snapshot on a new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val fakeSnapshot = Node(
            wireframe =
            MobileSegment.Wireframe.ShapeWireframe(
                0,
                0,
                0,
                fakeRootWidth,
                fakeRootHeight
            )
        )

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val focusRecord = captor.firstValue.records[1] as MobileSegment.MobileRecord.FocusRecord
        assertThat(focusRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(focusRecord.data.hasFocus).isTrue
    }

    @Test
    fun `M not send MetaRecord W process { snapshot 2 on same view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        testedProcessor.processScreenSnapshot(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        assertThat(captor.secondValue.records[0]).isInstanceOf(
            MobileSegment.MobileRecord
                .MobileIncrementalSnapshotRecord::class.java
        )
    }

    @Test
    fun `M not send FocusRecord W process { snapshot 2 on same view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        testedProcessor.processScreenSnapshot(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        assertThat(captor.secondValue.records[0]).isInstanceOf(
            MobileSegment.MobileRecord
                .MobileIncrementalSnapshotRecord::class.java
        )
    }

    @Test
    fun `M send MetaRecord W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val rootWireframe = MobileSegment.Wireframe.ShapeWireframe(
            0,
            0,
            0,
            fakeRootWidth,
            fakeRootHeight
        )
        val fakeSnapshot3 = Node(rootWireframe)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot3)).thenReturn(listOf(rootWireframe))

        testedProcessor.processScreenSnapshot(fakeSnapshot1)
        testedProcessor.processScreenSnapshot(fakeSnapshot2)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(forge.getForgery())

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.lastValue.records.size).isEqualTo(3)
        val metaRecord = captor.lastValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeRootHeight)
        assertThat(metaRecord.data.width).isEqualTo(fakeRootWidth)
    }

    @Test
    fun `M send FocusRecord W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeSnapshot3 = forge.aSingleLevelSnapshot()
        testedProcessor.processScreenSnapshot(fakeSnapshot1)
        testedProcessor.processScreenSnapshot(fakeSnapshot2)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(forge.getForgery())

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.lastValue.records.size).isEqualTo(3)
        val focusRecord = captor.lastValue.records[1] as MobileSegment.MobileRecord.FocusRecord
        assertThat(focusRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(focusRecord.data.hasFocus).isTrue
    }

    @Test
    fun `M send ViewEndRecord on prev view W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeSnapshot3 = forge.aSingleLevelSnapshot()
        testedProcessor.processScreenSnapshot(fakeSnapshot1)
        testedProcessor.processScreenSnapshot(fakeSnapshot2)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(forge.getForgery())

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.thirdValue.records.size).isEqualTo(1)
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        val viewEndRecord = captor.thirdValue.records[0] as MobileSegment.MobileRecord.ViewEndRecord
        assertThat(viewEndRecord.timestamp).isEqualTo(fakeTimestamp)
    }

    // endregion

    // region IncrementalSnapshotRecord

    @Test
    fun `M send IncrementalSnapshotRecord W process { snapshot 2nd time, same view }`(
        forge: Forge
    ) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeFlattenedSnapshot1 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeFlattenedSnapshot2 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeMutationData: MobileSegment.MobileIncrementalData.MobileMutationData =
            forge.getForgery()
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot1)).thenReturn(fakeFlattenedSnapshot1)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot2)).thenReturn(fakeFlattenedSnapshot2)
        whenever(
            mockMutationResolver.resolveMutations(
                fakeFlattenedSnapshot1,
                fakeFlattenedSnapshot2
            )
        ).thenReturn(fakeMutationData)
        testedProcessor.processScreenSnapshot(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val incrementalSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
        assertThat(incrementalSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(incrementalSnapshotRecord.data).isEqualTo(fakeMutationData)
    }

    @Test
    fun `M do nothing W process { no mutation was detected }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = fakeSnapshot1.copy()
        val fakeFlattenedSnapshot1 = forge.aList {
            getForgery(MobileSegment.Wireframe::class.java)
        }
        val fakeFlattenedSnapshot2 = ArrayList(fakeFlattenedSnapshot1)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot1)).thenReturn(fakeFlattenedSnapshot1)
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot2)).thenReturn(fakeFlattenedSnapshot2)
        whenever(
            mockMutationResolver.resolveMutations(
                fakeFlattenedSnapshot1,
                fakeFlattenedSnapshot2
            )
        ).thenReturn(null)
        testedProcessor.processScreenSnapshot(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot2)

        // Then
        // We should only send the FullSnapshotRecord. The IncrementalSnapshotRecord will not be
        // send as there was no mutation data detected.
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(1)).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    // region TouchData

    @Test
    fun `M send it to the writer as EnrichedRecord W process { TouchRecords }`(forge: Forge) {
        // Given
        val fakeTouchRecords = forge.aList {
            MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
                timestamp = forge.aPositiveLong(),
                data = forge.getForgery<MobileIncrementalData.PointerInteractionData>()
            )
        }

        // When
        testedProcessor.processTouchEventsRecords(fakeTouchRecords)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records).isEqualTo(fakeTouchRecords)
    }

    // endregion

    // region OrientationChanged

    @Test
    fun `M send it to the writer as EnrichedRecord W process { OrientationChanged }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSingleLevelSnapshot()
        val fakeOrientationChanged = OrientationChanged(forge.anInt(), forge.anInt())

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot, fakeOrientationChanged)

        // Then

        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(4)
        val incrementalSnapshotRecord = captor.firstValue.records[2] as
            MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
        val viewportResizeData = incrementalSnapshotRecord.data as
            MobileSegment.MobileIncrementalData.ViewportResizeData
        assertThat(viewportResizeData.height).isEqualTo(fakeOrientationChanged.height.toLong())
        assertThat(viewportResizeData.width).isEqualTo(fakeOrientationChanged.width.toLong())
    }

    @Test
    fun `M always send a FullSnapshot W process {OrientationChanged}`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeOrientationChanged1 = OrientationChanged(forge.anInt(), forge.anInt())
        val fakeOrientationChanged2 = OrientationChanged(forge.anInt(), forge.anInt())

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot1, fakeOrientationChanged1)
        testedProcessor.processScreenSnapshot(fakeSnapshot2, fakeOrientationChanged2)

        // Then

        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.secondValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.secondValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.secondValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(4)
        assertThat(captor.secondValue.records.size).isEqualTo(2)
        assertThat(captor.firstValue.records[3])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)

        assertThat(captor.secondValue.records[1])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    @Test
    fun `M always send a fullsnapshot W process {OrientationChanged, different view}`(
        forge: Forge
    ) {
        // Given
        val fakeRumContext2 = forge.getForgery<SessionReplayRumContext>()
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext)
            .thenReturn(fakeRumContext2)
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeOrientationChanged1 = OrientationChanged(forge.anInt(), forge.anInt())
        val fakeOrientationChanged2 = OrientationChanged(forge.anInt(), forge.anInt())

        // When
        testedProcessor.processScreenSnapshot(fakeSnapshot1, fakeOrientationChanged1)
        testedProcessor.processScreenSnapshot(fakeSnapshot2, fakeOrientationChanged2)

        // Then

        val captor = argumentCaptor<EnrichedRecord>()
        // one for first view, one for first view end and one for new view
        verify(mockWriter, times(3)).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.thirdValue.applicationId).isEqualTo(fakeRumContext2.applicationId)
        assertThat(captor.thirdValue.sessionId).isEqualTo(fakeRumContext2.sessionId)
        assertThat(captor.thirdValue.viewId).isEqualTo(fakeRumContext2.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(4)
        assertThat(captor.thirdValue.records.size).isEqualTo(4)
        assertThat(captor.firstValue.records[3])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)

        assertThat(captor.thirdValue.records[3])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    // endregion

    // region Misc

    @ParameterizedTest
    @MethodSource("processorArguments")
    fun `M do nothing W process { context is not invalid }`(argument: Any) {
        // Given
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())

        // When
        processArgument(argument)

        // Then
        verifyZeroInteractions(mockWriter)
    }

    // TODO: RUMM-2397 When proper logs are added modify this test accordingly
    @ParameterizedTest
    @MethodSource("processorArguments")
    fun `M do nothing W process { executor was shutdown }`(argument: Any) {
        // Given
        whenever(mockExecutorService.submit(any())).thenThrow(RejectedExecutionException())
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())

        // When
        processArgument(argument)

        // Then
        verifyZeroInteractions(mockWriter)
    }

    // TODO: RUMM-2397 When proper logs are added modify this test accordingly
    @ParameterizedTest
    @MethodSource("processorArguments")
    fun `M do nothing W process { executor throws NPE }`(argument: Any) {
        // Given
        whenever(mockExecutorService.submit(any())).thenThrow(NullPointerException())
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())

        // When
        processArgument(argument)

        // Then
        verifyZeroInteractions(mockWriter)
    }

    @ParameterizedTest
    @MethodSource("processorArguments")
    fun `M update current RUM context W process`(
        argument: Any,
        @Forgery
        fakeRumContext2: SessionReplayRumContext
    ) {
        // Given
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext)
            .thenReturn(fakeRumContext2)

        // When
        processArgument(argument)
        assertThat(testedProcessor.prevRumContext).isEqualTo(fakeRumContext)
        processArgument(argument)
        assertThat(testedProcessor.prevRumContext).isEqualTo(fakeRumContext2)
    }

    // endregion

    // region Internal

    private fun processArgument(argument: Any) {
        when (argument) {
            is Node -> {
                testedProcessor.processScreenSnapshot(argument)
            }
            is List<*> -> {
                val records = argument.filterIsInstance<MobileSegment.MobileRecord>()
                testedProcessor.processTouchEventsRecords(records)
            }
            is Pair<*, *> -> {
                testedProcessor.processScreenSnapshot(
                    argument.first as Node,
                    argument.second as OrientationChanged
                )
            }
            else -> fail(
                "The provided argument of " +
                    "class: ${argument::class.java.simpleName} was not matching " +
                    "any of the processor methods signature"
            )
        }
    }

    private fun MobileSegment.Wireframe.copy(id: Long): MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe ->
                this.copy(id = id)
            is MobileSegment.Wireframe.TextWireframe ->
                this.copy(id = id)
        }
    }

    private fun Forge.aSingleLevelSnapshot(): Node {
        return Node(
            MobileSegment.Wireframe.ShapeWireframe(
                aLong(min = 0),
                aLong(min = 0),
                aLong(min = 0),
                aLong(min = 0),
                aLong(min = 0)
            )
        )
    }

    // endregion

    companion object {

        private val FORGE: Forge = Forge().apply {
            ForgeConfigurator().configure(this)
        }

        @JvmStatic
        fun processorArguments(): List<Any> {
            val fakeSnapshot = Node(wireframe = FORGE.getForgery())
            val fakeTouchRecords = FORGE.aList {
                FORGE.getForgery<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>()
            }
            val fakeSnapshotWithOrientationChanged =
                Pair(
                    fakeSnapshot,
                    OrientationChanged(
                        FORGE.aPositiveInt(),
                        FORGE.aPositiveInt()
                    )
                )

            return listOf(fakeSnapshot, fakeTouchRecords, fakeSnapshotWithOrientationChanged)
        }
    }
}
