/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.ActivityManager
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.Rum
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.rum.AppLaunchPlaygroundActivity

internal class AppLaunchActivityTestRule : RumMockServerActivityTestRule<AppLaunchPlaygroundActivity>(
    activityClass = AppLaunchPlaygroundActivity::class.java,
    keepRequests = true,
    trackingConsent = TrackingConsent.GRANTED
) {
    override fun beforeActivityLaunched() {
        super.beforeActivityLaunched()
        val config = RuntimeConfig.configBuilder()
            .build()

        val sdkCore = Datadog.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            config,
            trackingConsent
        )
        checkNotNull(sdkCore)

        val rumConfig = RuntimeConfig.rumConfigBuilder()
            .trackUserInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
            .build()
        Rum.enable(rumConfig, sdkCore)
        DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}
