/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized

/**
 * View utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object ViewUtils {

    /**
     * Resolves the View global bounds and normalizes them based on the screen density.
     * By Global we mean that the View position will not be relative to its parent but to
     * the Device screen.
     * These dimensions are then normalized according with the current device screen density.
     * Example: if a device has a DPI = 2, the value of the dimension or position is divided by
     * 2 to get a normalized value.
     * @param view as [View]
     * @param pixelsDensity as the current device screen density
     * @return the computed view bounds as [GlobalBounds]
     */
    fun resolveViewGlobalBounds(view: View, pixelsDensity: Float): GlobalBounds {
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)
        val x = coordinates[0].densityNormalized(pixelsDensity).toLong()
        val y = coordinates[1].densityNormalized(pixelsDensity).toLong()
        val height = view.height.densityNormalized(pixelsDensity).toLong()
        val width = view.width.densityNormalized(pixelsDensity).toLong()
        return GlobalBounds(x = x, y = y, height = height, width = width)
    }
}
