/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.State
import com.datadog.android.compose.ExperimentalTrackingApi
import com.datadog.android.compose.InteractionType
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import com.datadog.tools.unit.forge.anException
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.stream.Stream

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@OptIn(ExperimentalMaterialApi::class, ExperimentalTrackingApi::class)
class SwipeAndScrollActionTrackerTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockInteractionSource: InteractionSource

    @StringForgery
    lateinit var fakeTargetName: String

    @MapForgery(
        key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
        value = AdvancedForgery(string = [StringForgery(StringForgeryType.ASCII)])
    )
    lateinit var fakeAttributes: Map<String, String>

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M report SWIPE action W trackSwipe { Start - Stop }`(
        swipeDirection: Direction,
        reverseDirection: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            val start = DragInteraction.Start()
            emit(start)
            emit(DragInteraction.Stop(start))
        }

        val swipeOrientation = swipeDirection.orientation

        val startState = forge.anInt()
        val endState = forge.anInt()

        val swipeableState =
            forge.aSwipeableState(startState, endState, swipeDirection, reverseDirection, isRtl)

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        val expectedAttributes = fakeAttributes +
            mapOf(
                RumAttributes.ACTION_TARGET_TITLE to fakeTargetName,
                RumAttributes.ACTION_GESTURE_DIRECTION
                    to swipeDirection.name.lowercase(Locale.US),
                RumAttributes.ACTION_GESTURE_FROM_STATE to startState,
                RumAttributes.ACTION_GESTURE_TO_STATE to endState
            )

        // When
        runBlocking {
            trackSwipe(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Swipe(swipeableState, swipeOrientation, reverseDirection),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor)
                .startAction(RumActionType.SWIPE, fakeTargetName, emptyMap())
            verify(mockRumMonitor)
                .stopAction(RumActionType.SWIPE, fakeTargetName, expectedAttributes)
        }
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M report SWIPE action W trackSwipe { Start - Cancel }`(
        swipeDirection: Direction,
        reverseDirection: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            val start = DragInteraction.Start()
            emit(start)
            emit(DragInteraction.Cancel(start))
        }

        val swipeOrientation = swipeDirection.orientation

        val startState = forge.anInt()
        val endState = forge.anInt()

        val swipeableState =
            forge.aSwipeableState(startState, endState, swipeDirection, reverseDirection, isRtl)

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        val expectedAttributes = fakeAttributes +
            mapOf(
                RumAttributes.ACTION_TARGET_TITLE to fakeTargetName,
                RumAttributes.ACTION_GESTURE_DIRECTION
                    to swipeDirection.name.lowercase(Locale.US),
                RumAttributes.ACTION_GESTURE_FROM_STATE to startState,
                RumAttributes.ACTION_GESTURE_TO_STATE to endState
            )

        // When
        runBlocking {
            trackSwipe(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Swipe(swipeableState, swipeOrientation, reverseDirection),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor)
                .startAction(RumActionType.SWIPE, fakeTargetName, emptyMap())
            verify(mockRumMonitor)
                .stopAction(RumActionType.SWIPE, fakeTargetName, expectedAttributes)
        }
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M not report SWIPE stop action W trackSwipe { Start - Start }`(
        swipeDirection: Direction,
        reverseDirection: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            emit(DragInteraction.Start())
            emit(DragInteraction.Start())
        }

        val swipeOrientation = swipeDirection.orientation

        val startState = forge.anInt()
        val endState = forge.anInt()

        val swipeableState =
            forge.aSwipeableState(startState, endState, swipeDirection, reverseDirection, isRtl)

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        // When
        runBlocking {
            trackSwipe(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Swipe(swipeableState, swipeOrientation, reverseDirection),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        verify(mockRumMonitor, times(2))
            .startAction(RumActionType.SWIPE, fakeTargetName, emptyMap())
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M not report SWIPE stop action W trackSwipe { Start - Stop for another Start }`(
        swipeDirection: Direction,
        reverseDirection: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            emit(DragInteraction.Start())
            emit(DragInteraction.Stop(DragInteraction.Start()))
        }

        val swipeOrientation = swipeDirection.orientation

        val startState = forge.anInt()
        val endState = forge.anInt()

        val swipeableState =
            forge.aSwipeableState(startState, endState, swipeDirection, reverseDirection, isRtl)

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        // When
        runBlocking {
            trackSwipe(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Swipe(swipeableState, swipeOrientation, reverseDirection),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        verify(mockRumMonitor)
            .startAction(RumActionType.SWIPE, fakeTargetName, emptyMap())
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M report SCROLL action W trackScroll { Start - Stop }`(
        scrollDirection: Direction,
        reverseLayout: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            val start = DragInteraction.Start()
            emit(start)
            emit(DragInteraction.Stop(start))
        }

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        val scrollOrientation = scrollDirection.orientation

        val mockScrollableState = forge.aScrollableState(scrollDirection, reverseLayout, isRtl)

        val expectedAttributes = fakeAttributes.run {
            val map = this.toMutableMap()
            map += RumAttributes.ACTION_TARGET_TITLE to fakeTargetName
            if (mockScrollableState is LazyListState || mockScrollableState is ScrollState) {
                map += RumAttributes.ACTION_GESTURE_DIRECTION to scrollDirection.name.lowercase(
                    Locale.US
                )
            }
            map
        }

        // When
        runBlocking {
            trackScroll(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Scroll(mockScrollableState, scrollOrientation, reverseLayout),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor)
                .startAction(RumActionType.SCROLL, fakeTargetName, emptyMap())
            verify(mockRumMonitor)
                .stopAction(RumActionType.SCROLL, fakeTargetName, expectedAttributes)
        }
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M report SCROLL action W trackScroll { Start - Cancel }`(
        scrollDirection: Direction,
        reverseLayout: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            val start = DragInteraction.Start()
            emit(start)
            emit(DragInteraction.Cancel(start))
        }

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        val scrollOrientation = scrollDirection.orientation

        val mockScrollableState = forge.aScrollableState(scrollDirection, reverseLayout, isRtl)

        val expectedAttributes = fakeAttributes.run {
            val map = toMutableMap()
            map += RumAttributes.ACTION_TARGET_TITLE to fakeTargetName
            if (mockScrollableState is LazyListState || mockScrollableState is ScrollState) {
                map += RumAttributes.ACTION_GESTURE_DIRECTION to scrollDirection.name.lowercase(
                    Locale.US
                )
            }
            map
        }

        // When
        runBlocking {
            trackScroll(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Scroll(mockScrollableState, scrollOrientation, reverseLayout),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        inOrder(mockRumMonitor) {
            verify(mockRumMonitor)
                .startAction(RumActionType.SCROLL, fakeTargetName, emptyMap())
            verify(mockRumMonitor)
                .stopAction(RumActionType.SCROLL, fakeTargetName, expectedAttributes)
        }
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M not report SCROLL stop action W trackScroll { Start - Start }`(
        scrollDirection: Direction,
        reverseLayout: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            emit(DragInteraction.Start())
            emit(DragInteraction.Start())
        }

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        val scrollOrientation = scrollDirection.orientation

        val mockScrollableState = forge.aScrollableState(scrollDirection, reverseLayout, isRtl)

        // When
        runBlocking {
            trackScroll(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Scroll(mockScrollableState, scrollOrientation, reverseLayout),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        verify(mockRumMonitor, times(2))
            .startAction(RumActionType.SCROLL, fakeTargetName, emptyMap())
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @ParameterizedTest
    @MethodSource("directionToReverseToRtl")
    fun `M not report SCROLL stop action W trackScroll { Start - Stop for another Start }`(
        scrollDirection: Direction,
        reverseLayout: Boolean,
        isRtl: Boolean,
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            emit(DragInteraction.Start())
            emit(DragInteraction.Stop(DragInteraction.Start()))
        }

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        val scrollOrientation = scrollDirection.orientation

        val mockScrollableState = forge.aScrollableState(scrollDirection, reverseLayout, isRtl)

        // When
        runBlocking {
            trackScroll(
                mockRumMonitor,
                fakeTargetName,
                mockInteractionSource,
                InteractionType.Scroll(mockScrollableState, scrollOrientation, reverseLayout),
                isRtl,
                fakeAttributes
            )
        }

        // Then
        verify(mockRumMonitor)
            .startAction(RumActionType.SCROLL, fakeTargetName, emptyMap())
        verifyNoMoreInteractions(mockRumMonitor)
    }

    @Test
    fun `M rethrow CancellationException W trackDragInteraction { collect is cancelled }`() {
        // CancellationException is a normal way to communicate between the jobs/scopes, so let
        // CancellationException to bubble up
        // Given
        val interactionsFlow = flow {
            emit(DragInteraction.Start())
            currentCoroutineContext().cancel()
        }

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        // When + Then
        assertThrows<CancellationException> {
            runBlocking {
                trackDragInteraction(
                    mockInteractionSource,
                    onStart = { _: Any, _ -> },
                    onStopOrCancel = { _: Any -> }
                )
            }
        }
    }

    @Test
    fun `M not rethrow exception W trackDragInteraction { non-CancellationException }`(
        forge: Forge
    ) {
        // Given
        val interactionsFlow = flow {
            emit(DragInteraction.Start())
            throw forge.anException()
        }

        whenever(mockInteractionSource.interactions) doReturn interactionsFlow

        // When + Then
        assertDoesNotThrow {
            runBlocking {
                trackDragInteraction(
                    mockInteractionSource,
                    onStart = { _: Any, _ -> },
                    onStopOrCancel = { _: Any -> }
                )
            }
        }
    }

    // region private

    private fun Forge.aScrollableState(
        direction: Direction,
        reverseLayout: Boolean,
        isRtl: Boolean
    ): ScrollableState {
        return anElementFrom(
            mock<LazyListState>().apply {
                // LazyList relies on the item index. Scroll DOWN - index decreases, UP - increases.
                // Same logic for the RIGHT/LEFT. If we have reversed layout (first element at
                // the bottom, not at the top, and we start from the bottom), logic is the opposite.
                val insideSameElement = aBool()

                if (insideSameElement) {
                    val index = anInt(min = 0, max = MAX_LAZY_LIST_STATE_ITEM_INDEX)
                    whenever(firstVisibleItemIndex) doReturn index
                    val (startOffset, endOffset) = scrollStartAndStopPoints(
                        direction,
                        reverseLayout,
                        isRtl,
                        max = MAX_LAZY_LIST_STATE_ITEM_OFFSET
                    )
                    whenever(firstVisibleItemScrollOffset) doReturnConsecutively listOf(
                        startOffset,
                        endOffset
                    )
                } else {
                    val (startIndex, endIndex) = scrollStartAndStopPoints(
                        direction,
                        reverseLayout,
                        isRtl,
                        max = MAX_LAZY_LIST_STATE_ITEM_INDEX
                    )
                    whenever(firstVisibleItemIndex) doReturnConsecutively listOf(
                        startIndex,
                        endIndex
                    )
                    whenever(firstVisibleItemScrollOffset) doReturnConsecutively listOf(
                        anInt(min = 0, max = MAX_LAZY_LIST_STATE_ITEM_OFFSET),
                        anInt(min = 0, max = MAX_LAZY_LIST_STATE_ITEM_OFFSET)
                    )
                }
            },
            mock<ScrollState>().apply {
                // for ScrollState it is overall scroll offset, unlike for LazyList,
                // where it is index
                val (startOffset, endOffset) = scrollStartAndStopPoints(
                    direction,
                    reverseLayout,
                    isRtl
                )
                whenever(value) doReturnConsecutively listOf(startOffset, endOffset)
            },
            mock()
        )
    }

    private fun <T> Forge.aSwipeableState(
        startState: T,
        endState: T,
        direction: Direction,
        // if true, this will make element move left if we do swipe right, for example
        reverseDirection: Boolean,
        isRtl: Boolean
    ): SwipeableState<T> {
        return mock<SwipeableState<T>>().apply {
            whenever(currentValue) doReturn startState
            whenever(targetValue) doReturn endState

            val (startOffset, endOffset) = twoAscendingPoints().let {
                var points = if (direction == Direction.DOWN || direction == Direction.RIGHT) {
                    it.first to it.second
                } else {
                    it.second to it.first
                }

                if (reverseDirection) {
                    points = points.second to points.first
                }

                if (isRtl && (direction.orientation == Orientation.Horizontal)) {
                    points = points.second to points.first
                }
                points
            }

            val mockOffsetState = mock<State<Float>>()
            whenever(mockOffsetState.value) doReturnConsecutively listOf(
                startOffset.toFloat(),
                endOffset.toFloat()
            )

            whenever(offset) doReturn mockOffsetState
        }
    }

    private fun Forge.scrollStartAndStopPoints(
        direction: Direction,
        reverseLayout: Boolean,
        isRtl: Boolean,
        max: Int = Int.MAX_VALUE
    ): Pair<Int, Int> {
        return twoAscendingPoints(max = max).let {
            var points = if (direction == Direction.DOWN || direction == Direction.RIGHT) {
                it.second to it.first
            } else {
                it.first to it.second
            }

            if (reverseLayout) {
                points = points.second to points.first
            }

            if (isRtl && (direction.orientation == Orientation.Horizontal)) {
                // on RTL right gesture will decrease offset instead of increasing it, same for left
                points = points.second to points.first
            }
            points
        }
    }

    // generates 2 points, second is larger than first
    private fun Forge.twoAscendingPoints(max: Int = Int.MAX_VALUE): Pair<Int, Int> {
        val first = anInt(min = 0, max = max / 2)
        val second = anInt(min = first + 1, max = max)
        return first to second
    }

    @Suppress("unused")
    enum class Direction(val orientation: Orientation) {
        UP(orientation = Orientation.Vertical),
        DOWN(orientation = Orientation.Vertical),
        RIGHT(orientation = Orientation.Horizontal),
        LEFT(orientation = Orientation.Horizontal)
    }

    companion object {
        const val MAX_LAZY_LIST_STATE_ITEM_INDEX = 0xFFFF
        const val MAX_LAZY_LIST_STATE_ITEM_OFFSET = 0x7FFF

        @Suppress("unused")
        @JvmStatic
        fun directionToReverseToRtl(): Stream<Arguments> {
            return Direction.values()
                .flatMap {
                    listOf(
                        Arguments.of(it, true, true),
                        Arguments.of(it, true, false),
                        Arguments.of(it, false, true),
                        Arguments.of(it, false, false)
                    )
                }
                .stream()
        }
    }

    // endregion
}
