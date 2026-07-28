/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.view.View

/**
 * A view's distance from the screen origin, in raw pixels and density-independent pixels.
 *
 * `positionInRoot`/`boundsInRoot` on a [androidx.compose.ui.semantics.SemanticsNode] are relative
 * to the [androidx.compose.ui.platform.AndroidComposeView], not the screen. When Compose is
 * embedded in a non-full-screen host (e.g. a `ComposeView` inside an XML `ScrollView`), this
 * offset must be added to every wireframe position resolved from the semantics tree so it lines
 * up with the rest of the (screen-absolute) Session Replay wireframes.
 */
internal data class ComposeWindowOffset(val xPx: Int, val yPx: Int, val xDp: Long, val yDp: Long) {
    internal companion object {
        internal val NONE = ComposeWindowOffset(xPx = 0, yPx = 0, xDp = 0L, yDp = 0L)
    }
}

/** Screen offset for [this] view, in both raw px (touch areas) and dp (wireframes). */
internal fun View.resolveComposeWindowOffset(density: Float): ComposeWindowOffset {
    val screenPos = IntArray(2)
    @Suppress("UnsafeThirdPartyFunctionCall") // array is always size 2 as required
    getLocationOnScreen(screenPos)
    return ComposeWindowOffset(
        xPx = screenPos[0],
        yPx = screenPos[1],
        xDp = (screenPos[0] / density).toLong(),
        yDp = (screenPos[1] / density).toLong()
    )
}
