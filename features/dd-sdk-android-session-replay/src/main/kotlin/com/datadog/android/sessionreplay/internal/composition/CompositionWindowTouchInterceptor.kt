/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.Context
import android.view.Window
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Wraps and unwraps each window's [Window.Callback] with a [CompositionWindowTouchCallback].
 * [AndroidSnapshotCaptureLifecycle] is the sole authority on which windows are current for the
 * composition pipeline, so unlike the legacy
 * [com.datadog.android.sessionreplay.internal.recorder.WindowCallbackInterceptor] this has no
 * window self-discovery of its own to reconcile against — [intercept] is always given the
 * complete, authoritative window list.
 */
internal class CompositionWindowTouchInterceptor(
    private val appContext: Context,
    private val recordWriter: RecordWriter,
    private val timeProvider: TimeProvider,
    private val rumContextProvider: RumContextProvider,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val internalLogger: InternalLogger,
    private val windowSource: ActiveWindowSource
) {
    private val lock = Any()
    private val wrappedWindows = WeakHashMap<Window, CompositionWindowTouchCallback>()
    private val scopeProvider = DefaultRumViewScopeProvider(rumContextProvider)

    @MainThread
    fun intercept(windows: List<Window>) {
        val staleWindows = synchronized(lock) { wrappedWindows.keys.filterNot(windows::contains) }
        staleWindows.forEach(::unwrap)
        windows.forEach(::wrap)
    }

    @MainThread
    fun stop() {
        val wrappedWindowsSnapshot = synchronized(lock) { wrappedWindows.keys.toList() }
        wrappedWindowsSnapshot.forEach(::unwrap)
        // A resumed screen must not inherit SHOW regions from the previous capture.
        windowSource.clearTouchPrivacy()
        touchPrivacyManager.replaceCurrentTouchOverrideAreas(emptyMap())
    }

    /** Reserve against the previously observed registration, including when replacing a stale wrapper. */
    @MainThread
    private fun wrap(window: Window) {
        val previous = synchronized(lock) { wrappedWindows[window] }
        val installed = window.callback
        if (previous != null && previous === installed) return
        val toWrap = installed ?: NoOpWindowCallback()
        // The callback is a value in a weak-key map; it must not retain the Window through its root.
        val root = window.peekDecorView()?.let(::WeakReference)
        val callback = CompositionWindowTouchCallback(
            appContext = appContext,
            wrappedCallback = toWrap,
            recordWriter = recordWriter,
            timeProvider = timeProvider,
            rumContextProvider = rumContextProvider,
            touchPrivacyManager = touchPrivacyManager,
            internalLogger = internalLogger,
            touchRecordingScope = {
                scopeProvider.currentScope()?.scope?.let { scope ->
                    windowSource.currentTouchPrivacyScope(root?.get(), scope)
                }
            },
            shouldRecordTouch = { point ->
                val scope = scopeProvider.currentScope()?.scope
                val areas = scope?.let { windowSource.currentTouchOverrideAreas(root?.get(), it) }
                areas != null && touchPrivacyManager.shouldRecordTouch(point, areas)
            }
        )
        val reserved = synchronized(lock) {
            if (wrappedWindows[window] !== previous) {
                false
            } else {
                // A replacement may still delegate to the old wrapper. Only the new one may record.
                previous?.deactivate()
                wrappedWindows[window] = callback
                true
            }
        }
        if (reserved) window.callback = callback
    }

    @MainThread
    private fun unwrap(window: Window) {
        val callback = synchronized(lock) { wrappedWindows.remove(window) } ?: return
        // Deactivate unconditionally: if something else replaced window.callback with a wrapper
        // that still delegates to ours, we can no longer remove it from the chain, but it must
        // still stop recording — otherwise it would keep writing touches after this pipeline
        // considers itself stopped.
        callback.deactivate()
        // If something else replaced window.callback since we wrapped it, that replacement — not
        // our stale reference to it — is what belongs back on the window, so it's left untouched.
        if (window.callback !== callback) return
        window.callback = callback.wrappedCallback.takeUnless { it is NoOpWindowCallback }
    }
}
