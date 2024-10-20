/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.graphics.Point
import android.graphics.Rect
import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.TouchPrivacy

internal class TouchPrivacyManager(
    private val globalTouchPrivacy: TouchPrivacy
) {
    // areas on screen where overrides are applied
    @VisibleForTesting internal val currentOverrideSnapshot = HashMap<Rect, TouchPrivacy>()

    // built during the view traversal and copied to currentOverrideSnapshot at the end
    @VisibleForTesting internal val nextOverrideSnapshot = HashMap<Rect, TouchPrivacy>()

    internal fun addTouchOverrideArea(bounds: Rect, touchPrivacy: TouchPrivacy) {
        nextOverrideSnapshot[bounds] = touchPrivacy
    }

    internal fun copyNextSnapshotToCurrentSnapshot() {
        currentOverrideSnapshot.clear()
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        currentOverrideSnapshot.putAll(nextOverrideSnapshot)
        nextOverrideSnapshot.clear()
    }

    internal fun resolveTouchPrivacy(touchLocation: Point): TouchPrivacy {
        var showOverrideSet = false

        // avoid having the collection change while we iterate through it
        val overrideAreas = HashMap<Rect, TouchPrivacy>()
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        overrideAreas.putAll(currentOverrideSnapshot)

        @Suppress("UnsafeThirdPartyFunctionCall") // ConcurrentModification cannot happen here
        overrideAreas.forEach { entry ->
            val area = entry.key
            val overrideValue = entry.value

            if (isWithinOverrideArea(touchLocation, area)) {
                when (overrideValue) {
                    TouchPrivacy.HIDE -> return TouchPrivacy.HIDE
                    TouchPrivacy.SHOW -> showOverrideSet = true
                }
            }
        }

        return if (showOverrideSet) TouchPrivacy.SHOW else globalTouchPrivacy
    }

    private fun isWithinOverrideArea(touchPoint: Point, overrideArea: Rect): Boolean =
        touchPoint.x in overrideArea.left..overrideArea.right &&
            touchPoint.y in overrideArea.top..overrideArea.bottom
}
