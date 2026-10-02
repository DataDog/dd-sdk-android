/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Handler
import android.os.Looper
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import java.util.concurrent.TimeUnit

internal class Debouncer(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val maxRecordDelayInNs: Long = MAX_DELAY_THRESHOLD_NS,
    private val timeBank: TimeBank = RecordingTimeBank(),
    private val sdkCore: FeatureSdkCore,
    private val dynamicOptimizationEnabled: Boolean,

    // Owns the frame-health signal entirely - this class only asks whether it currently allows a
    // capture, and whether it's enabled at all; it has no other knowledge of FrameHealthMonitor.
    private val jankAwareBackoffPolicy: JankAwareBackoffPolicy = JankAwareBackoffPolicy()
) {

    private var lastTimeRecordWasPerformed = 0L
    private var firstRequest = true

    // Multiplies both delays below whenever a skip check that's currently active reports a skip -
    // a sustained, cheap-but-frequent trigger (e.g. an idle caret blink) that repeatedly gets
    // skipped backs off exponentially instead of retrying on the very next draw. Resets to 1 the
    // moment a capture is allowed to run again, so a screen that only briefly gets skipped snaps
    // straight back to normal cadence. Stays at 1, and behavior is unchanged, whenever no skip
    // check is active.
    private var backoffMultiplier = 1L

    /**
     * Resets the backoff and timing state so the very next [debounce] call executes immediately,
     * regardless of how recently the last capture ran or how far the backoff had grown. Meant to
     * be called on a screen transition: a transition's own capture goes through
     * [ViewOnDrawInterceptor.requestCapture] (which bypasses this debouncer entirely), but without
     * this reset the debouncer's own state - possibly still backed off from the previous screen's
     * load - would keep throttling the new screen's subsequent, ordinary draw-triggered captures.
     */
    internal fun forceNextExecution() {
        backoffMultiplier = 1L
        lastTimeRecordWasPerformed = 0L
        firstRequest = false
    }

    internal fun debounce(runnable: Runnable) {
        if (firstRequest) {
            // we will initialize the lastTimeRecordWasPerformed here to the current time in nano
            // reason why we are not initializing this in the constructor is that in case the
            // component was initialized earlier than the first debounce request was requested
            // it will execute the runnable directly and will not pass through the handler.
            lastTimeRecordWasPerformed = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            firstRequest = false
        }
        handler.removeCallbacksAndMessages(null)
        val timePassedSinceLastExecution = sdkCore.timeProvider.getDeviceElapsedTimeNanos() - lastTimeRecordWasPerformed
        if (timePassedSinceLastExecution >= maxRecordDelayInNs * backoffMultiplier) {
            executeRunnable(runnable)
        } else {
            handler.postDelayed({ executeRunnable(runnable) }, DEBOUNCE_TIME_IN_MS * backoffMultiplier)
        }
    }

    private fun executeRunnable(runnable: Runnable) {
        if (dynamicOptimizationEnabled || jankAwareBackoffPolicy.isEnabled) {
            runWithAdaptiveBackoff {
                runnable.run()
            }
        } else {
            runnable.run()
        }
        lastTimeRecordWasPerformed = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
    }

    private fun runWithAdaptiveBackoff(block: () -> Unit) {
        val timeBankAllows = !dynamicOptimizationEnabled ||
            timeBank.updateAndCheck(sdkCore.timeProvider.getDeviceElapsedTimeNanos())
        val frameHealthAllows = jankAwareBackoffPolicy.allowsCapture()
        if (timeBankAllows && frameHealthAllows) {
            val startTimeInNano = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            block()
            val endTimeInNano = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            if (dynamicOptimizationEnabled) {
                timeBank.consume(endTimeInNano - startTimeInNano)
            }
            // Decay gradually rather than snapping straight back to 1: an 8-frame rolling window
            // can report "healthy" for one cycle even while the underlying trigger (e.g. a
            // sustained idle caret blink) is still just as frequent, and an instant reset would
            // undo the backoff almost as fast as it was applied - oscillating between throttled
            // and unthrottled instead of settling into a stable backoff for the duration of the
            // actual load.
            backoffMultiplier = (backoffMultiplier / 2).coerceAtLeast(1L)
        } else {
            backoffMultiplier = (backoffMultiplier * 2).coerceAtMost(MAX_BACKOFF_MULTIPLIER)
            logSkippedFrame(timeBankAllows = timeBankAllows, frameHealthAllows = frameHealthAllows)
        }
    }

    // Tagged with which check(s) failed, so this is distinguishable from a time-bank skip.
    private fun logSkippedFrame(timeBankAllows: Boolean, frameHealthAllows: Boolean) {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        val reasons = buildList {
            if (!timeBankAllows) add(SKIP_REASON_TIME_BANK)
            if (!frameHealthAllows) add(SKIP_REASON_FRAME_HEALTH)
        }
        val telemetryEvent = mapOf(TYPE_KEY to TYPE_VALUE, REASON_KEY to reasons.joinToString(","))
        rumFeature.sendEvent(telemetryEvent)
    }

    companion object {
        // one frame time
        private val MAX_DELAY_THRESHOLD_NS: Long = TimeUnit.MILLISECONDS.toNanos(64)

        // one frame time
        internal const val DEBOUNCE_TIME_IN_MS: Long = 64

        internal const val MAX_BACKOFF_TIME_IN_MS: Long = 1_024

        // Expressed as a ratio of DEBOUNCE_TIME_IN_MS, not an absolute duration, so the cap holds
        // regardless of maxRecordDelayInNs, which a caller can set independently of that constant.
        internal const val MAX_BACKOFF_MULTIPLIER: Long = MAX_BACKOFF_TIME_IN_MS / DEBOUNCE_TIME_IN_MS

        private const val TYPE_VALUE = "sr_skipped_frame"
        private const val TYPE_KEY = "type"
        private const val REASON_KEY = "reason"
        private const val SKIP_REASON_TIME_BANK = "time_bank"
        private const val SKIP_REASON_FRAME_HEALTH = "frame_health"
    }
}
