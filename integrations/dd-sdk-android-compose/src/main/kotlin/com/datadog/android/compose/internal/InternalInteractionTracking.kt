/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListState
import com.datadog.android.compose.InteractionType
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Suppress("LongParameterList")
internal suspend fun trackSwipe(
    rumMonitor: RumMonitor,
    targetName: String,
    interactionSource: InteractionSource,
    interactionType: InteractionType.Swipe<*>,
    isRtl: Boolean,
    attributes: Map<String, Any?>
) {
    trackDragInteraction<SwipeStartProps>(
        interactionSource,
        onStart = { interactions, start ->
            interactions[start] = SwipeStartProps(
                interactionType.currentValue,
                // roundToInt can throw exception for Float.NaN, but we won't get such value
                @Suppress("UnsafeThirdPartyFunctionCall")
                interactionType.offset.roundToInt()
            )
            rumMonitor.startAction(RumActionType.SWIPE, targetName, emptyMap())
        },
        onStopOrCancel = { startProps ->
            reportSwipeInteraction(
                rumMonitor,
                targetName,
                startProps,
                interactionType,
                isRtl,
                attributes
            )
        }
    )
}

@Suppress("LongParameterList")
internal suspend fun trackScroll(
    rumMonitor: RumMonitor,
    targetName: String,
    interactionSource: InteractionSource,
    interactionType: InteractionType.Scroll,
    isRtl: Boolean,
    attributes: Map<String, Any?>
) {
    trackDragInteraction(
        interactionSource,
        onStart = { interactions, start ->
            interactions[start] =
                ScrollStartProps(
                    interactionType.scrollableState.currentPosition
                )
            rumMonitor.startAction(RumActionType.SCROLL, targetName, emptyMap())
        },
        onStopOrCancel = { startProps ->
            reportScrollInteraction(
                rumMonitor,
                targetName,
                startProps,
                interactionType,
                isRtl,
                attributes
            )
        }
    )
}

internal suspend fun <T> trackDragInteraction(
    interactionSource: InteractionSource,
    onStart: (
        interactions: MutableMap<DragInteraction.Start, T>,
        start: DragInteraction.Start
    ) -> Unit,
    onStopOrCancel: (startProps: T) -> Unit
) {
    val ongoingInteractions = mutableMapOf<DragInteraction.Start, T>()
    try {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    @Suppress("UnsafeThirdPartyFunctionCall")
                    onStart(ongoingInteractions, interaction)
                }

                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val start = when (interaction) {
                        is DragInteraction.Stop -> interaction.start
                        is DragInteraction.Cancel -> interaction.start
                        else -> {
                            // this will never happen, let's just make compiler happy and
                            // give a fake start
                            Log.e(
                                LOG_TAG,
                                "Unexpected branch reached" +
                                    " for the drag interaction start"
                            )
                            DragInteraction.Start()
                        }
                    }

                    ongoingInteractions.remove(start)?.let {
                        @Suppress("UnsafeThirdPartyFunctionCall")
                        onStopOrCancel(it)
                    }
                }
            }
        }
    } catch (ce: CancellationException) {
        @Suppress("ThrowingInternalException")
        throw ce
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.e(LOG_TAG, "Exception during drag interactions tracking", e)
    }
}

// region private

internal const val SHIFT_SIZE = 15
internal const val SHIFT_MASK = 0x7FFF

private val ScrollableState.currentPosition: Int?
    get() {
        return when (this) {
            is LazyListState -> {
                // We may do the scroll, but still be in the current item, so we need more precise
                // tracking. We will pack item index in the first 16 bits, and item offset
                // in another 15 bits (int in Java is using 2's complement) => we have space for
                // 65,535 elements and 32,767 max offset in each. NB: firstVisibleItemScrollOffset
                // is offset relative to the item start, not to the list start.
                (this.firstVisibleItemIndex shl SHIFT_SIZE) or
                    (this.firstVisibleItemScrollOffset and SHIFT_MASK)
            }

            is ScrollState -> {
                this.value
            }

            else -> {
                null
            }
        }
    }

