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
    private val dynamicOptimizationEnabled: Boolean
) {

    private var lastTimeRecordWasPerformed = 0L
    private var firstRequest = true

    // Tallied since the last consumeStatsForHealthLog() call — @UiThread-only, same as every
    // other method here (debounce()/executeRunnable()/runInTimeBalance() are always invoked from
    // onDraw(), itself always on the main thread), so no synchronization is needed. See
    // DebouncerHealthStats for what each counts and why: retrospective visibility into this
    // class's own throttling was previously only available by attaching a live debugger — this
    // makes it show up in PixelCapture's periodic [Health] Logcat line instead.
    private var callCount = 0
    private var executedCount = 0
    private var skippedByTimeBankCount = 0

    internal fun debounce(runnable: Runnable) {
        callCount++
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
        if (timePassedSinceLastExecution >= maxRecordDelayInNs) {
            executeRunnable(runnable)
        } else {
            handler.postDelayed({ executeRunnable(runnable) }, DEBOUNCE_TIME_IN_MS)
        }
    }

    /**
     * Snapshot of [callCount]/[executedCount]/[skippedByTimeBankCount] since the last call to
     * this method, then resets all three to zero — mirrors the accumulate-then-flush pattern
     * [PixelCapture]'s own health counters use. Called once per executed cycle, right before
     * [PixelCapture.onPreTraversal] — see `WindowsOnDrawListener.runCompositionTreePipeline` —
     * so whatever [PixelCapture] logs next reflects every [debounce] call since its last flush,
     * not just this one.
     */
    internal fun consumeStatsForHealthLog(): DebouncerHealthStats {
        val stats = DebouncerHealthStats(callCount, executedCount, skippedByTimeBankCount)
        callCount = 0
        executedCount = 0
        skippedByTimeBankCount = 0
        return stats
    }

    private fun executeRunnable(runnable: Runnable) {
        if (dynamicOptimizationEnabled) {
            runInTimeBalance {
                runnable.run()
            }
        } else {
            executedCount++
            runnable.run()
        }
        lastTimeRecordWasPerformed = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
    }

    private fun runInTimeBalance(block: () -> Unit) {
        if (timeBank.updateAndCheck(sdkCore.timeProvider.getDeviceElapsedTimeNanos())) {
            executedCount++
            val startTimeInNano = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            block()
            val endTimeInNano = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            timeBank.consume(endTimeInNano - startTimeInNano)
        } else {
            skippedByTimeBankCount++
            logSkippedFrame()
        }
    }

    private fun logSkippedFrame() {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME) ?: return
        val telemetryEvent = mapOf(TYPE_KEY to TYPE_VALUE)
        rumFeature.sendEvent(telemetryEvent)
    }

    companion object {
        // one frame time
        private val MAX_DELAY_THRESHOLD_NS: Long = TimeUnit.MILLISECONDS.toNanos(64)

        // one frame time
        internal const val DEBOUNCE_TIME_IN_MS: Long = 64

        private const val TYPE_VALUE = "sr_skipped_frame"
        private const val TYPE_KEY = "type"
    }
}

/**
 * [callCount] — every [Debouncer.debounce] call, regardless of outcome: the raw rate `onDraw()`
 * is actually invoked at. [executedCount] — how many of those actually ran the snapshot runnable
 * (immediately, or via a delayed post that survived to fire). [skippedByTimeBankCount] — how many
 * were dropped by [Debouncer]'s own time bank specifically (see [Debouncer.runInTimeBalance]),
 * distinct from anything [PixelCapture] budgets on its own.
 *
 * Comparing [callCount] to [executedCount] across a health-log window answers, after the fact —
 * without needing to catch a jerky recording live — whether a low capture cadence came from this
 * class throttling a healthy stream of calls (callCount high, executedCount low) versus `onDraw()`
 * itself simply not firing often (both low, e.g. render/Choreographer starvation upstream of this
 * whole pipeline, which no budget here can compensate for).
 */
internal data class DebouncerHealthStats(
    val callCount: Int,
    val executedCount: Int,
    val skippedByTimeBankCount: Int
)
