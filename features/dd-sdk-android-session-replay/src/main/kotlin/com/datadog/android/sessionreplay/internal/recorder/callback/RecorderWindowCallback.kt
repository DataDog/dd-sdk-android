/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.FixedWindowCallback
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.recorder.WindowReflectionUtils
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.WeakHashMap

@Suppress("TooGenericExceptionCaught")
internal class RecorderWindowCallback(
    private val appContext: Context,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    internal val wrappedCallback: Window.Callback,
    private val timeProvider: TimeProvider,
    private val rumContextProvider: RumContextProvider,
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor,
    private val internalLogger: InternalLogger,
    private val privacy: TextAndInputPrivacy,
    private val imagePrivacy: ImagePrivacy,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val copyEvent: (MotionEvent) -> MotionEvent = {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        MotionEvent.obtain(it)
    },
    private val motionEventUtils: MotionEventUtils = MotionEventUtils,
    private val motionUpdateThresholdInNs: Long = PointerInteractionRecorder.MOTION_UPDATE_DELAY_THRESHOLD_NS,
    private val flushPositionBufferThresholdInNs: Long = PointerInteractionRecorder.FLUSH_BUFFER_THRESHOLD_NS,
    private val windowInspector: WindowInspector = WindowInspector,
    private val windowFromDecorView: (
        View
    ) -> Window? = { WindowReflectionUtils.getWindowFromDecorView(it, internalLogger) },
    private val onWindowWrapped: (Window) -> Unit = {},
    internal val shouldInstallCallbacks: (Window) -> Boolean = { true }
) : FixedWindowCallback(wrappedCallback) {
    private val pointerInteractionRecorder = PointerInteractionRecorder(
        pixelsDensity = appContext.resources.displayMetrics.density,
        timeProvider = timeProvider,
        rumContextProvider = rumContextProvider,
        touchPrivacyManager = touchPrivacyManager,
        onFlush = ::flushToQueue,
        copyEvent = copyEvent,
        motionEventUtils = motionEventUtils,
        motionUpdateThresholdInNs = motionUpdateThresholdInNs,
        flushPositionBufferThresholdInNs = flushPositionBufferThresholdInNs
    )
    internal val pointerInteractions: List<MobileSegment.MobileRecord>
        get() = pointerInteractionRecorder.pointerInteractions

    // region Window.Callback

    @MainThread
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            pointerInteractionRecorder.recordTouchEvent(event)
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { MOTION_EVENT_WAS_NULL_ERROR_MESSAGE },
                null
            )
        }

        @Suppress("SwallowedException")
        return try {
            super.dispatchTouchEvent(event)
        } catch (e: NullPointerException) {
            logOrRethrowWrappedCallbackException(e)
            EVENT_CONSUMED
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        val rootViews = windowInspector.getGlobalWindowViews(internalLogger)
        if (rootViews.isNotEmpty()) {
            // a new window was added or removed so we stop recording the previous root views
            // and we start recording the new ones.
            viewOnDrawInterceptor.stopIntercepting()
            viewOnDrawInterceptor.intercept(
                decorViews = rootViews,
                textAndInputPrivacy = privacy,
                imagePrivacy = imagePrivacy
            )
            installCallbackOnNewWindows(rootViews)
        }
        super.onWindowFocusChanged(hasFocus)
    }

    // endregion

    // region Internal

    private fun installCallbackOnNewWindows(rootViews: List<View>) {
        rootViews.forEach { decorView -> installCallbackOnWindow(decorView) }
    }

    private fun installCallbackOnWindow(decorView: View) {
        val window = windowFromDecorView(decorView)
        if (window == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                {
                    WINDOW_FROM_DECOR_VIEW_ERROR_MESSAGE_PREFIX +
                        decorView.javaClass.name +
                        WINDOW_FROM_DECOR_VIEW_ERROR_MESSAGE_SUFFIX
                },
                onlyOnce = true
            )
            return
        }
        // Zero-size windows (NavHost scaffolding, or a dialog window not yet laid out) would
        // trigger spurious stopIntercepting() calls that drop frames, so we wait for the next
        // layout pass instead of installing immediately.
        if (decorView.width == 0 || decorView.height == 0) {
            retryInstallAfterNextLayout(decorView, window)
            return
        }
        if (window.callback !is RecorderWindowCallback) {
            // Post so the dialog's own onWindowFocusChanged(true) fires first,
            // ensuring the new callback starts in a steady recording state.
            decorView.post {
                // re-check: recording may have stopped, or another focus change may have
                // already installed a callback, while this post was pending in the queue.
                if (window.callback !is RecorderWindowCallback && shouldInstallCallbacks(window)) {
                    val toWrap = window.callback ?: NoOpWindowCallback()
                    window.callback = RecorderWindowCallback(
                        appContext = appContext,
                        recordedDataQueueHandler = recordedDataQueueHandler,
                        wrappedCallback = toWrap,
                        timeProvider = timeProvider,
                        rumContextProvider = rumContextProvider,
                        viewOnDrawInterceptor = viewOnDrawInterceptor,
                        internalLogger = internalLogger,
                        privacy = privacy,
                        imagePrivacy = imagePrivacy,
                        touchPrivacyManager = touchPrivacyManager,
                        windowInspector = windowInspector,
                        windowFromDecorView = windowFromDecorView,
                        onWindowWrapped = onWindowWrapped,
                        shouldInstallCallbacks = shouldInstallCallbacks
                    )
                    onWindowWrapped(window)
                }
            }
        }
    }

    private fun retryInstallAfterNextLayout(decorView: View, window: Window) {
        if (pendingLayoutRetries.containsKey(window)) return
        val viewTreeObserver = decorView.viewTreeObserver
        if (viewTreeObserver == null || !viewTreeObserver.isAlive) return
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (decorView.width == 0 || decorView.height == 0) return@OnGlobalLayoutListener
            pendingLayoutRetries.remove(window)?.cancel()
            installCallbackOnWindow(decorView)
        }
        // a dialog dismissed before it ever lays out to a non-zero size would otherwise never
        // trigger onGlobalLayout's removal above, leaking this entry (and everything it retains)
        // until recording stops — detach is the fallback cleanup signal for that case.
        val attachStateListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                pendingLayoutRetries.remove(window)?.cancel()
            }
        }
        pendingLayoutRetries[window] = PendingLayoutRetry(decorView, layoutListener, attachStateListener)
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        decorView.addOnAttachStateChangeListener(attachStateListener)
    }

    @MainThread
    private fun flushToQueue(records: List<MobileSegment.MobileRecord>) {
        val item = recordedDataQueueHandler.addTouchEventItem(records) ?: return
        if (item.isReady()) {
            recordedDataQueueHandler.tryToConsumeItems()
        }
    }

    private fun logOrRethrowWrappedCallbackException(e: NullPointerException) {
        // When calling delegate callback, we may have something like
        // java.lang.NullPointerException: Parameter specified as non-null is null:
        // method xxx, parameter xxx
        // This happens because Kotlin delegate expects non-null value incorrectly inferring
        // non-null type from Java interface definition (seems to be solved in Kotlin 1.8 though)
        if (e.message?.contains("Parameter specified as non-null is null") == true) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { FAIL_TO_PROCESS_MOTION_EVENT_ERROR_MESSAGE },
                e
            )
        } else {
            @Suppress("ThrowingInternalException") // we need to let client exception to propagate
            throw e
        }
    }

    // endregion

    companion object {
        // shared across all RecorderWindowCallback instances (one per active window) so a decorView
        // scanned by multiple windows' focus events still gets at most one pending retry listener.
        private val pendingLayoutRetries: WeakHashMap<Window, PendingLayoutRetry> = WeakHashMap()

        internal fun cancelPendingLayoutRetry(window: Window) {
            pendingLayoutRetries.remove(window)?.cancel()
        }

        internal fun cancelAllPendingLayoutRetries() {
            pendingLayoutRetries.values.forEach { it.cancel() }
            pendingLayoutRetries.clear()
        }

        private const val EVENT_CONSUMED: Boolean = true

        internal const val MOTION_EVENT_WAS_NULL_ERROR_MESSAGE =
            "RecorderWindowCallback: intercepted null motion event"
        internal const val FAIL_TO_PROCESS_MOTION_EVENT_ERROR_MESSAGE =
            "RecorderWindowCallback: wrapped callback failed to handle the motion event"
        internal const val WINDOW_FROM_DECOR_VIEW_ERROR_MESSAGE_PREFIX =
            "SR: failed to get Window from "
        internal const val WINDOW_FROM_DECOR_VIEW_ERROR_MESSAGE_SUFFIX =
            " via reflection — Compose dialog destination may not be recorded"
    }
}

private class PendingLayoutRetry(
    private val decorView: View,
    private val layoutListener: ViewTreeObserver.OnGlobalLayoutListener,
    private val attachStateListener: View.OnAttachStateChangeListener
) {
    fun cancel() {
        val observer = decorView.viewTreeObserver
        if (observer != null && observer.isAlive) {
            observer.removeOnGlobalLayoutListener(layoutListener)
        }
        decorView.removeOnAttachStateChangeListener(attachStateListener)
    }
}
