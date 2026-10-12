/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CheckInternal")

package com.datadog.android.sdk.integration.rum

import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.monitor.DatadogRumMonitor
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.timeseries.TimeseriesType
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalRumApi::class)
internal class TimeseriesTrackingPlaygroundActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sdkCore = checkNotNull(
            Datadog.initialize(
                this,
                RuntimeConfig.configBuilder().build(),
                intent.getTrackingConsent()
            )
        )

        DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        val timeseriesConfiguration = TimeseriesConfiguration(
            setOf(TimeseriesType.CPU, TimeseriesType.MEMORY),
            bufferSize = BUFFER_SIZE,
            intervalMs = TimeseriesConfiguration.DEFAULT_INTERVAL_MS
        )

        Rum.enable(
            sdkCore = sdkCore,
            rumConfiguration = RuntimeConfig.rumConfigBuilder()
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(trackExtras = false))
                .setTimeseriesConfiguration(timeseriesConfiguration)
                // These events also trigger session expiry checks and could mask a broken timeseries notifier.
                .trackLongTasks(0)
                .trackNonFatalAnrs(false)
                .build()
        )
        shortenSessionInactivity(sdkCore)
    }

    // The SDK has no setting for the inactivity timeout, so the test patches the active session and the expiry
    // checker in place to avoid waiting the default 15 minutes. This reaches into private SDK state and must be
    // updated if RumSessionScope.sessionInactivityNanos is renamed.
    private fun shortenSessionInactivity(sdkCore: SdkCore) {
        val monitor = GlobalRumMonitor.get(sdkCore) as DatadogRumMonitor
        val inactivityField = RumSessionScope::class.java.getDeclaredField("sessionInactivityNanos")
            .apply { isAccessible = true }
        synchronized(monitor.rootScope) {
            monitor.rootScope.childScopes.filterIsInstance<RumSessionScope>().forEach {
                inactivityField.setLong(it, TimeUnit.MILLISECONDS.toNanos(SESSION_INACTIVITY_MS))
            }
            monitor.sessionExpirationChecker.checkIntervalMs = SESSION_INACTIVITY_MS / 3
        }
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = TimeseriesConfiguration.DEFAULT_INTERVAL_MS

        // The buffer must not fill before the session expires, at most SESSION_INACTIVITY_MS plus one
        // expiry check interval (a third of it) after the last RUM event, or a batch is written while still tracked.
        const val BUFFER_SIZE = 20
        const val SESSION_INACTIVITY_MS = 10_000L
    }
}
