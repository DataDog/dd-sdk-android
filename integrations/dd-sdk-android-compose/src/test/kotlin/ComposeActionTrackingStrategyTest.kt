/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose

import android.view.View
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.platform.AndroidComposeView
import com.datadog.android.compose.internal.ComposeActionTrackingStrategy
import com.datadog.android.compose.internal.utils.LayoutNodeUtils
import com.datadog.android.rum.tracking.ViewTarget
import com.datadog.tools.unit.forge.BaseConfigurator
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComposeActionTrackingStrategyTest {

    private lateinit var testedComposeActionTrackingStrategy: ComposeActionTrackingStrategy

    @Mock
    private lateinit var mockView: View

    @Mock
    private lateinit var mockAndroidComposeView: AndroidComposeView

    @Mock
    private lateinit var mockLayoutNodeUtils: LayoutNodeUtils

    @BeforeEach
    fun `set up`() {
        testedComposeActionTrackingStrategy = ComposeActionTrackingStrategy(
            layoutNodeUtils = mockLayoutNodeUtils
        )
    }

    @Test
    fun `M return null W find tap target in decorView {not a compose owner}`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()

        // When
        val result = testedComposeActionTrackingStrategy.findTargetForTap(mockView, x, y)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W find scroll target in decorView {not a compose owner}`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()

        // When
        val result = testedComposeActionTrackingStrategy.findTargetForScroll(mockView, x, y)

        // Then
        assertThat(result).isNull()
    }

    /**
     *           0
     *         /   \
     *        1(c)  2
     *      /     \
     *     3(c)   4
     */
    @Test
    fun `M return bottom-most clickable node W findTargetForTap`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()
        mockLayoutNodeTree(
            forge = forge,
            targetX = x,
            targetY = y,
            hitIndexes = listOf(1, 3),
            clickableIndexes = listOf(1, 3)
        )

        // When
        val result =
            testedComposeActionTrackingStrategy.findTargetForTap(mockAndroidComposeView, x, y)

        // Then
        assertThat(result).isEqualTo(ViewTarget(view = null, tag = "node3"))
    }

    /**
     *           0
     *         /  \
     *       1(c)  2
     *      /    \
     *     3(c)   4
     */
    @Test
    fun `M return bottom-most scrollable node W findTargetForScroll`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()
        mockLayoutNodeTree(
            forge = forge,
            targetX = x,
            targetY = y,
            hitIndexes = listOf(1, 3),
            scrollableIndexes = listOf(1, 3)
        )

        // When
        val result =
            testedComposeActionTrackingStrategy.findTargetForScroll(mockAndroidComposeView, x, y)

        // Then
        assertThat(result).isEqualTo(ViewTarget(view = null, tag = "node3"))
    }

    /**
     *           0
     *         /   \
     *       1(s)   2
     *      /   \
     *     3(c)  4
     */
    @Test
    fun `M return clickable node inside a scrollable parent W findTargetForTap`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()
        mockLayoutNodeTree(
            forge = forge,
            targetX = x,
            targetY = y,
            hitIndexes = listOf(1, 3),
            clickableIndexes = listOf(3),
            scrollableIndexes = listOf(1)
        )

        // When
        val result =
            testedComposeActionTrackingStrategy.findTargetForTap(mockAndroidComposeView, x, y)

        // Then
        assertThat(result).isEqualTo(ViewTarget(view = null, tag = "node3"))
    }

    /**
     *           0
     *         /   \
     *       1(c)   2
     *      /    \
     *     3(s)   4
     */
    @Test
    fun `M return scrollable node inside a clickable parent W findTargetForScroll`(forge: Forge) {
        // Given
        val x = forge.aFloat(
            min = 0f,
            max = XY_MAX_VALUE
        )
        val y = forge.mockSmallFloat()
        mockLayoutNodeTree(
            forge = forge,
            targetX = x,
            targetY = y,
            hitIndexes = listOf(1, 3),
            clickableIndexes = listOf(1),
            scrollableIndexes = listOf(3)
        )

        // When
        val result =
            testedComposeActionTrackingStrategy.findTargetForScroll(mockAndroidComposeView, x, y)

        // Then
        assertThat(result).isEqualTo(ViewTarget(view = null, tag = "node3"))
    }

    /**
     *          0
     *        /  \
     *       1    2
     *      /  \
     *     3   4(h)
     */
    @Test
    fun `M return hit node inside W findTargetForTap`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()
        mockLayoutNodeTree(
            forge = forge,
            targetX = x,
            targetY = y,
            hitIndexes = listOf(4),
            clickableIndexes = listOf(3, 4)
        )

        // When
        val result =
            testedComposeActionTrackingStrategy.findTargetForTap(mockAndroidComposeView, x, y)

        // Then
        assertThat(result).isEqualTo(ViewTarget(view = null, tag = "node4"))
    }

    /**
     *          0
     *        /  \
     *       1    2
     *      /  \
     *     3   4(h)
     */
    @Test
    fun `M return hit node inside W findTargetForScroll`(forge: Forge) {
        // Given
        val x = forge.mockSmallFloat()
        val y = forge.mockSmallFloat()
        mockLayoutNodeTree(
            forge = forge,
            targetX = x,
            targetY = y,
            hitIndexes = listOf(4),
            scrollableIndexes = listOf(3, 4)
        )

        // When
        val result =
            testedComposeActionTrackingStrategy.findTargetForScroll(mockAndroidComposeView, x, y)

        // Then
        assertThat(result).isEqualTo(ViewTarget(view = null, tag = "node4"))
    }

    /**
     * This function will build up a mock layout node tree with following structure:
     *         0
     *        / \
     *       1   2
     *      / \
     *     3   4
     * Each node can be individually mocked as clickable or scrollable target, and also
     * if it should be the target hit by the given coordinates.
     */
    private fun mockLayoutNodeTree(
        forge: Forge,
        targetX: Float,
        targetY: Float,
        hitIndexes: List<Int> = emptyList(),
        clickableIndexes: List<Int> = emptyList(),
        scrollableIndexes: List<Int> = emptyList()
    ) {
        val nodeList = mutableListOf<LayoutNode>()
        for (i in 0..5) {
            nodeList.add(
                mockLayoutNode(
                    forge = forge,
                    tagName = "node$i",
                    shouldBeHit = hitIndexes.contains(i),
                    isClickable = clickableIndexes.contains(i),
                    isScrollable = scrollableIndexes.contains(i),
                    targetX = targetX,
                    targetY = targetY
                )
            )
        }
        whenever(nodeList[0].zSortedChildren) doReturn mutableVectorOf(nodeList[1], nodeList[2])
        whenever(nodeList[1].zSortedChildren) doReturn mutableVectorOf(nodeList[3], nodeList[4])
        whenever(nodeList[2].zSortedChildren) doReturn mutableVectorOf()
        whenever(nodeList[3].zSortedChildren) doReturn mutableVectorOf()
        whenever(nodeList[4].zSortedChildren) doReturn mutableVectorOf()
        whenever(mockAndroidComposeView.root) doReturn nodeList[0]
    }

    private fun mockLayoutNode(
        forge: Forge,
        tagName: String,
        isClickable: Boolean = false,
        isScrollable: Boolean = false,
        targetX: Float = 0f,
        targetY: Float = 0f,
        shouldBeHit: Boolean = false
    ): LayoutNode {
        val node = mock<LayoutNode> {
            whenever(it.isPlaced) doReturn true
        }
        whenever(mockLayoutNodeUtils.resolveLayoutNode(node)) doReturn
            LayoutNodeUtils.TargetNode(
                tag = tagName,
                isScrollable = isScrollable,
                isClickable = isClickable
            )
        val mockRect = if (shouldBeHit) {
            Rect(
                left = targetX - forge.anInt(min = 1, max = 10),
                right = targetX + forge.anInt(min = 1, max = 10),
                top = targetY - forge.anInt(min = 1, max = 10),
                bottom = targetY + forge.anInt(min = 1, max = 10)
            )
        } else {
            Rect(
                left = targetX + forge.anInt(min = 1, max = 10),
                right = targetX + forge.anInt(min = 10, max = 20),
                top = targetY + forge.anInt(min = 1, max = 10),
                bottom = targetY + forge.anInt(min = 10, max = 20)
            )
        }
        whenever(mockLayoutNodeUtils.getLayoutNodeBoundsInWindow(node)) doReturn mockRect
        return node
    }

    private fun Forge.mockSmallFloat(): Float {
        return this.aFloat(0f, XY_MAX_VALUE)
    }

    companion object {
        private const val XY_MAX_VALUE = 1000f
    }
}
