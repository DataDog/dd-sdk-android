/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.Choreographer
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener

/**
 * Composition-tree-pipeline-only supplement to [WindowsOnDrawListener.onDraw] — entirely separate
 * from [ViewOnDrawInterceptor], which the legacy pipeline also uses and which this class never
 * touches. Only ever constructed when the composition-tree pipeline is active (see
 * [SessionReplayRecorder]'s conditional construction), so its existence alone is proof the legacy
 * pipeline cannot be affected by it.
 *
 * [WindowsOnDrawListener.onDraw] only fires on a full View-system draw traversal, which some
 * content updates (e.g. a GPU/RenderThread-composited layer swap, confirmed on-device for an
 * async image finishing load) visibly change the screen without ever triggering. A
 * [Choreographer.FrameCallback] fires on every rendered frame this app produces regardless of
 * which path drew it, closing that gap, by driving [WindowsOnDrawListener.onDraw] directly —
 * both paths funnel into the exact same listener (and its debouncer), so this only adds a
 * trigger, not a new capture cadence.
 *
 * [ViewOnDrawInterceptor.intercept] always creates exactly one fresh [WindowsOnDrawListener]
 * shared across every currently-known window, replacing whatever was there before — so this
 * class only ever needs to track a single "current" listener, not a collection.
 */
internal class ComposeFrameCallbackAttacher {

    @Volatile
    private var activeListener: WindowsOnDrawListener? = null

    @Volatile
    private var frameCallbackActive = false

    @Volatile
    private var pausedByLifecycle = false

    // Deliberately self-reposting (each doFrame() call posts the next one) rather than posted
    // once — see stop()'s doc for why this makes explicit lifecycle management mandatory.
    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { onFrame() }

    /**
     * Called by [DefaultOnDrawListenerProducer] every time [ViewOnDrawInterceptor.intercept]
     * creates a fresh composition-tree listener — i.e. on every window add/remove/resume event.
     * Retires whichever listener was previously driven (if any) before taking over the new one.
     */
    @MainThread
    fun onListenerCreated(listener: WindowsOnDrawListener) {
        stop()
        activeListener = listener
        if (!pausedByLifecycle) {
            start()
        }
    }

    /** Called when recording stops entirely ([SessionReplayRecorder.stopRecorders]) — no replacement listener follows. */
    @MainThread
    fun stopAll() {
        stop()
        activeListener = null
    }

    /** Meant to be called when the app process leaves the foreground — see [ProcessLifecycleMonitor]. */
    @MainThread
    fun pause() {
        pausedByLifecycle = true
        stop()
    }

    /** Reverses [pause] — meant to be called when the app process re-enters the foreground. */
    @MainThread
    fun resume() {
        pausedByLifecycle = false
        if (activeListener != null) {
            start()
        }
    }

    private fun onFrame() {
        if (!frameCallbackActive) return
        activeListener?.onDraw()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun start() {
        if (frameCallbackActive) return
        frameCallbackActive = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    // A self-reposting Choreographer.FrameCallback holds a strong reference to itself (via the
    // closure capturing this attacher, which in turn holds a strong reference to activeListener)
    // — without an explicit stop, the frame loop (and the listener it drives) never gets garbage
    // collected, leaking a permanently-running per-frame callback.
    private fun stop() {
        if (!frameCallbackActive) return
        frameCallbackActive = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }
}
