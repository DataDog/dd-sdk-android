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
class TouchPrivacyManager private constructor(
    private val globalTouchPrivacy: TouchPrivacy,
    private val overrideAreaSink: ((Rect, TouchPrivacy) -> Unit)?
) {
    constructor(globalTouchPrivacy: TouchPrivacy) : this(globalTouchPrivacy, null)

    /** Keeps extension capture separate from the legacy recorder's staging and published maps. */
    internal fun withOverrideAreaSink(sink: (Rect, TouchPrivacy) -> Unit): TouchPrivacyManager =
        TouchPrivacyManager(globalTouchPrivacy, sink)

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
        val sink = overrideAreaSink
        if (sink == null) currentPass().putTouchOverrideArea(bounds, touchPrivacy) else sink(bounds, touchPrivacy)
    }

    @UiThread
    internal fun updateCurrentTouchOverrideAreas() {
        val builtByThisPass = currentPass()
        replaceCurrentTouchOverrideAreas(builtByThisPass)
        builtByThisPass.clear()
    }

    /** Publishes a completed composition traversal without using the legacy thread-local staging map. */
    @UiThread
    internal fun replaceCurrentTouchOverrideAreas(areas: Map<Rect, TouchPrivacy>) {
        synchronized(lock) {
            currentOverrideAreas.clear()
            // NPE cannot happen here
            @Suppress("UnsafeThirdPartyFunctionCall")
            currentOverrideAreas.putAll(areas)
        }
    }

    @UiThread
    internal fun shouldRecordTouch(touchLocation: Point): Boolean {
        return synchronized(lock) { shouldRecordTouch(touchLocation, currentOverrideAreas) }
    }

    /** Applies the global policy to overrides captured from the window receiving this touch. */
    internal fun shouldRecordTouch(touchLocation: Point, overrideAreas: Map<Rect, TouchPrivacy>): Boolean {
        var isOverriddenToShowTouch = false
        @Suppress("UnsafeThirdPartyFunctionCall")
        overrideAreas.forEach { entry ->
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

/** Equal rectangles must retain the same HIDE precedence as overlapping rectangles. */
internal fun MutableMap<Rect, TouchPrivacy>.putTouchOverrideArea(bounds: Rect, privacy: TouchPrivacy) {
    if (this[bounds] != TouchPrivacy.HIDE) this[bounds] = privacy
}
