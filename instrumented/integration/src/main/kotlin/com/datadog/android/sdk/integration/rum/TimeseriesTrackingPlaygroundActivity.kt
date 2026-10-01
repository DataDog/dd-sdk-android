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
import com.datadog.android.core.internal.DatadogCore
import com.datadog.android.core.internal.time.KronosTimeProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.Rum
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
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

        val timeseriesConfiguration = TimeseriesConfiguration(setOf(TimeseriesType.CPU, TimeseriesType.MEMORY))
            .apply { bufferSize = BUFFER_SIZE }

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
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = TimeseriesConfiguration.DEFAULT_INTERVAL_MS
        const val BUFFER_SIZE = 10
        val SESSION_INACTIVITY_MS = TimeUnit.NANOSECONDS.toMillis(RumSessionScope.DEFAULT_SESSION_INACTIVITY_NS)

        /**
         * Moves the clock RUM measures session inactivity with by [offsetMs], leaving wall-clock time untouched,
         * so a test can expire the session without waiting for it. Returns false while the NTP initialization
         * has not installed its provider yet, as it would silently replace the shifted one.
         */
        fun shiftSessionClock(offsetMs: Long): Boolean {
            val coreFeature = (Datadog.getInstance() as DatadogCore).coreFeature
            val timeProvider = coreFeature.timeProvider
            if (timeProvider !is KronosTimeProvider) return false
            coreFeature.timeProvider =
                SessionClockShiftedTimeProvider(timeProvider, TimeUnit.MILLISECONDS.toNanos(offsetMs))
            return true
        }
    }
}

private class SessionClockShiftedTimeProvider(
    private val delegate: TimeProvider,
    private val offsetNs: Long
) : TimeProvider by delegate {
    override fun getDeviceElapsedRealtimeNanos(): Long = delegate.getDeviceElapsedRealtimeNanos() + offsetNs

    override fun getDeviceElapsedRealtimeMillis(): Long =
        TimeUnit.NANOSECONDS.toMillis(getDeviceElapsedRealtimeNanos())
}
