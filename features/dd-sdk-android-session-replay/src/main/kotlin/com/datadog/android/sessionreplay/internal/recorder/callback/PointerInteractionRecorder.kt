/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.graphics.Point
import android.view.MotionEvent
import androidx.annotation.MainThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList
import java.util.concurrent.TimeUnit

/**
 * Turns [MotionEvent]s into buffered [MobileSegment.MobileRecord]s, gated by [touchPrivacyManager]
 * and debounced/flushed on the same cadence regardless of where the completed batch ends up.
 * Shared by the legacy and composition-tree recording pipelines' window callbacks, which differ
 * only in how they wrap [android.view.Window.Callback] and where a completed batch is written —
 * both hand that off to [onFlush], which reports whether the batch was accepted. A rejected batch
 * (e.g. no RUM context available yet) stays buffered so a later flush can retry it.
 */
internal class PointerInteractionRecorder(
    private val pixelsDensity: Float,
    private val timeProvider: TimeProvider,
    private val rumContextProvider: RumContextProvider,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val onFlush: (List<MobileSegment.MobileRecord>) -> Boolean,
    private val copyEvent: (MotionEvent) -> MotionEvent = {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        MotionEvent.obtain(it)
    },
    private val motionEventUtils: MotionEventUtils = MotionEventUtils,
    private val motionUpdateThresholdInNs: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS,
    private val flushPositionBufferThresholdInNs: Long = FLUSH_BUFFER_THRESHOLD_NS
) {
    internal val pointerInteractions: MutableList<MobileSegment.MobileRecord> = LinkedList()
    private var lastOnMoveUpdateTimeInNs: Long = 0L
    private var lastPerformedFlushTimeInNs: Long = timeProvider.getDeviceElapsedTimeNanos()
    private var shouldRecordMotion: Boolean = false

    @MainThread
    fun recordTouchEvent(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // touch privacy override areas are screen-absolute (built from getLocationOnScreen()),
            // so we must compare against raw/absolute coordinates rather than event.x/y, which are
            // window-local and only match screen coordinates for windows positioned at the origin.
            val touchLocation = Point(
                motionEventUtils.getPointerAbsoluteX(event, 0).toInt(),
                motionEventUtils.getPointerAbsoluteY(event, 0).toInt()
            )
            shouldRecordMotion = touchPrivacyManager.shouldRecordTouch(touchLocation)
        }
        if (!shouldRecordMotion) return

        @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
        val copy = copyEvent(event)
        try {
            handleEvent(copy)
        } finally {
            copy.recycle()
        }
    }

    @MainThread
    private fun handleEvent(event: MotionEvent) {
        when (event.action.and(MotionEvent.ACTION_MASK)) {
            MotionEvent.ACTION_DOWN -> {
                // reset the flush clock so it doesn't trigger an intermediary flush on the next event
                lastPerformedFlushTimeInNs = timeProvider.getDeviceElapsedTimeNanos()
                updatePositions(event, MobileSegment.PointerEventType.DOWN)
                // reset so the first move after this down is always recorded, regardless of threshold
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

        // A rejected batch (e.g. RUM context not available yet) is left buffered so a later
        // move or ACTION_UP can retry it, rather than losing it or leaving only the gesture tail.
        if (onFlush(ArrayList(pointerInteractions))) {
            pointerInteractions.clear()
            lastPerformedFlushTimeInNs = timeProvider.getDeviceElapsedTimeNanos()
        }
    }

    internal companion object {
        // every frame we collect the move event positions
        val MOTION_UPDATE_DELAY_THRESHOLD_NS: Long = TimeUnit.MILLISECONDS.toNanos(16)

        // every 10 frames we flush the buffer
        val FLUSH_BUFFER_THRESHOLD_NS: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS * 10
    }
}
