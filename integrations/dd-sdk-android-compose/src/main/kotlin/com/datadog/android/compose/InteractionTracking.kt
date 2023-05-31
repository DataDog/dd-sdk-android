/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalMaterialApi::class, ExperimentalTrackingApi::class)

package com.datadog.android.compose

import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.android.v2.api.SdkCore
import kotlinx.coroutines.flow.collect
import java.lang.Exception
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Creates a proxy around click listener, which will report clicks to Datadog.
 *
 * @param sdkCore the SDK instance to use.
 * @param targetName Name of the click target.
 * @param attributes Additional custom attributes to attach to the action. Attributes can be
 * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
 * @param onClick Click listener.
 */
@ExperimentalTrackingApi
@Composable
fun trackClick(
    sdkCore: SdkCore,
    targetName: String,
    attributes: Map<String, Any?> = remember { emptyMap() },
    onClick: () -> Unit
): () -> Unit {
    val onTapState = rememberUpdatedState(newValue = onClick)
    return remember(targetName, attributes) {
        TapActionTracker(targetName, attributes, onTapState, GlobalRum.get(sdkCore))
    }
}

/**
 * When [TrackInteractionEffect] enters composition, it will start tracking interactions (swipe or
 * scroll) emitted by the given interaction source in the composition's [CoroutineContext].
 * Tracking will be cancelled once effect leaves the composition.
 *
 * For tracking clicks check [trackClick].
 *
 * @param sdkCore the SDK instance to use.
 * @param targetName Name of the tracking target.
 * @param interactionSource [InteractionSource] which hosts the flow of interactions happening.
 * @param interactionType Type of the interaction, either [InteractionType.Scroll]
 * or [InteractionType.Swipe]
 * @param attributes Additional custom attributes to attach to the action. Attributes can be
 * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
 */
@ExperimentalTrackingApi
@Composable
fun TrackInteractionEffect(
    sdkCore: SdkCore,
    targetName: String,
    interactionSource: InteractionSource,
    interactionType: InteractionType,
    attributes: Map<String, Any?> = emptyMap()
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(interactionSource, interactionType, isRtl) {
        val rumMonitor = GlobalRum.get(sdkCore)
        when (interactionType) {
            is InteractionType.Swipe<*> -> trackSwipe(
                rumMonitor,
                targetName,
                interactionSource,
                interactionType,
                isRtl,
                attributes
            )
            is InteractionType.Scroll -> trackScroll(
                rumMonitor,
                targetName,
                interactionSource,
                interactionType,
                isRtl,
                attributes
            )
        }
    }
}

/**
 * Type of the interaction, either swipe or scroll.
 */
sealed class InteractionType {

    /**
     * Swipe interaction type.
     *
     * @param T the type of the state
     * @param swipeableState Instance of [SwipeableState] to query for the current values
     * of interaction.
     * @param orientation The orientation in which the swipeable can be swiped.
     * @param reverseDirection Whether the direction of the swipe is reversed. Tracking
     * automatically supports RTL layout direction, so this parameter needs to be set only if
     * result of the swipe is not natural for the current layout direction (element moves in
     * the opposite direction to the finger gesture).
     */
    class Swipe<T : Any>(
        internal val swipeableState: SwipeableState<T>,
        internal val orientation: Orientation,
        internal val reverseDirection: Boolean = false
    ) : InteractionType()

    /**
     * Scroll interaction type.
     *
     * @param scrollableState Instance of [ScrollableState] to query for the current values
     * of interaction.
     * @param orientation The orientation in which the scrollable can be scrolled.
     * @param reverseDirection Whether the direction of the scroll (and layout) is reversed,
     * same as the value of [Modifier.verticalScroll] or [Modifier.horizontalScroll], for example.
     * Tracking automatically supports RTL layout direction, so this parameter needs to be set only
     * if scrolling/layout is made in the direction which is not natural for the current layout
     * direction.
     */
    class Scroll(
        internal val scrollableState: ScrollableState,
        internal val orientation: Orientation,
        internal val reverseDirection: Boolean = false
    ) : InteractionType()
}

// region Internal

internal class TapActionTracker(
    private val targetName: String,
    private val attributes: Map<String, Any?> = emptyMap(),
    private val onTap: State<() -> Unit>,
    private val rumMonitor: RumMonitor
) : () -> Unit {
    override fun invoke() {
        rumMonitor.addUserAction(
            RumActionType.TAP,
            targetName,
            attributes + mapOf(
                RumAttributes.ACTION_TARGET_TITLE to targetName
            )
        )
        // that is user code, not ours
        @Suppress("UnsafeThirdPartyFunctionCall")
        onTap.value.invoke()
    }
}

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
                interactionType.swipeableState.currentValue,
                // roundToInt can throw exception for Float.NaN, but we won't get such value
                @Suppress("UnsafeThirdPartyFunctionCall")
                interactionType.swipeableState.offset.value.roundToInt()
            )
            rumMonitor.startUserAction(RumActionType.SWIPE, targetName, emptyMap())
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
    trackDragInteraction<ScrollStartProps>(
        interactionSource,
        onStart = { interactions, start ->
            interactions[start] =
                ScrollStartProps(
                    interactionType.scrollableState.currentPosition
                )
            rumMonitor.startUserAction(RumActionType.SCROLL, targetName, emptyMap())
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

// endregion

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
    swipeableState: SwipeableState<*>,
    orientation: Orientation,
    reverseDirection: Boolean,
    isRtl: Boolean
): Map<String, Any?> {
    // normally should use .direction property of SwipeableState, but it is affected by the
    // same bug as described below
    // roundToInt can throw exception for Float.NaN, but we won't get such value
    @Suppress("UnsafeThirdPartyFunctionCall")
    val directionSign = swipeableState.offset.value.roundToInt() - swipeStartProps.offset
    val direction = resolveDragDirection(
        if (reverseDirection) -directionSign else directionSign,
        orientation,
        isRtl
    )

    return mapOf(
        RumAttributes.ACTION_GESTURE_FROM_STATE to swipeStartProps.anchorState,
        // https://issuetracker.google.com/issues/149549482
        // There is a Compose bug: if drag stopped (pointer up) and threshold for the next value is
        // not yet reached, but there is enough velocity to continue the fling, this will
        // still report current value, this affects reporting direction as well
        RumAttributes.ACTION_GESTURE_TO_STATE to swipeableState.targetValue,
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

@Suppress("LongParameterList")
private fun reportSwipeInteraction(
    rumMonitor: RumMonitor,
    targetName: String,
    startProps: SwipeStartProps,
    interaction: InteractionType.Swipe<*>,
    isRtl: Boolean,
    attributes: Map<String, Any?>
) {
    rumMonitor.stopUserAction(
        RumActionType.SWIPE,
        targetName,
        attributes.toMutableMap().apply {
            put(RumAttributes.ACTION_TARGET_TITLE, targetName)
            putAll(
                resolveSwipeChangeAttributes(
                    startProps,
                    interaction.swipeableState,
                    interaction.orientation,
                    interaction.reverseDirection,
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
    rumMonitor.stopUserAction(
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
