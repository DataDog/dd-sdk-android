/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View

/**
 * View utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object DefaultViewBoundsResolver : ViewBoundsResolver {

    override fun resolveViewGlobalBounds(view: View, screenDensity: Float): GlobalBounds {
        val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)
        val x = (coordinates[0] * inverseDensity).toLong()
        val y = (coordinates[1] * inverseDensity).toLong()
        val width = (view.width * inverseDensity).toLong()
        val height = (view.height * inverseDensity).toLong()
        return GlobalBounds(x = x, y = y, width = width, height = height)
    }

    override fun resolveViewPaddedBounds(view: View, screenDensity: Float): GlobalBounds {
        val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity

        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationOnScreen(coordinates)
        val x = ((coordinates[0] + view.paddingLeft) * inverseDensity).toLong()
        val y = ((coordinates[1] + view.paddingTop) * inverseDensity).toLong()
        val width = ((view.width - view.paddingLeft - view.paddingRight) * inverseDensity).toLong()
        val height = ((view.height - view.paddingTop - view.paddingBottom) * inverseDensity).toLong()

        return GlobalBounds(x = x, y = y, width = width, height = height)
    }
}
