/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.view.View
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Strategy interface for tracking user interaction targets within a UI,
 * such as taps and scrolls.
 */
@NoOpImplementation
interface ActionTrackingStrategy : TrackingStrategy {

    /**
     * Finds the target element at the given screen coordinates in response to a tap action.
     *
     * @param view The root [View] to start search from, top-down.
     * @param x The X coordinate of the tap, in screen coordinates.
     * @param y The Y coordinate of the tap, in screen coordinates.
     * @return A [ViewTarget] representing the UI element found at the given location,
     *         or `null` if no target was found.
     */
    fun findTargetForTap(view: View, x: Float, y: Float): ViewTarget?

    /**
     * Finds the target element at the given screen coordinates in response to a scroll action.
     *
     * @param view The root [View] to start search from, top-down.
     * @param x The X coordinate of the scroll event, in screen coordinates.
     * @param y The Y coordinate of the scroll event, in screen coordinates.
     * @return A [ViewTarget] representing the UI element found at the given location,
     *         or `null` if no target was found.
     */
    fun findTargetForScroll(view: View, x: Float, y: Float): ViewTarget?
}
