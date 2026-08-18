/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.TouchPrivacy
import java.lang.ref.WeakReference

/**
 * The window set the traversal implementation reads when a generation starts. Interception updates
 * it from the looper that discovered the windows, while every window's own UI thread can reach this
 * class through its draw callbacks, so metadata is guarded rather than assumed confined. Reading
 * a dirty touch policy also inspects live Views and must run on the owning (main) UI thread.
 */
@Suppress("TooManyFunctions") // Window membership, privacy revisions and readiness share one lock.
internal class ActiveWindowSource(
    private val readTouchPrivacy: (View) -> Map<Rect, TouchPrivacy>? = WindowTouchPrivacyReader()::read
) {
    private val lock = Any()
    private var windows: List<WeakReference<View>> = emptyList()
    private var version = 0L
    private var touchPrivacyVersion = 0L
    private var drawVersion = 0L
    private var privacyReadyWindows: List<WeakReference<View>> = emptyList()
    private var privacyScope: TouchPrivacyScope? = null
    private var privacyOverrides: List<WindowTouchOverrides> = emptyList()

    /** Returns whether the ordered roots changed; an unchanged discovery refresh keeps captures valid. */
    fun update(views: List<View>): Boolean {
        val references = views.map(::WeakReference)
        return synchronized(lock) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Equal list sizes keep every indexed comparison in bounds.
            val changed = windows.size != views.size || windows.indices.any { windows[it].get() !== views[it] }
            if (changed) {
                version++
                invalidateTouchPrivacy()
            }
            windows = references
            changed
        }
    }

    fun currentWindows(): List<View> =
        synchronized(lock) { windows }.mapNotNull(WeakReference<View>::get)

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(windows.mapNotNull(WeakReference<View>::get), version, touchPrivacyVersion, drawVersion)
    }

    fun isCurrent(snapshot: Snapshot): Boolean = synchronized(lock) { version == snapshot.version }

    fun clear() {
        synchronized(lock) {
            if (windows.isNotEmpty()) version++
            windows = emptyList()
            invalidateTouchPrivacy()
        }
    }

    /** Marks only the drawing window dirty; live hierarchy checks happen on demand before touches. */
    fun onDraw(root: View) {
        synchronized(lock) {
            drawVersion++
            for (entry in privacyOverrides) {
                if (entry.root.get() === root) {
                    entry.dirtyRevision = drawVersion
                }
            }
        }
    }

    /** A draw during traversal leaves its candidate map dirty until a bounded live check succeeds. */
    fun publishTouchPrivacy(
        snapshot: Snapshot,
        views: List<View>,
        scope: RumViewIdentityScope,
        overridesByWindow: Map<View, Map<Rect, TouchPrivacy>> = emptyMap(),
        publishAreas: () -> Unit
    ) {
        synchronized(lock) {
            if (version == snapshot.version && touchPrivacyVersion == snapshot.touchPrivacyVersion) {
                val sameDraw = drawVersion == snapshot.drawVersion
                if (sameDraw) publishAreas()
                val nextOverrides = views.map { view ->
                    val previous = privacyOverrides.firstOrNull { it.root.get() === view }
                    if (!sameDraw && privacyScope?.rumViewScope == scope && previous != null) {
                        previous
                    } else {
                        WindowTouchOverrides(
                            WeakReference(view),
                            overridesByWindow[view].orEmpty().toMap(),
                            if (sameDraw) null else drawVersion
                        )
                    }
                }
                val samePolicy = nextOverrides.size == privacyOverrides.size && nextOverrides.all { next ->
                    privacyOverrides.any { previous ->
                        previous.root.get() === next.root.get() && previous.areas == next.areas
                    }
                }
                if (privacyScope?.rumViewScope != scope || !samePolicy) {
                    touchPrivacyVersion++
                }
                privacyReadyWindows = views.map(::WeakReference)
                privacyOverrides = nextOverrides
                privacyScope = TouchPrivacyScope(scope, touchPrivacyVersion)
            }
        }
    }

    @UiThread
    fun currentTouchPrivacyScope(root: View?, scope: RumViewIdentityScope): TouchPrivacyScope? {
        if (currentTouchOverrideAreas(root, scope) == null) return null
        return synchronized(lock) {
            privacyScope?.takeIf {
                it.rumViewScope == scope && privacyReadyWindows.any { reference -> reference.get() === root }
            }
        }
    }

    @UiThread
    fun hasTouchPrivacy(root: View?, scope: RumViewIdentityScope): Boolean =
        currentTouchPrivacyScope(root, scope) != null

    @Suppress("ReturnCount") // Never expose a dirty map, failed refresh, or changed window/scope.
    @UiThread
    fun currentTouchOverrideAreas(root: View?, scope: RumViewIdentityScope): Map<Rect, TouchPrivacy>? {
        if (root == null) return null
        val (entry, dirtyRevision) = synchronized(lock) {
            if (privacyScope?.rumViewScope != scope) return null
            val entry = privacyOverrides.firstOrNull { it.root.get() === root } ?: return null
            if (entry.dirtyRevision == null) return entry.areas
            entry to entry.dirtyRevision
        }
        // Read live Views outside the membership lock and only on the window's UI thread.
        val refreshed = readTouchPrivacy(root) ?: return null
        return synchronized(lock) {
            if (privacyScope?.rumViewScope != scope || !privacyOverrides.any { it === entry } ||
                entry.dirtyRevision != dirtyRevision
            ) {
                return@synchronized null
            }
            if (entry.areas != refreshed) {
                touchPrivacyVersion++
                privacyScope = TouchPrivacyScope(scope, touchPrivacyVersion)
            }
            entry.areas = refreshed
            entry.dirtyRevision = null
            refreshed
        }
    }

    fun clearTouchPrivacy() {
        synchronized(lock) { invalidateTouchPrivacy() }
    }

    /** The lock guards the capture-wide revision, per-window overrides and readiness together. */
    private fun invalidateTouchPrivacy() {
        touchPrivacyVersion++
        privacyReadyWindows = emptyList()
        privacyScope = null
        privacyOverrides = emptyList()
    }

    private class WindowTouchOverrides(
        val root: WeakReference<View>,
        var areas: Map<Rect, TouchPrivacy>,
        var dirtyRevision: Long?
    )

    internal data class Snapshot(
        val windows: List<View>,
        val version: Long,
        val touchPrivacyVersion: Long,
        val drawVersion: Long
    )
}

/** A gesture may continue only while both its RUM view and captured privacy revision are unchanged. */
internal data class TouchPrivacyScope(val rumViewScope: RumViewIdentityScope, val version: Long)
