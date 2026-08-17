/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Thread-confined source read by the traversal implementation when a generation starts. */
internal class ActiveWindowSource {
    private var windows: List<WeakReference<View>> = emptyList()

    @MainThread
    fun update(views: List<View>) {
        windows = views.map(::WeakReference)
    }

    @MainThread
    fun currentWindows(): List<View> = windows.mapNotNull(WeakReference<View>::get)

    @MainThread
    fun clear() {
        windows = emptyList()
    }
}

/** Reports which decor views drew or were (re)discovered; owns no traversal, enrichment, queue, or generation state. */
internal fun interface CompositionChangeListener {
    fun onWindowsChanged(windows: List<View>)
}

/** A [CaptureChangeset] of decor views observed to have drawn since it was last drained. */
internal class CompositionChangeset private constructor(
    private val windows: Set<View>
) : CaptureChangeset {
    fun changedWindows(): List<View> = windows.toList()

    override fun isEmpty(): Boolean = windows.isEmpty()

    // Empty means "no information, treat everything as changed" (see CapturedSnapshotProducer), so
    // it must dominate the merge rather than act as an identity value: merging a known window set
    // with an unknown/full invalidation must stay a full invalidation in either direction.
    override fun mergedWith(other: CaptureChangeset): CaptureChangeset = when {
        isEmpty() || other.isEmpty() -> EMPTY
        other is CompositionChangeset -> CompositionChangeset(windows + other.windows)
        else -> other
    }

    companion object {
        val EMPTY: CompositionChangeset = CompositionChangeset(emptySet())

        fun of(windows: List<View>): CompositionChangeset =
            if (windows.isEmpty()) EMPTY else CompositionChangeset(windows.toSet())
    }
}

/** Identifies the decor view it was registered on, since [ViewTreeObserver.OnDrawListener.onDraw] carries no source. */
internal class CompositionOnDrawListener(
    private val view: View,
    private val onWindowsChanged: CompositionChangeListener
) : ViewTreeObserver.OnDrawListener {
    // listOf throws only for a null element; `view` is a non-null Kotlin type.
    @Suppress("UnsafeThirdPartyFunctionCall")
    @MainThread
    override fun onDraw() = onWindowsChanged.onWindowsChanged(listOf(view))
}

internal class CompositionViewOnDrawInterceptor(
    private val windowSource: ActiveWindowSource,
    private val onWindowsChanged: CompositionChangeListener,
    private val internalLogger: InternalLogger
) {
    private val interceptedViews = WeakHashMap<View, ViewTreeObserver.OnDrawListener>()

    @MainThread
    fun intercept(decorViews: List<View>) {
        val removed = interceptedViews.keys.filterNot(decorViews::contains)
        removed.forEach(::removeListener)
        decorViews.filterNot(interceptedViews::containsKey).forEach(::addListener)
        windowSource.update(decorViews)
        onWindowsChanged.onWindowsChanged(decorViews)
    }

    @MainThread
    fun stop() {
        interceptedViews.keys.toList().forEach(::removeListener)
        interceptedViews.clear()
        windowSource.clear()
    }

    private fun addListener(view: View) {
        val observer = view.viewTreeObserver
        if (!observer.isAlive) return
        try {
            val listener = CompositionOnDrawListener(view, onWindowsChanged)
            observer.addOnDrawListener(listener)
            interceptedViews[view] = listener
        } catch (e: IllegalStateException) {
            logListenerFailure("add", e)
        }
    }

    private fun removeListener(view: View) {
        val listener = interceptedViews.remove(view) ?: return
        val observer = view.viewTreeObserver
        if (!observer.isAlive) return
        try {
            observer.removeOnDrawListener(listener)
        } catch (e: IllegalStateException) {
            logListenerFailure("remove", e)
        }
    }

    private fun logListenerFailure(operation: String, error: IllegalStateException) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            { "Unable to $operation composition onDrawListener on viewTreeObserver" },
            error
        )
    }
}
