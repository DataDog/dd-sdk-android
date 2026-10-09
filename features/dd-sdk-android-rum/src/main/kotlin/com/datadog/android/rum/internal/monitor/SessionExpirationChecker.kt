/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.os.Handler
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumSessionScope

internal class SessionExpirationChecker(
    private val monitor: DatadogRumMonitor,
    private val timeProvider: TimeProvider,
    internal var checkIntervalMs: Long,
    private val handler: Handler
) {
    @Volatile
    private var stopped = false
    private val expiryCheckRunnable = Runnable {
        if (!stopped) {
            monitor.handleEvent(RumRawEvent.SessionExpiryCheck(Time.now(timeProvider)))
        }
    }

    // Called after every handled RUM event, including the expiry check itself. Only a tracked
    // session gets a pending check, so ticks stop once the session expires and resume with the
    // next event that renews it.
    fun onEventHandled(sessionState: RumSessionScope.State?) {
        handler.removeCallbacks(expiryCheckRunnable)
        if (!stopped && RumSessionScope.State.TRACKED == sessionState) {
            handler.postDelayed(expiryCheckRunnable, checkIntervalMs)
        }
    }

    // Handler delays use uptime, which does not advance in deep sleep, so a pending check can lag far behind
    // the session clock once the device wakes up. Checking when the app comes back to foreground catches a session
    // that expired meanwhile.
    fun checkNow() = expiryCheckRunnable.run()

    fun onSdkStopped() {
        stopped = true
        handler.removeCallbacks(expiryCheckRunnable)
    }
}
