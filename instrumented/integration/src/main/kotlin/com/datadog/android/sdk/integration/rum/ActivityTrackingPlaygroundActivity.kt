/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.app.ActivityManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.Rum
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent

internal class ActivityTrackingPlaygroundActivity : AppCompatActivity() {

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // we will use a large long task threshold to make sure we will not have LongTask events
        // noise in our integration tests.
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)

        val rumConfig = RuntimeConfig.rumConfigBuilder()
            .trackUserInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()
        Rum.enable(rumConfig, sdkCore)

        DdRumContentProvider::class.java.declaredMethods.firstOrNull() {
            it.name == "overrideProcessImportance"
        }?.apply {
            isAccessible = true
            invoke(null, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        }
        setContentView(R.layout.fragment_tracking_layout)
    }
}
