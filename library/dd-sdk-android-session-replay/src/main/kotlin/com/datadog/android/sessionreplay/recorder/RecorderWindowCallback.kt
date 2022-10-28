/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.view.MotionEvent
import android.view.Window
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.processor.Processor
import com.datadog.android.sessionreplay.utils.TimeProvider
import java.util.LinkedList
import java.util.concurrent.TimeUnit

@Suppress("TooGenericExceptionCaught")
internal class RecorderWindowCallback(
    private val processor: Processor,
    private val pixelsDensity: Float,
    internal val wrappedCallback: Window.Callback,
    private val timeProvider: TimeProvider,
    private val copyEvent: (MotionEvent) -> MotionEvent = {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        MotionEvent.obtain(it)
    },
    private val motionUpdateThresholdInNs: Long = MOTION_UPDATE_DELAY_THRESHOLD_NS,
    private val flushPositionBufferThresholdInNs: Long = FLUSH_BUFFER_THRESHOLD_NS
) : Window.Callback by wrappedCallback {

    internal var positions: MutableList<MobileSegment.Position> = LinkedList()
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
                updatePositions(event)
                // reset the on move update time in order to take into account the first move event
                lastOnMoveUpdateTimeInNs = 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (System.nanoTime() - lastOnMoveUpdateTimeInNs >= motionUpdateThresholdInNs) {
                    updatePositions(event)
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
                updatePositions(event)
                flushPositions()
                lastOnMoveUpdateTimeInNs = 0
            }
        }
    }

    private fun updatePositions(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i).toLong()
            val pointerCoordinates = MotionEvent.PointerCoords()
            event.getPointerCoords(i, pointerCoordinates)
            positions.add(
                MobileSegment.Position(
                    pointerId,
                    pointerCoordinates.x.toLong().densityNormalized(pixelsDensity),
                    pointerCoordinates.y.toLong().densityNormalized(pixelsDensity),
                    timeProvider.getDeviceTimestamp()
                )
            )
        }
    }

    private fun flushPositions() {
        if (positions.isEmpty()) {
            return
        }
        val touchData = MobileSegment.MobileIncrementalData
            .TouchData(LinkedList(positions))
        processor.process(touchData)
        positions.clear()
        lastPerformedFlushTimeInNs = System.nanoTime()
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
