/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewTreeObserver.OnDrawListener
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import java.util.WeakHashMap

internal class ViewOnDrawInterceptor(
    private val internalLogger: InternalLogger,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val adaptiveCaptureSchedulingEnabled: Boolean = false,
    private val onDrawListenerProducer: OnDrawListenerProducer
) {
    internal val decorOnDrawListeners: WeakHashMap<View, OnDemandCaptureListener> =
        WeakHashMap()

    @MainThread
    fun intercept(
        decorViews: List<View>,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy
    ) {
        if (adaptiveCaptureSchedulingEnabled) {
            stopIntercepting()
        } else {
            stopInterceptingAndRemove(decorViews)
        }
        val onDrawListener =
            onDrawListenerProducer.create(
                decorViews,
                textAndInputPrivacy,
                imagePrivacy,
                touchPrivacyManager
            )
        decorViews.forEach { decorView ->
            val viewTreeObserver = decorView.viewTreeObserver
            if (viewTreeObserver != null && viewTreeObserver.isAlive) {
                try {
                    viewTreeObserver.addOnDrawListener(onDrawListener)
                    decorOnDrawListeners[decorView] = onDrawListener
                } catch (e: IllegalStateException) {
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.TELEMETRY,
                        { "Unable to add onDrawListener onto viewTreeObserver" },
                        e
                    )
                }
            }
        }

        // force onDraw here in order to make sure we take at least one snapshot if the
        // window is changed very fast
        onDrawListener.onDraw()
    }

    @MainThread
    fun stopIntercepting(decorViews: List<View>) {
        stopInterceptingAndRemove(decorViews)
    }

    @MainThread
    fun stopIntercepting() {
        decorOnDrawListeners.entries.forEach { (decorView, listener) ->
            stopInterceptingSafe(decorView, listener)
        }
        decorOnDrawListeners.values.toSet().forEach { it.cancelPendingCapture() }
        decorOnDrawListeners.clear()
    }

    /**
     * Takes a snapshot of every intercepted window, without going through the debouncer, which
     * would be free to drop it — see [SessionReplayRecorder.requestCapture].
     */
    @MainThread
    fun requestCapture(): CaptureRequestResult {
        // Copy before callbacks because a capture may change the registered listener collection.
        val listeners = decorOnDrawListeners.values.toSet()
        if (listeners.isEmpty()) {
            return CaptureRequestResult.NOT_INTERCEPTING
        }
        // Every window is asked, not only up to the first that succeeds: each contributes its own
        // wireframes to the snapshot.
        @Suppress("UnsafeThirdPartyFunctionCall") // count only propagates what captureNow throws
        return if (listeners.count { it.captureNow() } > 0) {
            CaptureRequestResult.CAPTURED
        } else {
            CaptureRequestResult.NOT_CAPTURED
        }
    }

    @MainThread
    fun scheduleCapture() {
        decorOnDrawListeners.values.toSet().forEach { it.scheduleCapture() }
    }

    /**
     * The outcome of a [requestCapture] call.
     */
    internal enum class CaptureRequestResult {
        /** A snapshot was taken and queued. */
        CAPTURED,

        /** Nothing is being intercepted yet, so the caller still owes itself a capture. */
        NOT_INTERCEPTING,

        /** Listeners are registered, but none of them could produce a snapshot right now. */
        NOT_CAPTURED
    }

    @MainThread
    @Suppress("UnsafeThirdPartyFunctionCall") // Local set operations use only existing listener references.
    private fun stopInterceptingAndRemove(decorViews: List<View>) {
        val removedListeners = mutableSetOf<OnDemandCaptureListener>()
        decorViews.forEach { decorView ->
            decorOnDrawListeners.remove(decorView)?.let { listener ->
                stopInterceptingSafe(decorView, listener)
                removedListeners.add(listener)
            }
        }
        removedListeners.filterNot { it in decorOnDrawListeners.values }.forEach { it.cancelPendingCapture() }
    }

    private fun stopInterceptingSafe(decorView: View, listener: OnDrawListener) {
        if (decorView.viewTreeObserver.isAlive) {
            try {
                decorView.viewTreeObserver.removeOnDrawListener(listener)
            } catch (e: IllegalStateException) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.TELEMETRY,
                    { "Unable to remove onDrawListener from viewTreeObserver" },
                    e
                )
            }
        }
    }
}
