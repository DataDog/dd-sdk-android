 /*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import android.content.res.Configuration
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.RecordWriter
import com.datadog.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem
import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.model.MobileSegment.MobileIncrementalData
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
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
    lateinit var mockMutationResolver: MutationResolver

    @Mock
    lateinit var mockNodeFlattener: NodeFlattener

    @LongForgery
    var fakeTimestamp: Long = 0L

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    lateinit var testedProcessor: RecordedDataProcessor

    @Mock
    lateinit var mockRumContextDataHandler: RumContextDataHandler

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    private val invalidRumContext = SessionReplayRumContext()

    private lateinit var sameViewRumContextData: RumContextData
    private lateinit var invalidRumContextData: RumContextData
    private lateinit var initialRumContextData: RumContextData

    private lateinit var currentRumContextData: RumContextData

    private lateinit var fakeSnapshotItem1: SnapshotRecordedDataQueueItem
    private lateinit var fakeSnapshotItem2: SnapshotRecordedDataQueueItem
    private lateinit var fakeSnapshotItem3: SnapshotRecordedDataQueueItem
    private lateinit var fakeSystemInfoItem: SnapshotRecordedDataQueueItem

    private lateinit var fakeSnapshot1: List<Node>
    private lateinit var fakeSnapshot2: List<Node>
    private lateinit var fakeSnapshot3: List<Node>
    private lateinit var fakeSystemInformation2: SystemInformation

    @BeforeEach
    fun `set up`(forge: Forge) {
        initialRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext,
            invalidRumContext
        )

        invalidRumContextData = RumContextData(
            fakeTimestamp,
            invalidRumContext,
            invalidRumContext
        )

        sameViewRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext,
            fakeRumContext
        )

        fakeSnapshot1 = listOf(forge.aSingleLevelSnapshot())
        fakeSnapshot2 = listOf(forge.aSingleLevelSnapshot())
        fakeSnapshot3 = listOf(forge.aSingleLevelSnapshot())

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(initialRumContextData)
            .thenReturn(sameViewRumContextData)

        val availableOrientations = intArrayOf(
            Configuration.ORIENTATION_LANDSCAPE,
            Configuration.ORIENTATION_PORTRAIT
        )

        fakeSystemInformation2 = fakeSystemInformation.copy(
            screenOrientation = forge.anElementFrom(availableOrientations)
        )

        // we make sure the fullsnapshot was not triggered by a screen orientation change
        fakeSystemInformation = fakeSystemInformation
            .copy(screenOrientation = Configuration.ORIENTATION_UNDEFINED)
        whenever(mockNodeFlattener.flattenNode(any()))
            .thenReturn(forge.aList { forge.getForgery() })
        whenever(mockMutationResolver.resolveMutations(any(), any()))
            .thenReturn(forge.getForgery())
        whenever(mockTimeProvider.getDeviceTimestamp()).thenReturn(fakeTimestamp)
        testedProcessor = RecordedDataProcessor(
            mockWriter,
            mockMutationResolver,
            mockNodeFlattener
        )
    }

    @Test
    fun `M send to the writer as EnrichedRecord W process { snapshot }`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

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
        val fakeFlattenedSnapshots = fakeSnapshot1.map {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
            fakeFlattenedSnapshot
        }.flatten()
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val fullSnapshotRecord = captor.firstValue.records[2]
            as MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(fakeFlattenedSnapshots)
    }

    @Test
    fun `M send FullSnapshot W process { new view }`(forge: Forge) {
        // Given
        val fakeRumContext2 = forge.getForgery<SessionReplayRumContext>()

        val newerRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext2,
            initialRumContextData.newRumContext
        )

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(initialRumContextData)
            .thenReturn(newerRumContextData)

        fakeSnapshot1.forEach {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
        }
        fakeSnapshot2.forEach {
            val fakeFlattenedSnapshot = forge.aList {
                getForgery(MobileSegment.Wireframe::class.java)
            }
            whenever(mockNodeFlattener.flattenNode(it)).thenReturn(fakeFlattenedSnapshot)
        }

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

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
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(RecordedDataProcessor.FULL_SNAPSHOT_INTERVAL_IN_NS))

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

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
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        val fullSnapshotRecord = captor.secondValue.records[0]
            as MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord
        assertThat(fullSnapshotRecord.data).isEqualTo(fakeMutationData)
    }

    @Test
    fun `M send MetaRecord first W process { snapshot on a new view }`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val metaRecord = captor.firstValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeSystemInformation.screenBounds.height)
        assertThat(metaRecord.data.width).isEqualTo(fakeSystemInformation.screenBounds.width)
    }

    @Test
    fun `M send FocusRecord second W process { snapshot on a new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val fakeSnapshot = listOf(
            Node(
                wireframes = listOf(
                    MobileSegment.Wireframe.ShapeWireframe(
                        0,
                        0,
                        0,
                        fakeRootWidth,
                        fakeRootHeight
                    )
                )
            )
        )

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        val focusRecord = captor.firstValue.records[1] as MobileSegment.MobileRecord.FocusRecord
        assertThat(focusRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(focusRecord.data.hasFocus).isTrue
    }

    @Test
    fun `M not send MetaRecord W process { snapshot 2 on same view }`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

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
    fun `M not send FocusRecord W process { snapshot 2 on same view }`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

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
        val fakeRumContext2: SessionReplayRumContext = forge.getForgery()

        val newerRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext2,
            initialRumContextData.newRumContext
        )

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(initialRumContextData)
            .thenReturn(sameViewRumContextData)
            .thenReturn(newerRumContextData)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem3 = createSnapshotItem(fakeSnapshot3)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(4)).write(captor.capture())
        assertThat(captor.lastValue.records.size).isEqualTo(3)
        val metaRecord = captor.lastValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeSystemInformation2.screenBounds.height)
        assertThat(metaRecord.data.width).isEqualTo(fakeSystemInformation2.screenBounds.width)
    }

    @Test
    fun `M send FocusRecord W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeRumContext2: SessionReplayRumContext = forge.getForgery()

        val newerRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext2,
            initialRumContextData.newRumContext
        )

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(initialRumContextData)
            .thenReturn(sameViewRumContextData)
            .thenReturn(newerRumContextData)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem3 = createSnapshotItem(fakeSnapshot3)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem3)

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
        val fakeRumContext2: SessionReplayRumContext = forge.getForgery()

        val newRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext2,
            sameViewRumContextData.newRumContext
        )

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(initialRumContextData)
            .thenReturn(sameViewRumContextData)
            .thenReturn(newRumContextData)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem3 = createSnapshotItem(fakeSnapshot3)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem3)

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

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

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

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

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

        val rumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        val item = TouchEventRecordedDataQueueItem(
            rumContextData = rumContextData,
            touchData = fakeTouchRecords
        )

        // When
        testedProcessor.processTouchEventsRecords(item)

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
    fun `M send send a FullSnapshot W process { OrientationChanged }`(forge: Forge) {
        // Given
        // we make sure the orientation changed
        fakeSystemInformation = fakeSystemInformation
            .copy(
                screenOrientation = forge.anElementFrom(
                    Configuration.ORIENTATION_LANDSCAPE,
                    Configuration.ORIENTATION_PORTRAIT
                )
            )

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1, fakeSystemInformation)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

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
        assertThat(viewportResizeData.height).isEqualTo(fakeSystemInformation.screenBounds.height)
        assertThat(viewportResizeData.width).isEqualTo(fakeSystemInformation.screenBounds.width)
    }

    @Test
    fun `M always send a FullSnapshot W process {orientation changed same view}`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSystemInfoItem = createSnapshotItem(fakeSnapshot2, fakeSystemInformation2)

        testedProcessor.processScreenSnapshots(fakeSystemInfoItem)

        // Then

        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.secondValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.secondValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.secondValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        assertThat(captor.secondValue.records.size).isEqualTo(2)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)

        assertThat(captor.secondValue.records[1])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    @Test
    fun `M not send a FullSnapshot W process {orientation not changed same view}`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem2 = createSnapshotItem(fakeSnapshot2)

        testedProcessor.processScreenSnapshots(fakeSnapshotItem2)

        // Then

        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.secondValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.secondValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.secondValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
        assertThat(captor.secondValue.records[0])
            .isInstanceOf(MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord::class.java)
    }

    @Test
    fun `M always send a FullSnapshot W process {orientation changed in a row}`() {
        // Given
        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSystemInfoItem = createSnapshotItem(fakeSnapshot2, fakeSystemInformation2)

        testedProcessor.processScreenSnapshots(fakeSystemInfoItem)

        // Then

        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.secondValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.secondValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.secondValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(3)
        assertThat(captor.secondValue.records.size).isEqualTo(2)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)

        assertThat(captor.secondValue.records[1])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    @Test
    fun `M always send a FullSnapshot W process {OrientationChanged, different view}`(
        forge: Forge
    ) {
        // Given
        val fakeRumContext2 = forge.getForgery<SessionReplayRumContext>()

        val newerRumContextData = RumContextData(
            fakeTimestamp,
            fakeRumContext2,
            fakeRumContext
        )

        whenever(mockRumContextDataHandler.createRumContextData())
            .thenReturn(initialRumContextData)
            .thenReturn(newerRumContextData)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")

        fakeSnapshotItem1 = createSnapshotItem(fakeSnapshot1)

        // When
        testedProcessor.processScreenSnapshots(fakeSnapshotItem1)

        currentRumContextData = mockRumContextDataHandler.createRumContextData() ?: fail("RumContextData is null")
        fakeSystemInfoItem = createSnapshotItem(fakeSnapshot2, fakeSystemInformation2)
        testedProcessor.processScreenSnapshots(fakeSystemInfoItem)

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
        assertThat(captor.thirdValue.records.size).isEqualTo(4)
        assertThat(captor.firstValue.records[2])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)

        assertThat(captor.thirdValue.records[3])
            .isInstanceOf(MobileSegment.MobileRecord.MobileFullSnapshotRecord::class.java)
    }

    // endregion

    // region Internal

    private fun MobileSegment.Wireframe.copy(id: Long): MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe ->
                this.copy(id = id)
            is MobileSegment.Wireframe.TextWireframe ->
                this.copy(id = id)
            is MobileSegment.Wireframe.ImageWireframe ->
                this.copy(id = id)
            is MobileSegment.Wireframe.PlaceholderWireframe ->
                this.copy(id = id)
        }
    }

    private fun Forge.aSingleLevelSnapshot(): Node {
        return Node(
            wireframes = listOf(
                MobileSegment.Wireframe.ShapeWireframe(
                    aLong(min = 0),
                    aLong(min = 0),
                    aLong(min = 0),
                    aLong(min = 0),
                    aLong(min = 0)
                )
            )
        )
    }

    private fun createSnapshotItem(snapshot: List<Node>, systemInformation: SystemInformation = fakeSystemInformation):
        SnapshotRecordedDataQueueItem {
        val item = SnapshotRecordedDataQueueItem(
            rumContextData = currentRumContextData,
            systemInformation = systemInformation
        )

        item.nodes = snapshot

        return item
    }

    // endregion
}
