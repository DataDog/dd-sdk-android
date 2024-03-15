/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized

/**
 * View utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object DefaultViewBoundsResolver : ViewBoundsResolver {

    override fun resolveViewGlobalBounds(view: View, pixelsDensity: Float): GlobalBounds {
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
