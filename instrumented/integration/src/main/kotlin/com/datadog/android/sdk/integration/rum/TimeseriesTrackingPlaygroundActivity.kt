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
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.Rum
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent

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

        Rum.enable(
            sdkCore = sdkCore,
            rumConfiguration = RuntimeConfig.rumConfigBuilder()
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(trackExtras = false))
                .setTimeseriesConfiguration(TimeseriesConfiguration.DEFAULT)
                .build()
        )
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = TimeseriesConfiguration.DEFAULT_INTERVAL_MS
    }
}
