/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.junit4.ForgeRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class ComposeScrollingRecordingTest(orientation: Orientation) {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val forge = ForgeRule()

    private val vertical = orientation == Orientation.VERTICAL

    @Test
    fun `M_omitOriginDuplicates_W_createComposeWireframes_duringAnimatedScroll`() {
        // Given
        val bitmap = Bitmap.createBitmap(
            forge.anInt(min = 16, max = 64),
            forge.anInt(min = 16, max = 64),
            Bitmap.Config.ARGB_8888
        ).apply { eraseColor(forge.anInt() or Color.BLACK) }.asImageBitmap()
        val testedRecorder = ComposeRecordingTestFixture(InstrumentationRegistry.getInstrumentation().targetContext)
        lateinit var animateTo: (Int) -> Job
        lateinit var listState: LazyListState
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState = state
            val scope = rememberCoroutineScope()
            animateTo = { index -> scope.launch { state.animateScrollToItem(index) } }
            val modifier = Modifier.fillMaxSize().testTag("list")
            if (vertical) {
                LazyColumn(modifier, state) {
                    items(10) { index ->
                        Image(
                            bitmap,
                            "photo-$index",
                            Modifier.fillParentMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
            } else {
                LazyRow(modifier, state) {
                    items(10) { index ->
                        Image(
                            bitmap,
                            "photo-$index",
                            Modifier.fillParentMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
            }
        }
        val listNode = composeTestRule.onNodeWithTag("list", useUnmergedTree = true).fetchSemanticsNode()
        val root = generateSequence(listNode) { it.parent }.last()
        val frames = mutableListOf<CapturedFrame>()
        fun capture() {
            val visibleItems = listState.layoutInfo.visibleItemsInfo.associateBy { it.index }
            val imageNodes = descendants(root).filter {
                it.config.contains(SemanticsProperties.ContentDescription)
            }
            val density = root.layoutInfo.density.density
            frames.add(
                CapturedFrame(
                    wireframes = testedRecorder.map(root),
                    originIds = imageNodes.filter { it.positionInRoot == Offset.Zero }.map { it.id.toLong() },
                    positions = imageNodes.mapNotNull { node ->
                        val index = checkNotNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
                            .single().removePrefix("photo-").toInt()
                        visibleItems[index]?.let { node.id.toLong() to (it.offset / density).toLong() }
                    }.toMap()
                )
            )
        }
        composeTestRule.mainClock.autoAdvance = false

        // When
        composeTestRule.runOnIdle { capture() }
        listOf(4, 0).forEach { index ->
            val animation = composeTestRule.runOnIdle { animateTo(index) }
            var frameCount = 0
            while (!animation.isCompleted && frameCount < 120) {
                composeTestRule.mainClock.advanceTimeByFrame()
                composeTestRule.waitForIdle()
                composeTestRule.runOnIdle { capture() }
                frameCount++
            }
            assertThat(animation.isCompleted).describedAs("Scroll animation must finish within 120 frames").isTrue()
        }

        // Then
        assertThat(frames.size).isGreaterThan(2)
        val allWireframes = frames.flatMap { it.wireframes }
        assertThat(allWireframes).describedAs("Capture images while they are between scroll boundaries").isNotEmpty()
        assertThat(allWireframes.map { it.id }.distinct().size).isGreaterThan(1)
        assertThat(frames.sumOf { frame -> frame.wireframes.count { frame.positions.containsKey(it.id) } })
            .describedAs("Check recorded coordinates against visible list layout positions").isGreaterThan(1)
        assertThat(
            frames.flatMap {
                it.originIds
            }
        ).describedAs("Exercise the origin guard during scrolling").isNotEmpty()
        frames.forEachIndexed { index, frame ->
            val wireframes = frame.wireframes
            assertThat(wireframes.map { it.id }).describedAs("No duplicated image IDs in frame $index")
                .doesNotHaveDuplicates()
            assertThat(wireframes.map { it.id }.intersect(frame.originIds.toSet()))
                .describedAs("RUM-9030: no origin image artifacts in frame $index").isEmpty()
            wireframes.forEach { wireframe ->
                frame.positions[wireframe.id]?.let { expectedPosition ->
                    assertThat(if (vertical) wireframe.y else wireframe.x)
                        .describedAs("Visible item position in frame $index").isCloseTo(expectedPosition, offset(1L))
                }
                assertThat(if (vertical) wireframe.x else wireframe.y).isZero()
                assertThat(wireframe.isEmpty).isFalse()
                assertThat(testedRecorder.resources).containsKey(wireframe.resourceId)
            }
        }
        assertThat(testedRecorder.pendingJobs).isZero()
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap { descendants(it) }

    private class CapturedFrame(
        val wireframes: List<MobileSegment.Wireframe.ImageWireframe>,
        val originIds: List<Long>,
        val positions: Map<Long, Long>
    )

    internal enum class Orientation {
        VERTICAL,
        HORIZONTAL
    }

    private companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun orientations(): List<Array<Orientation>> = Orientation.values().map { arrayOf(it) }
    }
}
