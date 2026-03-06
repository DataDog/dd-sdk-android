/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.content.Context
import android.graphics.Point
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.recorder.ViewOnDrawInterceptor
import com.datadog.android.sessionreplay.internal.recorder.WindowInspector
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList
import java.util.concurrent.TimeUnit

@Suppress("TooGenericExceptionCaught")
internal class RecorderWindowCallback(
    appContext: Context,
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
    private val motionUpdateThresholdInNs: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS,
    private val flushPositionBufferThresholdInNs: Long = FLUSH_BUFFER_THRESHOLD_NS,
    private val windowInspector: WindowInspector = WindowInspector
) : Window.Callback by wrappedCallback {
    private val pixelsDensity = appContext.resources.displayMetrics.density
    internal val pointerInteractions: MutableList<MobileSegment.MobileRecord> = LinkedList()
    private var lastOnMoveUpdateTimeInNs: Long = 0L
    private var lastPerformedFlushTimeInNs: Long = timeProvider.getDeviceElapsedTimeNanos()
    private var shouldRecordMotion: Boolean = false

    // region Window.Callback

    @MainThread
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val touchLocation = Point(event.x.toInt(), event.y.toInt())
                shouldRecordMotion = touchPrivacyManager.shouldRecordTouch(touchLocation)
            }

            if (shouldRecordMotion) {
                // we copy it and delegate it to the gesture detector for analysis
                @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
                val copy = copyEvent(event)
                try {
                    handleEvent(copy)
                } finally {
                    copy.recycle()
                }
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { MOTION_EVENT_WAS_NULL_ERROR_MESSAGE },
                null
            )
        }

        return wrappedCallback.dispatchTouchEvent(event)
    }

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall") // NPE caught below
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        return try {
            wrappedCallback.onMenuOpened(featureId, menu)
        } catch (e: NullPointerException) {
            false
        }
    }

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall") // NPE caught below
    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        return try {
            wrappedCallback.onMenuItemSelected(featureId, item)
        } catch (e: NullPointerException) {
            false
        }
    }

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall") // NPE caught below
    override fun onPanelClosed(featureId: Int, menu: Menu) {
        try {
            wrappedCallback.onPanelClosed(featureId, menu)
        } catch (_: NullPointerException) {
            // no-op
        }
    }

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall") // NPE caught below
    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
        return try {
            wrappedCallback.onPreparePanel(featureId, view, menu)
        } catch (e: NullPointerException) {
            false
        }
    }

    @Suppress("SwallowedException", "UnsafeThirdPartyFunctionCall") // NPE caught below
    override fun onCreatePanelMenu(featureId: Int, menu: Menu): Boolean {
        return try {
            wrappedCallback.onCreatePanelMenu(featureId, menu)
        } catch (e: NullPointerException) {
            false
        }
    }

    // endregion

    // region Internal

    @MainThread
    private fun handleEvent(event: MotionEvent) {
        when (event.action.and(MotionEvent.ACTION_MASK)) {
            MotionEvent.ACTION_DOWN -> {
                // reset the flush time to avoid flush in the next event
                lastPerformedFlushTimeInNs = timeProvider.getDeviceElapsedTimeNanos()
                updatePositions(event, MobileSegment.PointerEventType.DOWN)
                // reset the on move update time in order to take into account the first move event
                lastOnMoveUpdateTimeInNs = 0
            }

            MotionEvent.ACTION_MOVE -> {
                if (timeProvider.getDeviceElapsedTimeNanos() - lastOnMoveUpdateTimeInNs >= motionUpdateThresholdInNs) {
                    updatePositions(event, MobileSegment.PointerEventType.MOVE)
                    lastOnMoveUpdateTimeInNs = timeProvider.getDeviceElapsedTimeNanos()
                }
                // make sure we flush from time to time to avoid glitches in the player
                if (timeProvider.getDeviceElapsedTimeNanos() - lastPerformedFlushTimeInNs >=
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
                    timestamp = timeProvider.getDeviceTimestampMillis() +
                        rumContextProvider.getRumContext().viewTimeOffsetMs,
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

    @MainThread
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
        lastPerformedFlushTimeInNs = timeProvider.getDeviceElapsedTimeNanos()
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
        }
        wrappedCallback.onWindowFocusChanged(hasFocus)
    }

    // endregion

    companion object {
        // every frame we collect the move event positions
        internal val MOTION_UPDATE_DELAY_THRESHOLD_NS: Long =
            TimeUnit.MILLISECONDS.toNanos(16)

        // every 10 frames we flush the buffer
        internal val FLUSH_BUFFER_THRESHOLD_NS: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS * 10
        internal const val MOTION_EVENT_WAS_NULL_ERROR_MESSAGE =
            "RecorderWindowCallback: intercepted null motion event"
    }
}
