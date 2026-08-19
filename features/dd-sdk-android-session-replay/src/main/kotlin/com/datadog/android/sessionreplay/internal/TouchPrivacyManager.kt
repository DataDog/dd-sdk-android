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

    // Each recorded window runs the snapshot pipeline on the Looper thread its view hierarchy is
    // attached to. This could not be the main thread so these maps can be accessed concurrently.
    private val lock = Any()

    /**
     * Adds touch area with [TouchPrivacy] override.
     */
    @UiThread
    fun addTouchOverrideArea(bounds: Rect, touchPrivacy: TouchPrivacy) {
        synchronized(lock) {
            nextOverrideAreas[bounds] = touchPrivacy
        }
    }

    @UiThread
    internal fun updateCurrentTouchOverrideAreas() {
        synchronized(lock) {
            currentOverrideAreas.clear()
            // NPE cannot happen here
            @Suppress("UnsafeThirdPartyFunctionCall")
            currentOverrideAreas.putAll(nextOverrideAreas)
            nextOverrideAreas.clear()
        }
    }

    @UiThread
    internal fun shouldRecordTouch(touchLocation: Point): Boolean {
        var isOverriddenToShowTouch = false

        synchronized(lock) {
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
        }

        return if (isOverriddenToShowTouch) true else globalTouchPrivacy == TouchPrivacy.SHOW
    }

    @VisibleForTesting
    internal fun getCurrentOverrideAreas(): Map<Rect, TouchPrivacy> {
        synchronized(lock) {
            // NPE cannot happen here
            @Suppress("UnsafeThirdPartyFunctionCall")
            return HashMap(currentOverrideAreas)
        }
    }

    @VisibleForTesting
    internal fun getNextOverrideAreas(): Map<Rect, TouchPrivacy> {
        synchronized(lock) {
            // NPE cannot happen here
            @Suppress("UnsafeThirdPartyFunctionCall")
            return HashMap(nextOverrideAreas)
        }
    }
}
