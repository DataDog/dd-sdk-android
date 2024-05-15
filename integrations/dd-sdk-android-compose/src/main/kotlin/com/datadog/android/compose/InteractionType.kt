/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:OptIn(ExperimentalMaterialApi::class)

package com.datadog.android.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.ui.Modifier

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
