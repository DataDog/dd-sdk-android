/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.Context
import android.view.MotionEvent
import android.view.Window
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.FixedWindowCallback
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.recorder.callback.MotionEventUtils
import com.datadog.android.sessionreplay.internal.recorder.callback.PointerInteractionRecorder
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * Wraps one window's [Window.Callback] to record pointer interactions for the composition-tree
 * pipeline. [PointerInteractionRecorder] does the buffering/debouncing; this class only decides
 * where a completed batch is written and how [Window.Callback] delegation behaves — unlike the
 * legacy [com.datadog.android.sessionreplay.internal.recorder.callback.RecorderWindowCallback],
 * it has no window self-discovery of its own, since [AndroidSnapshotCaptureLifecycle] is already
 * the sole authority on which windows are current for this pipeline.
 */
@Suppress("TooGenericExceptionCaught")
internal class CompositionWindowTouchCallback(
    appContext: Context,
    internal val wrappedCallback: Window.Callback,
    private val recordWriter: RecordWriter,
    timeProvider: TimeProvider,
    private val rumContextProvider: RumContextProvider,
    touchPrivacyManager: TouchPrivacyManager,
    private val internalLogger: InternalLogger,
    copyEvent: (MotionEvent) -> MotionEvent = {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        MotionEvent.obtain(it)
    },
    motionEventUtils: MotionEventUtils = MotionEventUtils,
    motionUpdateThresholdInNs: Long = PointerInteractionRecorder.MOTION_UPDATE_DELAY_THRESHOLD_NS,
    flushPositionBufferThresholdInNs: Long = PointerInteractionRecorder.FLUSH_BUFFER_THRESHOLD_NS
) : FixedWindowCallback(wrappedCallback) {
    private val pointerInteractionRecorder = PointerInteractionRecorder(
        pixelsDensity = appContext.resources.displayMetrics.density,
        timeProvider = timeProvider,
        rumContextProvider = rumContextProvider,
        touchPrivacyManager = touchPrivacyManager,
        onFlush = ::flushToRecordWriter,
        copyEvent = copyEvent,
        motionEventUtils = motionEventUtils,
        motionUpdateThresholdInNs = motionUpdateThresholdInNs,
        flushPositionBufferThresholdInNs = flushPositionBufferThresholdInNs
    )

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

    @MainThread
    private fun flushToRecordWriter(records: List<MobileSegment.MobileRecord>): Boolean {
        val rumContext = rumContextProvider.getRumContext()
        if (!rumContext.isValid()) return false
        recordWriter.write(
            EnrichedRecord(
                applicationId = rumContext.applicationId,
                sessionId = rumContext.sessionId,
                viewId = rumContext.viewId,
                records = records
            )
        )
        return true
    }

    private fun logOrRethrowWrappedCallbackException(e: NullPointerException) {
        // Delegating to wrappedCallback can throw a NullPointerException with the message
        // "Parameter specified as non-null is null: method xxx, parameter xxx" — Kotlin's
        // compiler-injected null check firing because the wrapped callback declared a Java
        // platform-type parameter as non-null. That failure is an artifact of us delegating
        // through it, not a real fault in the app's touch handling, so it's ours to swallow.
        // Anything else is a genuine failure in the wrapped callback and must propagate, or the
        // app's own bug would go unnoticed.
        if (e.message?.contains("Parameter specified as non-null is null") == true) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { FAIL_TO_PROCESS_MOTION_EVENT_ERROR_MESSAGE },
                e
            )
        } else {
            @Suppress("ThrowingInternalException") // must propagate the wrapped callback's own failure
            throw e
        }
    }

    private companion object {
        private const val EVENT_CONSUMED: Boolean = true
        const val MOTION_EVENT_WAS_NULL_ERROR_MESSAGE =
            "CompositionWindowTouchCallback: intercepted null motion event"
        const val FAIL_TO_PROCESS_MOTION_EVENT_ERROR_MESSAGE =
            "CompositionWindowTouchCallback: wrapped callback failed to handle the motion event"
    }
}
