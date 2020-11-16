/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig

internal class RumGesturesTrackingActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent
) : RumMockServerActivityTestRule<T>(activityClass, keepRequests, trackingConsent) {

    override fun beforeActivityLaunched() {
        super.beforeActivityLaunched()
        // attach the gestures tracker
        val config = DatadogConfig.Builder(
            RuntimeConfig.DD_TOKEN,
            RuntimeConfig.INTEGRATION_TESTS_ENVIRONMENT,
            RuntimeConfig.APP_ID
        ).useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .useCustomRumEndpoint(RuntimeConfig.rumEndpointUrl)
            .trackInteractions()
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
            .build()

        Datadog.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            trackingConsent,
            config
        )
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }
}
