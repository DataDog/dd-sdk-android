/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.Node
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.utils.TimeProvider
import com.datadog.android.sessionreplay.writer.Writer
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
import java.util.LinkedList
import java.util.Stack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.math.pow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotProcessorTest {

    @Mock
    lateinit var mockWriter: Writer

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockRumContextProvider: RumContextProvider

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @LongForgery
    var fakeTimestamp: Long = 0L

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    lateinit var testedProcessor: SnapshotProcessor

    @BeforeEach
    fun `set up`() {
        whenever(mockExecutorService.submit(any())).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }
        whenever(mockTimeProvider.getDeviceTimestamp()).thenReturn(fakeTimestamp)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(fakeRumContext)
        testedProcessor = SnapshotProcessor(
            mockRumContextProvider,
            mockTimeProvider,
            mockExecutorService,
            mockWriter
        )
    }

    @Test
    fun `M send it to the writer as EnrichedRecord W process { snapshot }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSingleLevelSnapshot()

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.applicationId).isEqualTo(fakeRumContext.applicationId)
        assertThat(captor.firstValue.sessionId).isEqualTo(fakeRumContext.sessionId)
        assertThat(captor.firstValue.viewId).isEqualTo(fakeRumContext.viewId)
        assertThat(captor.firstValue.records.size).isEqualTo(2)
    }

    @Test
    fun `M do nothing W process { snapshot is empty }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSingleLevelSnapshot().copy(wireframes = emptyList())

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M do nothing W process { context is not invalid }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSingleLevelSnapshot()
        whenever(mockRumContextProvider.getRumContext()).thenReturn(SessionReplayRumContext())

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `M send MetaRecord first W process { snapshot on a new view }`(forge: Forge) {
        // Given
        val fakeRootWidth = forge.aLong(min = 400)
        val fakeRootHeight = forge.aLong(min = 700)
        val fakeSnapshot = Node(
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

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        assertThat(captor.firstValue.records.size).isEqualTo(2)
        val metaRecord = captor.firstValue.records[0] as MobileSegment.MobileRecord.MetaRecord
        assertThat(metaRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(metaRecord.data.height).isEqualTo(fakeRootHeight)
        assertThat(metaRecord.data.width).isEqualTo(fakeRootWidth)
    }

    @Test
    fun `M not send MetaRecord W process { snapshot 2 on same view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        testedProcessor.process(fakeSnapshot1)

        // When
        testedProcessor.process(fakeSnapshot2)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(2)).write(captor.capture())
        assertThat(captor.secondValue.records.size).isEqualTo(1)
        assertThat(captor.secondValue.records[0]).isInstanceOf(
            MobileSegment.MobileRecord
                .MobileFullSnapshotRecord::class.java
        )
    }

    @Test
    fun `M send MetaRecord W process { snapshot 3 on new view }`(forge: Forge) {
        // Given
        val fakeSnapshot1 = forge.aSingleLevelSnapshot()
        val fakeSnapshot2 = forge.aSingleLevelSnapshot()
        val fakeSnapshot3 = forge.aSingleLevelSnapshot()
        testedProcessor.process(fakeSnapshot1)
        testedProcessor.process(fakeSnapshot2)
        whenever(mockRumContextProvider.getRumContext()).thenReturn(forge.getForgery())

        // When
        testedProcessor.process(fakeSnapshot3)

        // Then
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter, times(3)).write(captor.capture())
        assertThat(captor.thirdValue.records.size).isEqualTo(2)
        assertThat(captor.thirdValue.records[0]).isInstanceOf(
            MobileSegment.MobileRecord.MetaRecord::class.java
        )
    }

    @Test
    fun `M flatten the tree using DFS W process { snapshot 2 levels }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSnapshot(2)

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        val expectedList = fakeSnapshot.wireframes +
            fakeSnapshot.children[0].wireframes +
            fakeSnapshot.children[0].children[0].wireframes +
            fakeSnapshot.children[0].children[1].wireframes +
            fakeSnapshot.children[1].wireframes +
            fakeSnapshot.children[1].children[0].wireframes +
            fakeSnapshot.children[1].children[1].wireframes
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        val fullSnapshotRecord = captor.firstValue.records[1] as
            MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(expectedList)
    }

    @Test
    fun `M flatten the tree using DFS W process { snapshot n levels }`(forge: Forge) {
        // Given
        val requiredLevels = forge.anInt(min = 1, max = 9)
        val fakeSnapshot = forge.aSnapshot(requiredLevels)

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        val stack = Stack<Node>()
        val expectedList = LinkedList<MobileSegment.Wireframe>()
        stack.push(fakeSnapshot)
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            expectedList.addAll(node!!.wireframes)
            for (i in node.children.count() - 1 downTo 0) {
                stack.push(node.children[i])
            }
        }
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        val fullSnapshotRecord = captor.firstValue.records[1] as
            MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(expectedList)
    }

    @Test
    fun `M filter out the completely covered wireframes W process`(forge: Forge) {
        // Given
        var fakeSnapshot = forge.aSnapshot(2)
        val topWireframeId = forge.aLong(min = 0)
        val topWireframe = fakeSnapshot.children[1].wireframes[0].copy(topWireframeId)
        val childrenWithCoverUpWireframe = fakeSnapshot.children +
            Node(wireframes = listOf(topWireframe))
        fakeSnapshot = fakeSnapshot.copy(children = childrenWithCoverUpWireframe)

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        // The added wireframe completely covers the previous one + its children in the list. The
        // previous wireframe and it children will therefore be removed.
        val expectedList = fakeSnapshot.wireframes +
            fakeSnapshot.children[0].wireframes +
            fakeSnapshot.children[0].children[0].wireframes +
            fakeSnapshot.children[0].children[1].wireframes +
            topWireframe
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        val fullSnapshotRecord = captor.firstValue.records[1] as
            MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(expectedList)
    }

    @Test
    fun `M filter out wireframes with invalid width W process`(forge: Forge) {
        // Given
        var fakeSnapshot = forge.aSnapshot(1)
        val invalidWidthWireframe: MobileSegment.Wireframe = if (forge.aBool()) {
            forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(width = forge.aLong(max = 1))
        } else {
            forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
                .copy(width = forge.aLong(max = 1))
        }
        val childrenWithCoverUpWireframe = fakeSnapshot.children + Node(
            wireframes = listOf(invalidWidthWireframe)
        )
        fakeSnapshot = fakeSnapshot.copy(children = childrenWithCoverUpWireframe)

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        val expectedList = fakeSnapshot.wireframes +
            fakeSnapshot.children[0].wireframes +
            fakeSnapshot.children[1].wireframes
        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        val fullSnapshotRecord = captor.firstValue.records[1] as
            MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(expectedList)
    }

    @Test
    fun `M filter out wireframes with invalid height W process`(forge: Forge) {
        // Given
        var fakeSnapshot = forge.aSnapshot(1)
        val invalidHeightWireframe: MobileSegment.Wireframe = if (forge.aBool()) {
            forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(height = forge.aLong(max = 1))
        } else {
            forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
                .copy(height = forge.aLong(max = 1))
        }
        val childrenWithCoverUpWireframe = fakeSnapshot.children + Node(
            wireframes = listOf(invalidHeightWireframe)
        )
        fakeSnapshot = fakeSnapshot.copy(children = childrenWithCoverUpWireframe)

        // When
        testedProcessor.process(fakeSnapshot)

        // Then
        val expectedList = fakeSnapshot.wireframes +
            fakeSnapshot.children[0].wireframes +
            fakeSnapshot.children[1].wireframes

        val captor = argumentCaptor<EnrichedRecord>()
        verify(mockWriter).write(captor.capture())
        val fullSnapshotRecord = captor.firstValue.records[1] as
            MobileSegment.MobileRecord.MobileFullSnapshotRecord
        assertThat(fullSnapshotRecord.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fullSnapshotRecord.data.wireframes).isEqualTo(expectedList)
    }

    // region Internal

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

    private fun Forge.aSnapshot(treeLevel: Int, childrenSize: Int = 2): Node {
        val baseWidth = aLong(min = 200, max = 300)
        val baseHeight = aLong(min = 300, max = 400)
        val dimensionsMultiplierFactor = childrenSize.toFloat().pow(treeLevel).toLong()
        val root = Node(
            wireframes = listOf(
                MobileSegment.Wireframe.ShapeWireframe(
                    0,
                    0,
                    0,
                    baseWidth * dimensionsMultiplierFactor,
                    baseHeight * dimensionsMultiplierFactor
                )
            )
        )
        return root.copy(children = snapshots(root, 1, treeLevel, childrenSize))
    }

    private fun Forge.snapshots(parent: Node, treeLevel: Int, maxTreeLevel: Int, childrenSize: Int):
        List<Node> {
        val startIdIndex = childrenSize * parent.wireframes[0].id()
        val parentWireframeBounds = parent.wireframes[0].bounds()
        val parentWidth = parentWireframeBounds.width
        val parentHeight = parentWireframeBounds.height
        val parentY = parentWireframeBounds.y
        val parentX = parentWireframeBounds.x
        val maxWidth = parentWidth / childrenSize
        val maxHeight = parentHeight / childrenSize
        if (maxWidth == 0L || maxHeight == 0L) {
            return emptyList()
        }

        return if (treeLevel <= maxTreeLevel) {
            Array(childrenSize) {
                val nodeId = startIdIndex + (treeLevel * 10 + it).toLong()
                val minY = parentY + maxHeight * it
                val minX = parentX + maxWidth * it
                val wireframe = forgeWireframe(nodeId, minX, minY, maxWidth, maxHeight)
                var node = Node(wireframes = listOf(wireframe))
                node = node.copy(
                    children = snapshots(
                        node,
                        treeLevel + 1,
                        maxTreeLevel,
                        childrenSize
                    )
                )
                node
            }.toList()
        } else emptyList()
    }

    private fun Forge.forgeWireframe(id: Long, x: Long, y: Long, width: Long, height: Long):
        MobileSegment.Wireframe {
        return when (val fakeWireframe = getForgery<MobileSegment.Wireframe>()) {
            is MobileSegment.Wireframe.ShapeWireframe -> fakeWireframe.copy(
                id = id,
                x = x,
                y = y,
                width = width,
                height = height
            )
            is MobileSegment.Wireframe.TextWireframe -> fakeWireframe.copy(
                id = id,
                x = x,
                y = y,
                width = width,
                height = height
            )
        }
    }

    private fun MobileSegment.Wireframe.bounds(): Bounds {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe ->
                Bounds(this.x, this.y, this.width, this.height)
            is MobileSegment.Wireframe.TextWireframe ->
                Bounds(this.x, this.y, this.width, this.height)
        }
    }

    private data class Bounds(val x: Long, val y: Long, val width: Long, val height: Long)

    private fun MobileSegment.Wireframe.id(): Long {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> this.id
            is MobileSegment.Wireframe.TextWireframe -> this.id
        }
    }

    // endregion
}
