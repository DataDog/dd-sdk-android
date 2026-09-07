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
    private val internalLogger: InternalLogger
) {
    private val lock = Any()
    private val wrappedWindows = WeakHashMap<Window, CompositionWindowTouchCallback>()

    @MainThread
    fun intercept(windows: List<Window>) {
        val staleWindows = synchronized(lock) { wrappedWindows.keys.filterNot(windows::contains) }
        staleWindows.forEach(::unwrap)
        val newWindows = synchronized(lock) { windows.filterNot(wrappedWindows::containsKey) }
        newWindows.forEach(::wrap)
    }

    @MainThread
    fun stop() {
        val wrappedWindowsSnapshot = synchronized(lock) { wrappedWindows.keys.toList() }
        wrappedWindowsSnapshot.forEach(::unwrap)
    }

    /**
     * [intercept] only decides which windows are new under [lock]; the actual registration below
     * must reserve the window under the same lock before touching its callback, otherwise two
     * concurrent [intercept] calls that both see the same window as new would both wrap it, and
     * only one would ever be found again to unwrap.
     */
    private fun wrap(window: Window) {
        val toWrap = window.callback ?: NoOpWindowCallback()
        val callback = CompositionWindowTouchCallback(
            appContext = appContext,
            wrappedCallback = toWrap,
            recordWriter = recordWriter,
            timeProvider = timeProvider,
            rumContextProvider = rumContextProvider,
            touchPrivacyManager = touchPrivacyManager,
            internalLogger = internalLogger
        )
        val reserved = synchronized(lock) {
            if (wrappedWindows.containsKey(window)) {
                false
            } else {
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
