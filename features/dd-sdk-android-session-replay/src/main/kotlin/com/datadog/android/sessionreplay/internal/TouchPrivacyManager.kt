/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.graphics.Point
import android.graphics.Rect
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import com.datadog.android.lint.InternalApi
import com.datadog.android.sessionreplay.TouchPrivacy

/**
 * Manager to handle touch privacy area.
 */
@InternalApi
class TouchPrivacyManager(
    private val globalTouchPrivacy: TouchPrivacy
) {
    // areas on screen where overrides are applied
    private val currentOverrideAreas = HashMap<Rect, TouchPrivacy>()

    // Built during the view traversal and copied to currentOverrideAreas at the end
    // We use two hashmaps because touch handling happens in parallel to the view traversal
    // and we don't know which will happen first.
    // Secondly, because we don't want to have to keep track of the lifecycle of the overridden views in order to remove
    // the overrides when they are no longer needed.
    private val nextOverrideAreas = HashMap<Rect, TouchPrivacy>()

    /**
     * Adds touch area with [TouchPrivacy] override.
     */
    @UiThread
    fun addTouchOverrideArea(bounds: Rect, touchPrivacy: TouchPrivacy) {
        nextOverrideAreas[bounds] = touchPrivacy
    }

    @UiThread
    internal fun updateCurrentTouchOverrideAreas() {
        currentOverrideAreas.clear()
        // NPE cannot happen here
        @Suppress("UnsafeThirdPartyFunctionCall")
        currentOverrideAreas.putAll(nextOverrideAreas)
        nextOverrideAreas.clear()
    }

    @UiThread
    internal fun shouldRecordTouch(touchLocation: Point): Boolean {
        var isOverriddenToShowTouch = false

        // Everything is UiThread, so ConcurrentModification cannot happen here
        @Suppress("UnsafeThirdPartyFunctionCall")
        currentOverrideAreas.forEach { entry ->
            val area = entry.key
            val overrideValue = entry.value

            if (area.contains(touchLocation.x, touchLocation.y)) {
                when (overrideValue) {
                    TouchPrivacy.HIDE -> return false
                    TouchPrivacy.SHOW -> isOverriddenToShowTouch = true
                }
            }
        }

        return if (isOverriddenToShowTouch) true else globalTouchPrivacy == TouchPrivacy.SHOW
    }

    @VisibleForTesting
    internal fun getCurrentOverrideAreas(): Map<Rect, TouchPrivacy> {
        return currentOverrideAreas
    }

    @VisibleForTesting
    internal fun getNextOverrideAreas(): Map<Rect, TouchPrivacy> {
        return nextOverrideAreas
    }
}
