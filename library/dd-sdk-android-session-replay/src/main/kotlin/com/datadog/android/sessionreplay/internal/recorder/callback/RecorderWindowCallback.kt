/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.content.Context
import android.view.MotionEvent
import android.view.Window
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.utils.TimeProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList
import java.util.concurrent.TimeUnit

@Suppress("TooGenericExceptionCaught")
internal class RecorderWindowCallback(
    private val appContext: Context,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    internal val wrappedCallback: Window.Callback,
    private val timeProvider: TimeProvider,
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor,
    private val copyEvent: (MotionEvent) -> MotionEvent = {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        MotionEvent.obtain(it)
    },
    private val motionEventUtils: MotionEventUtils = MotionEventUtils,
    private val motionUpdateThresholdInNs: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS,
    private val flushPositionBufferThresholdInNs: Long = FLUSH_BUFFER_THRESHOLD_NS,
    private val windowInspector: WindowInspector = WindowInspector
) : Window.Callback by wrappedCallback {
    private val pixelsDensity = appContext.resources.displayMetrics.density
    internal var pointerInteractions: MutableList<MobileSegment.MobileRecord> = LinkedList()
    private var lastOnMoveUpdateTimeInNs: Long = 0L
    private var lastPerformedFlushTimeInNs: Long = System.nanoTime()

    // region Window.Callback

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            // we copy it and delegate it to the gesture detector for analysis
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            val copy = copyEvent(event)
            try {
                handleEvent(copy)
            } finally {
                copy.recycle()
            }
        } else {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // sdkLogger.errorWithTelemetry("Received MotionEvent=null")
        }

        @Suppress("SwallowedException")
        return try {
            wrappedCallback.dispatchTouchEvent(event)
        } catch (e: Throwable) {
            // TODO: RUMM-2397 Add the proper logs here once the sdkLogger will be added
            // sdkLogger.errorWithTelemetry("Wrapped callback failed processing MotionEvent", e)
            EVENT_CONSUMED
        }
    }

    // endregion

    // region Internal

    private fun handleEvent(event: MotionEvent) {
        when (event.action.and(MotionEvent.ACTION_MASK)) {
            MotionEvent.ACTION_DOWN -> {
                // reset the flush time to avoid flush in the next event
                lastPerformedFlushTimeInNs = System.nanoTime()
                updatePositions(event, MobileSegment.PointerEventType.DOWN)
                // reset the on move update time in order to take into account the first move event
                lastOnMoveUpdateTimeInNs = 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (System.nanoTime() - lastOnMoveUpdateTimeInNs >= motionUpdateThresholdInNs) {
                    updatePositions(event, MobileSegment.PointerEventType.MOVE)
                    lastOnMoveUpdateTimeInNs = System.nanoTime()
                }
                // make sure we flush from time to time to avoid glitches in the player
                if (System.nanoTime() - lastPerformedFlushTimeInNs >=
                    flushPositionBufferThresholdInNs
                ) {
                    flushPositions()
                }
            }
            MotionEvent.ACTION_UP -> {
                updatePositions(event, MobileSegment.PointerEventType.UP)
                flushPositions()
                lastOnMoveUpdateTimeInNs = 0
            }
        }
    }

    private fun updatePositions(event: MotionEvent, eventType: MobileSegment.PointerEventType) {
        for (pointerIndex in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(pointerIndex).toLong()
            val pointerAbsoluteX = motionEventUtils.getPointerAbsoluteX(event, pointerIndex)
            val pointerAbsoluteY = motionEventUtils.getPointerAbsoluteY(event, pointerIndex)
            pointerInteractions.add(
                MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
                    timestamp = timeProvider.getDeviceTimestamp(),
                    data = MobileSegment.MobileIncrementalData.PointerInteractionData(
                        pointerEventType = eventType,
                        pointerType = MobileSegment.PointerType.TOUCH,
                        pointerId = pointerId,
                        x = pointerAbsoluteX.toLong().densityNormalized(pixelsDensity),
                        y = pointerAbsoluteY.toLong().densityNormalized(pixelsDensity)
                    )
                )
            )
        }
    }

    private fun flushPositions() {
        if (pointerInteractions.isEmpty()) {
            return
        }

        val item = recordedDataQueueHandler.addTouchEventItem(
            ArrayList(pointerInteractions)
        ) ?: return

        if (item.isReady()) {
            recordedDataQueueHandler.tryToConsumeItems()
        }

        pointerInteractions.clear()
        lastPerformedFlushTimeInNs = System.nanoTime()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        val rootViews = windowInspector.getGlobalWindowViews()
        if (rootViews.isNotEmpty()) {
            // a new window was added or removed so we stop recording the previous root views
            // and we start recording the new ones.
            viewOnDrawInterceptor.stopIntercepting()
            viewOnDrawInterceptor.intercept(rootViews, appContext)
        }
    }

    // endregion

    companion object {
        private const val EVENT_CONSUMED: Boolean = true

        // every frame we collect the move event positions
        internal val MOTION_UPDATE_DELAY_THRESHOLD_NS: Long =
            TimeUnit.MILLISECONDS.toNanos(16)

        // every 10 frames we flush the buffer
        internal val FLUSH_BUFFER_THRESHOLD_NS: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS * 10
    }
}
