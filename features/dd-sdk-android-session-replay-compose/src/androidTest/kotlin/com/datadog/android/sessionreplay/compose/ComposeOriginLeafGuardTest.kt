/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.viewinterop.AndroidView
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class ComposeOriginLeafGuardTest(private val scenario: Scenario) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val forge = ForgeRule()

    @IntForgery
    private var fakePhotoColor: Int = 0

    private val testedSemanticsUtils = SemanticsUtils()

    @Test
    fun `M_preserveScrollingGuard_W_isNodePositionUnavailable`() {
        // Given
        composeTestRule.setContent {
            when (scenario) {
                Scenario.STANDALONE -> Photo(0, Modifier.fillMaxSize())
                Scenario.LAZY_COLUMN -> LazyColumn(Modifier.fillMaxSize().testTag(LIST_TAG)) {
                    items(ITEM_COUNT) { index -> Photo(index, Modifier.fillParentMaxSize()) }
                }
                Scenario.LAZY_ROW -> LazyRow(Modifier.fillMaxSize().testTag(LIST_TAG)) {
                    items(ITEM_COUNT) { index -> Photo(index, Modifier.fillParentMaxSize()) }
                }
                Scenario.NATIVE_SCROLL_VIEW -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ScrollView(context).apply {
                            addView(
                                LinearLayout(context).apply {
                                    orientation = LinearLayout.VERTICAL
                                    repeat(ITEM_COUNT) { index ->
                                        addView(
                                            ComposeView(context).apply {
                                                setContent { Photo(index, Modifier.fillMaxSize()) }
                                            },
                                            LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                CARD_HEIGHT_PX
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
        val indices = if (scenario.isComposeScroll) listOf(0, 4, 9, 1, 0) else listOf(0)

        indices.forEach { index ->
            if (scenario.isComposeScroll) {
                composeTestRule.onNodeWithTag(LIST_TAG).performScrollToIndex(index)
            }
            val node = composeTestRule.onNodeWithTag("photo-$index", useUnmergedTree = true).fetchSemanticsNode()
            composeTestRule.runOnIdle {
                // When
                val unavailable = testedSemanticsUtils.isNodePositionUnavailable(node)

                // Then
                assertThat(node.children).isEmpty()
                assertThat(node.positionInRoot).isEqualTo(Offset.Zero)
                assertThat(node.layoutInfo.isAttached).isTrue()
                assertThat(node.layoutInfo.isPlaced).isTrue()
                assertThat(node.boundsInRoot.isEmpty).isFalse()
                val ancestors = generateSequence(node) { it.parent }.toList()
                assertThat(node.boundsInRoot).isEqualTo(ancestors.last().boundsInRoot)
                assertThat(
                    ancestors.any {
                        it.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
                            it.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
                    }
                ).isEqualTo(scenario.isComposeScroll)
                assertThat(unavailable).isEqualTo(scenario.isComposeScroll)
            }
        }
    }

    @Composable
    private fun Photo(index: Int, modifier: Modifier) {
        Image(
            painter = ColorPainter(Color(fakePhotoColor)),
            contentDescription = null,
            modifier = modifier.testTag("photo-$index").semantics { role = Role.Image }
        )
    }

    internal enum class Scenario(val isComposeScroll: Boolean) {
        STANDALONE(false),
        LAZY_COLUMN(true),
        LAZY_ROW(true),
        NATIVE_SCROLL_VIEW(false)
    }

    private companion object {
        private const val LIST_TAG = "list"
        private const val ITEM_COUNT = 10
        private const val CARD_HEIGHT_PX = 200

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): List<Array<Scenario>> = Scenario.values().map { arrayOf(it) }
    }
}
