/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View

/**
 * A utility interface to extract a [View]'s bounds relative to the device's screen, and scaled according to
 * the screen's density.
 * This interface is meant for internal usage, please use it carefully.
 */
fun interface ViewBoundsResolver {
    /**
     * Resolves the View bounds in device space, and normalizes them based on the screen density.
     * These dimensions are then normalized according with the current device screen density.
     * Example: if a device has a DPI = 2, the value of the dimension or position is divided by
     * 2 to get a normalized value.
     * @param view the [View]
     * @param pixelsDensity the current device screen density
     * @return the computed view bounds
     */
    // TODO RUM-0000 return an array of primitives here instead of creating an object.
    // This method is being called too often every time we take a screen snapshot
    // and we might want to avoid creating too many instances.
    fun resolveViewGlobalBounds(view: View, pixelsDensity: Float): GlobalBounds
}
