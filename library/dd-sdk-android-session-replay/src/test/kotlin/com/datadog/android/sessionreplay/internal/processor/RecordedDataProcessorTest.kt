 /*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.OrientationChanged
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.MobileIncrementalData
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
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

    @Mock
    lateinit var mockRecordCallback: RecordCallback

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
            mockRecordCallback,
            mockMutationResolver,
            mockNodeFlattener
        )
    }

    @Test
    fun `M send to the writer as EnrichedRecord W process { snapshot }`(forge: Forge) {
        // Given
        val fakeSnapshots = forge.aList { aSingleLevelSnapshot() }

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshots)

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
        val fakeSnapshots = forge.aList { aSingleLevelSnapshot() }
        val fakeFlattenedSnapshots = fakeSnapshots.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshots)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val fullSnapshotRecord = captor.firstValue.records[2]
            as MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(fakeFlattenedSnapshots)
        verify(mockRecordCallback).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send FullSnapshot W process { new view }`(forge: Forge) {
        // Given
        val fakeRumContext2 = forge.getForgery<SessionReplayRumContext>()
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext)
            .thenReturn(fakeRumContext2)
        val fakeSnapshotView1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshotView2 = forge.aList { aSingleLevelSnapshot() }
        fakeSnapshotView1.forEach {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
        }
        fakeSnapshotView2.forEach {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
        }

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotView1)
        testedProcessor.processScreenSnapshots(fakeSnapshotView2)

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
        inOrder(mockRecordCallback) {
            verify(mockRecordCallback).onRecordForViewSent(fakeRumContext.viewId)
            verify(mockRecordCallback).onRecordForViewSent(fakeRumContext2.viewId)
        }
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send FullSnapshot W process { same view, full snapshot window reached }`(
        forge: Forge
    ) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        fakeSnapshot1.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }
        val fakeFlattenedSnapshot2 = fakeSnapshot2.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        testedProcessor.processScreenSnapshots(fakeSnapshot1)
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(RecordedDataProcessor.FULL_SNAPSHOT_INTERVAL_IN_NS))

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val fullSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(fakeFlattenedSnapshot2)
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send IncrementalRecord W process { same view, full snapshot window not reached }`(
        forge: Forge
    ) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        val fakeFlattenedSnapshot1 = fakeSnapshot1.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        val fakeFlattenedSnapshot2 = fakeSnapshot2.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        val fakeMutationData: MobileIncrementalData.MobileMutationData =
            forge.getForgery()
        whenever(
            mockMutationResolver.resolveMutations(
                fakeFlattenedSnapshot1,
                fakeFlattenedSnapshot2
            )
        ).thenReturn(fakeMutationData)
        testedProcessor.processScreenSnapshots(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val fullSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
        assertThat(fullSnapshotRecord.data).isEqualTo(fakeMutationData)
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
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
        val fakeSnapshots = listOf(Node(wireframe = rootWireframe))
        whenever(mockNodeFlattener.flattenNode(fakeSnapshots[0])).thenReturn(listOf(rootWireframe))

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshots)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val metaRecord = captor.firstValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeRootHeight)
        assertThat(metaRecord.data.width).isEqualTo(fakeRootWidth)
        verify(mockRecordCallback).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send FocusRecord second W process { snapshot on a new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val fakeSnapshots = listOf(
            Node(
                wireframe =
                MobileSegment.Wireframe.ShapeWireframe(
                    0,
                    0,
                    0,
                    fakeRootWidth,
                    fakeRootHeight
                )
            )
        )

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshots)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val focusRecord = captor.firstValue.records[1] as MobileSegment.MobileRecord.FocusRecord
        assertThat(focusRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(focusRecord.data.hasFocus).isTrue
        verify(mockRecordCallback).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M not send MetaRecord W process { snapshot 2 on same view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }

        testedProcessor.processScreenSnapshots(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        assertThat(captor.secondValue.records[0]).isInstanceOf(
            MobileSegment.MobileRecord
                .MobileIncrementalSnapshotRecord::class.java
        )
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M not send FocusRecord W process { snapshot 2 on same view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        testedProcessor.processScreenSnapshots(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        assertThat(captor.secondValue.records[0]).isInstanceOf(
            MobileSegment.MobileRecord
                .MobileIncrementalSnapshotRecord::class.java
        )
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send MetaRecord W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val fakeSnapshot1 = listOf(forge.aSingleLevelSnapshot())
        val fakeSnapshot2 = listOf(forge.aSingleLevelSnapshot())
        val rootWireframe = MobileSegment.Wireframe.ShapeWireframe(
            0,
            0,
            0,
            fakeRootWidth,
            fakeRootHeight
        )
        val fakeSnapshot3 = listOf(Node(rootWireframe))
        whenever(mockNodeFlattener.flattenNode(fakeSnapshot3[0])).thenReturn(listOf(rootWireframe))

        testedProcessor.processScreenSnapshots(fakeSnapshot1)
        testedProcessor.processScreenSnapshots(fakeSnapshot2)
        val fakeRumContext2: SessionReplayRumContext = forge.getForgery()
        whenever(mockRumContextProvider.getRumContext())
            .thenReturn(fakeRumContext2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.lastValue.records.size).isEqualTo(3)
        val metaRecord = captor.lastValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeRootHeight)
        assertThat(metaRecord.data.width).isEqualTo(fakeRootWidth)
        inOrder(mockRecordCallback) {
            verify(mockRecordCallback, times(2))
                .onRecordForViewSent(fakeRumContext.viewId)
            verify(mockRecordCallback).onRecordForViewSent(fakeRumContext2.viewId)
        }
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send FocusRecord W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = listOf(forge.aSingleLevelSnapshot())
        val fakeSnapshot2 = listOf(forge.aSingleLevelSnapshot())
        val fakeSnapshot3 = listOf(forge.aSingleLevelSnapshot())
        testedProcessor.processScreenSnapshots(fakeSnapshot1)
        testedProcessor.processScreenSnapshots(fakeSnapshot2)
        val fakeRumContext2: SessionReplayRumContext = forge.getForgery()
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.lastValue.records.size).isEqualTo(3)
        val focusRecord = captor.lastValue.records[1] as MobileSegment.MobileRecord.FocusRecord
        assertThat(focusRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(focusRecord.data.hasFocus).isTrue
        inOrder(mockRecordCallback) {
            verify(mockRecordCallback, times(2))
                .onRecordForViewSent(fakeRumContext.viewId)
            verify(mockRecordCallback).onRecordForViewSent(fakeRumContext2.viewId)
        }
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M send ViewEndRecord on prev view W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = listOf(forge.aSingleLevelSnapshot())
        val fakeSnapshot2 = listOf(forge.aSingleLevelSnapshot())
        val fakeSnapshot3 = listOf(forge.aSingleLevelSnapshot())
        testedProcessor.processScreenSnapshots(fakeSnapshot1)
        testedProcessor.processScreenSnapshots(fakeSnapshot2)
        val fakeRumContext2: SessionReplayRumContext = forge.getForgery()
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.thirdValue.records.size).isEqualTo(1)
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        val viewEndRecord = captor.thirdValue.records[0] as MobileSegment.MobileRecord.ViewEndRecord
        assertThat(viewEndRecord.timestamp).isEqualTo(fakeTimestamp)
        inOrder(mockRecordCallback) {
            verify(mockRecordCallback, times(2))
                .onRecordForViewSent(fakeRumContext.viewId)
            verify(mockRecordCallback).onRecordForViewSent(fakeRumContext2.viewId)
        }
        verifyNoMoreInteractions(mockRecordCallback)
    }

    // endregion

    // region IncrementalSnapshotRecord

    @Test
    fun `M send IncrementalSnapshotRecord W process { snapshot 2nd time, same view }`(
        forge: Forge
    ) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        val fakeFlattenedSnapshot1 = fakeSnapshot1.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        val fakeFlattenedSnapshot2 = fakeSnapshot2.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        val fakeMutationData: MobileIncrementalData.MobileMutationData =
            forge.getForgery()
        whenever(
            mockMutationResolver.resolveMutations(
                fakeFlattenedSnapshot1,
                fakeFlattenedSnapshot2
            )
        ).thenReturn(fakeMutationData)
        testedProcessor.processScreenSnapshots(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val incrementalSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
        assertThat(incrementalSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(incrementalSnapshotRecord.data).isEqualTo(fakeMutationData)
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M do nothing W process { no mutation was detected }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        val fakeFlattenedSnapshot1 = fakeSnapshot1.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        val fakeFlattenedSnapshot2 = fakeSnapshot2.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        whenever(
            mockMutationResolver.resolveMutations(
                fakeFlattenedSnapshot1,
                fakeFlattenedSnapshot2
            )
        ).thenReturn(null)
        testedProcessor.processScreenSnapshots(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot2)

        // Then
        // We should only send the FullSnapshotRecord. The IncrementalSnapshotRecord will not be
        // send as there was no mutation data detected.
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(1)).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
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
        verify(mockRecordCallback).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    // endregion

    // region OrientationChanged

    @Test
    fun `M send it to the writer as EnrichedRecord W process { OrientationChanged }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aList { forge.aSingleLevelSnapshot() }
        val fakeOrientationChanged = OrientationChanged(forge.anInt(), forge.anInt())

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot, fakeOrientationChanged)

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
            MobileIncrementalData.ViewportResizeData
        assertThat(viewportResizeData.height).isEqualTo(fakeOrientationChanged.height.toLong())
        assertThat(viewportResizeData.width).isEqualTo(fakeOrientationChanged.width.toLong())
        verify(mockRecordCallback).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M always send a FullSnapshot W process {OrientationChanged}`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        val fakeOrientationChanged1 = OrientationChanged(forge.anInt(), forge.anInt())
        val fakeOrientationChanged2 = OrientationChanged(forge.anInt(), forge.anInt())

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot1, fakeOrientationChanged1)
        testedProcessor.processScreenSnapshots(fakeSnapshot2, fakeOrientationChanged2)

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
        verify(mockRecordCallback, times(2)).onRecordForViewSent(fakeRumContext.viewId)
        verifyNoMoreInteractions(mockRecordCallback)
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
        val fakeSnapshot1 = forge.aList { aSingleLevelSnapshot() }
        val fakeSnapshot2 = forge.aList { aSingleLevelSnapshot() }
        val fakeOrientationChanged1 = OrientationChanged(forge.anInt(), forge.anInt())
        val fakeOrientationChanged2 = OrientationChanged(forge.anInt(), forge.anInt())

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshot1, fakeOrientationChanged1)
        testedProcessor.processScreenSnapshots(fakeSnapshot2, fakeOrientationChanged2)

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
        inOrder(mockRecordCallback) {
            verify(mockRecordCallback)
                .onRecordForViewSent(fakeRumContext.viewId)
            verify(mockRecordCallback).onRecordForViewSent(fakeRumContext2.viewId)
        }
        verifyNoMoreInteractions(mockRecordCallback)
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
            is List<*> -> {
                val records = argument.filterIsInstance<MobileSegment.MobileRecord>()
                testedProcessor.processTouchEventsRecords(records)
            }
            is Pair<*, *> -> {
                @Suppress("UNCHECKED_CAST", "CastToNullableType")
                testedProcessor.processScreenSnapshots(
                    argument.first as List<Node>,
                    argument.second as OrientationChanged?
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
            val fakeSnapshots = FORGE.aList { Node(wireframe = FORGE.getForgery()) }
            val fakeTouchRecords = FORGE.aList {
                FORGE.getForgery<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>()
            }
            return listOf(
                fakeSnapshots to null,
                fakeTouchRecords,
                fakeSnapshots to OrientationChanged(FORGE.aPositiveInt(), FORGE.aPositiveInt())
            )
        }
    }
}
