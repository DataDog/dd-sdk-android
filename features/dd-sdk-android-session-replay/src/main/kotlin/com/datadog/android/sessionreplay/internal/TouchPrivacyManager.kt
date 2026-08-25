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

    // Built during the view traversal and copied to currentOverrideAreas at the end.
    // ThreadLocal because each window's onDraw pass rebuilds this on its own thread: a shared
    // map would let one pass's clear() wipe another's still-in-progress entries.
    // initialValue() (not withInitial(), which needs API 26) keeps this working below minSdk.
    private val nextOverrideAreas = object : ThreadLocal<HashMap<Rect, TouchPrivacy>>() {
        override fun initialValue(): HashMap<Rect, TouchPrivacy> = HashMap()
    }

    // ThreadLocal#get() is a platform type from Kotlin's view; initialValue() always supplies a
    // value, but falling back to a fresh map here (never crashing) is safer than asserting it.
    private fun currentPass(): HashMap<Rect, TouchPrivacy> {
        val existing = nextOverrideAreas.get()
        if (existing != null) return existing
        val fresh = HashMap<Rect, TouchPrivacy>()
        nextOverrideAreas.set(fresh)
        return fresh
    }

    // Each recorded window runs the snapshot pipeline on the Looper thread its view hierarchy is
    // attached to. This could not be the main thread so currentOverrideAreas can be accessed
    // concurrently.
    private val lock = Any()

    /**
     * Adds touch area with [TouchPrivacy] override.
     */
    @UiThread
    fun addTouchOverrideArea(bounds: Rect, touchPrivacy: TouchPrivacy) {
        currentPass()[bounds] = touchPrivacy
    }

    @UiThread
    internal fun updateCurrentTouchOverrideAreas() {
        val builtByThisPass = currentPass()
        synchronized(lock) {
            currentOverrideAreas.clear()
            // NPE cannot happen here
            @Suppress("UnsafeThirdPartyFunctionCall")
            currentOverrideAreas.putAll(builtByThisPass)
        }
        builtByThisPass.clear()
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
        // NPE cannot happen here
        @Suppress("UnsafeThirdPartyFunctionCall")
        return HashMap(currentPass())
    }
}
