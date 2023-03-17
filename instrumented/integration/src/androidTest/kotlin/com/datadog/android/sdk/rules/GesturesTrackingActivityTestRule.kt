/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig

internal class GesturesTrackingActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent
) : RumMockServerActivityTestRule<T>(activityClass, keepRequests, trackingConsent) {

    override fun beforeActivityLaunched() {
        super.beforeActivityLaunched()
        val config = RuntimeConfig.configBuilder()
            .build()

        Datadog.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            RuntimeConfig.credentials(),
            config,
            trackingConsent
        )
        Datadog.getInstance()?.registerFeature( // attach the gestures tracker
            // we will use a large long task threshold to make sure we will not have LongTask events
            // noise in our integration tests.
            RuntimeConfig.rumFeatureBuilder()
                .trackInteractions()
                .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
                .build()
        )
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }
}
