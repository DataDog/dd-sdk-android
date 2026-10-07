/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import java.util.concurrent.TimeUnit

internal class Debouncer(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val maxRecordDelayInNs: Long = MAX_DELAY_THRESHOLD_NS,
    private val timeBank: TimeBank = RecordingTimeBank(),
    private val sdkCore: FeatureSdkCore,
    private val dynamicOptimizationEnabled: Boolean,
    private val adaptiveCaptureSchedulingEnabled: Boolean = false
) {
    private var captureIntervalStartNs: Long? = null
    private var pendingCapture: Runnable? = null
    private var forceCapture = false
    private val captureCallback = Runnable {
        @Suppress("ThreadSafety") // Posted only to the main handler.
        executePendingCapture()
    }

    /** Keep the latest update without postponing a capture already scheduled. */
    @MainThread
    internal fun debounce(capture: Runnable, force: Boolean = false) {
        if (!adaptiveCaptureSchedulingEnabled) {
            debounceLegacy(capture)
            return
        }
        val wasPending = pendingCapture != null
        pendingCapture = capture
        forceCapture = forceCapture || force
        if (force || !wasPending) {
            val now = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            val delay = if (force) 0L else maxRecordDelayInNs - (now - (captureIntervalStartNs ?: now))
            // Always post: traversal must not extend the current onDraw callback.
            handler.removeCallbacksAndMessages(null)
            postCapture(delay.coerceAtLeast(0L))
        }
    }

    @MainThread
    internal fun cancel() {
        if (!adaptiveCaptureSchedulingEnabled) return
        handler.removeCallbacksAndMessages(null)
        pendingCapture = null
        forceCapture = false
    }

    @MainThread
    private fun executePendingCapture() {
        val capture = pendingCapture ?: return
        val start = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
        val isCaptureAllowedByBudget = !dynamicOptimizationEnabled || timeBank.updateAndCheck(start)
        if (!forceCapture && !isCaptureAllowedByBudget) {
            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(mapOf("type" to "sr_skipped_frame"))
            postCapture(timeBank.timeUntilAvailableInNs().coerceAtLeast(MAX_DELAY_THRESHOLD_NS))
            return
        }
        capture.run()
        val end = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
        if (dynamicOptimizationEnabled) timeBank.consume(end - start)
        captureIntervalStartNs = end
        // Missing windows or RUM context need a new draw or view notification, not polling.
        pendingCapture = null
        forceCapture = false
    }

    private fun debounceLegacy(capture: Runnable) {
        val now = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
        if (captureIntervalStartNs == null) captureIntervalStartNs = now
        handler.removeCallbacksAndMessages(null)
        if (now - (captureIntervalStartNs ?: now) >= maxRecordDelayInNs) {
            executeLegacyCapture(capture)
        } else {
            handler.postDelayed({ executeLegacyCapture(capture) }, DEBOUNCE_TIME_IN_MS)
        }
    }

    private fun executeLegacyCapture(capture: Runnable) {
        if (!dynamicOptimizationEnabled || timeBank.updateAndCheck(sdkCore.timeProvider.getDeviceElapsedTimeNanos())) {
            val start = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
            capture.run()
            if (dynamicOptimizationEnabled) timeBank.consume(sdkCore.timeProvider.getDeviceElapsedTimeNanos() - start)
        } else {
            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(mapOf("type" to "sr_skipped_frame"))
        }
        captureIntervalStartNs = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
    }

    private fun postCapture(delayNs: Long) {
        val delayMs = TimeUnit.NANOSECONDS.toMillis(delayNs) + if (delayNs % NANOS_PER_MILLISECOND == 0L) 0 else 1
        handler.postDelayed(captureCallback, delayMs)
    }

    companion object {
        internal const val DEBOUNCE_TIME_IN_MS: Long = 64
        private val MAX_DELAY_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(DEBOUNCE_TIME_IN_MS)
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