private fun resolveSwipeChangeAttributes(
    swipeStartProps: SwipeStartProps,
    interaction: InteractionType.Swipe<*>,
    isRtl: Boolean
): Map<String, Any?> {
    // normally should use .direction property of SwipeableState, but it is affected by the
    // same bug as described below
    // roundToInt can throw exception for Float.NaN, but we won't get such value
    @Suppress("UnsafeThirdPartyFunctionCall")
    val directionSign = interaction.offset.roundToInt() - swipeStartProps.offset
    val direction = resolveDragDirection(
        if (interaction.reverseDirection) -directionSign else directionSign,
        interaction.orientation,
        isRtl
    )

    return mapOf(
        RumAttributes.ACTION_GESTURE_FROM_STATE to swipeStartProps.anchorState,
        // https://issuetracker.google.com/issues/149549482
        // There is a Compose bug: if drag stopped (pointer up) and threshold for the next value is
        // not yet reached, but there is enough velocity to continue the fling, this will
        // still report current value, this affects reporting direction as well
        RumAttributes.ACTION_GESTURE_TO_STATE to interaction.targetValue,
        RumAttributes.ACTION_GESTURE_DIRECTION to direction
    )
}

private fun resolveScrollChangeAttributes(
    scrollStartProps: ScrollStartProps,
    scrollableState: ScrollableState,
    orientation: Orientation,
    reverseDirection: Boolean,
    isRtl: Boolean
): Map<String, Any?> {
    val startOffset = scrollStartProps.position
    val endOffset = scrollableState.currentPosition

    return if (startOffset != null && endOffset != null) {
        val directionSign = -(endOffset - startOffset)
        mapOf(
            RumAttributes.ACTION_GESTURE_DIRECTION to
                resolveDragDirection(
                    if (reverseDirection) -directionSign else directionSign,
                    orientation,
                    isRtl
                )
        )
    } else {
        emptyMap()
    }
}

private fun resolveDragDirection(
    directionSign: Int,
    orientation: Orientation,
    isRtl: Boolean
): String {
    val isDirectionPositive = directionSign > 0

    return when (orientation) {
        Orientation.Vertical -> if (isDirectionPositive) {
            "down"
        } else {
            "up"
        }

        Orientation.Horizontal -> if (isDirectionPositive) {
            if (!isRtl) "right" else "left"
        } else {
            if (!isRtl) "left" else "right"
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
private fun reportSwipeInteraction(
    rumMonitor: RumMonitor,
    targetName: String,
    startProps: SwipeStartProps,
    interaction: InteractionType.Swipe<*>,
    isRtl: Boolean,
    attributes: Map<String, Any?>
) {
    rumMonitor.stopAction(
        RumActionType.SWIPE,
        targetName,
        attributes.toMutableMap().apply {
            put(RumAttributes.ACTION_TARGET_TITLE, targetName)
            putAll(
                resolveSwipeChangeAttributes(
                    startProps,
                    interaction,
                    isRtl
                )
            )
        }
    )
}

@Suppress("LongParameterList")
private fun reportScrollInteraction(
    rumMonitor: RumMonitor,
    targetName: String,
    startProps: ScrollStartProps,
    interaction: InteractionType.Scroll,
    isRtl: Boolean,
    attributes: Map<String, Any?>
) {
    rumMonitor.stopAction(
        RumActionType.SCROLL,
        targetName,
        attributes.toMutableMap().apply {
            put(RumAttributes.ACTION_TARGET_TITLE, targetName)
            putAll(
                resolveScrollChangeAttributes(
                    startProps,
                    interaction.scrollableState,
                    interaction.orientation,
                    interaction.reverseDirection,
                    isRtl
                )
            )
        }
    )
}

private data class SwipeStartProps(val anchorState: Any, val offset: Int)

private data class ScrollStartProps(val position: Int?)

private const val LOG_TAG = "Datadog-Compose"

// endregion
