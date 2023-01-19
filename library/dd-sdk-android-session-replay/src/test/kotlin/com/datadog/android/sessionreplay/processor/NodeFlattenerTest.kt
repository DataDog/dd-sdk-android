/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.Node
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.LinkedList
import kotlin.collections.ArrayList
import kotlin.math.pow

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class NodeFlattenerTest {

    lateinit var testedNodeFlattener: NodeFlattener

    @Mock
    lateinit var mockWireframeUtils: WireframeUtils

    @BeforeEach
    fun `set up`() {
        whenever(mockWireframeUtils.checkIsValidWireframe(any(), any()))
            .thenReturn(true)
        testedNodeFlattener = NodeFlattener(mockWireframeUtils)
    }

    // region Unit Tests

    @Test
    fun `M flatten the tree using DFS W process { snapshot 2 levels }`(forge: Forge) {
        // Given
        val fakeSnapshot = forge.aSnapshot(2)

        // When
        val wireframes = testedNodeFlattener.flattenNode(fakeSnapshot)

        // Then
        val expectedList = listOf(
            fakeSnapshot.wireframe,
            fakeSnapshot.children[0].wireframe,
            fakeSnapshot.children[0].children[0].wireframe,
            fakeSnapshot.children[0].children[1].wireframe,
            fakeSnapshot.children[1].wireframe,
            fakeSnapshot.children[1].children[0].wireframe,
            fakeSnapshot.children[1].children[1].wireframe
        )
        assertThat(wireframes).isEqualTo(expectedList)
    }

    @Test
    fun `M flatten the tree using DFS W process { snapshot n levels }`(forge: Forge) {
        // Given
        val maxWidth = 40L
        val maxHeight = 40L
        val expectedList: List<MobileSegment.Wireframe> =
            Array<MobileSegment.Wireframe>(forge.anInt(min = 10, max = 100)) {
                // just to avoid collisions we will add the wireframes manually
                val width = forge.aLong(min = 1, max = maxWidth)
                val height = forge.aLong(min = 1, max = maxHeight)
                val x = it * maxWidth
                val y = it * maxHeight
                forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                    .copy(width = width, height = height, x = x, y = y)
            }.toList()
        val fakeSnapshot = generateTreeFromList(expectedList)

        // When
        val wireframes = testedNodeFlattener.flattenNode(fakeSnapshot)

        // Then
        assertThat(wireframes).isEqualTo(expectedList)
    }

    @Test
    fun `M filter out the invalid wireframe W process`(forge: Forge) {
        // Given
        val expectedList: MutableList<MobileSegment.Wireframe> =
            Array<MobileSegment.Wireframe>(forge.anInt(min = 10, max = 20)) {
                forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            }.toMutableList()
        val fakeSnapshot = generateTreeFromList(expectedList)

        val randomIndex = forge.anInt(min = 0, max = expectedList.size)
        val excludedWireframe = expectedList.removeAt(randomIndex)
        whenever(mockWireframeUtils.checkIsValidWireframe(eq(excludedWireframe), any()))
            .thenReturn(false)

        // When
        val wireframes = testedNodeFlattener.flattenNode(fakeSnapshot)

        // Then
        assertThat(wireframes).isEqualTo(expectedList)
    }

    // endregion

    // region Internals

    private fun generateTreeFromList(list: List<MobileSegment.Wireframe>): Node {
        val mutableList = list.toMutableList()
        val root = mutableList.removeFirst().toNode()
        val middle = mutableList.size / 2
        // add left
        // we need to create a new list as Kotlin .subList re - uses the old list
        addLeafsToParent(root, ArrayList(mutableList.subList(0, middle)))
        // add right
        addLeafsToParent(root, ArrayList(mutableList.subList(middle, mutableList.size)))
        return root
    }

    private fun addLeafsToParent(parent: Node, leafs: MutableList<MobileSegment.Wireframe>) {
        if (leafs.isEmpty()) {
            return
        }
        val leafToAdd = leafs.removeFirst().toNode()
        parent.addChild(leafToAdd)
        val middle = leafs.size / 2
        // add left
        addLeafsToParent(leafToAdd, ArrayList(leafs.subList(0, middle)))
        // add right
        addLeafsToParent(leafToAdd, ArrayList(leafs.subList(middle, leafs.size)))
    }

    private fun Node.addChild(node: Node) {
        (this.children as LinkedList).add(node)
    }

    private fun MobileSegment.Wireframe.toNode(parent: Node? = null): Node {
        val parents = parent?.parents ?: emptyList()
        whenever(mockWireframeUtils.resolveWireframeClip(this, parents))
            .thenReturn(this.clip())
        return Node(
            wireframe = this,
            children = LinkedList(),
            parents = parents
        )
    }

    private fun Forge.aSnapshot(treeLevel: Int, childrenSize: Int = 2): Node {
        val baseWidth = aLong(min = 200, max = 300)
        val baseHeight = aLong(min = 300, max = 400)
        val dimensionsMultiplierFactor = childrenSize.toFloat().pow(treeLevel).toLong()
        val root = Node(
            wireframe =
            MobileSegment.Wireframe.ShapeWireframe(
                0,
                0,
                0,
                baseWidth * dimensionsMultiplierFactor,
                baseHeight * dimensionsMultiplierFactor
            )
        )
        return root.copy(children = snapshots(root, 1, treeLevel, childrenSize))
    }

    private fun MobileSegment.Wireframe.copy(id: Long): MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe ->
                this.copy(id = id)
            is MobileSegment.Wireframe.TextWireframe ->
                this.copy(id = id)
        }
    }

    private fun Forge.snapshots(parent: Node, treeLevel: Int, maxTreeLevel: Int, childrenSize: Int):
        List<Node> {
        val startIdIndex = childrenSize * parent.wireframe.id()
        val parentWireframeBounds = parent.wireframe.bounds()
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
                var node = Node(wireframe = wireframe)
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
        } else {
            emptyList()
        }
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

    private fun MobileSegment.Wireframe.clip(): MobileSegment.WireframeClip? {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe ->
                clip
            is MobileSegment.Wireframe.TextWireframe ->
                clip
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
