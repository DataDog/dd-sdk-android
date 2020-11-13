/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent

internal class RumActivityTrackingPlaygroundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // use the activity view tracking strategy
        val config = DatadogConfig.Builder(
            RuntimeConfig.DD_TOKEN,
            RuntimeConfig.INTEGRATION_TESTS_ENVIRONMENT,
            RuntimeConfig.APP_ID
        )
            .useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .useCustomRumEndpoint(RuntimeConfig.rumEndpointUrl)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()

        Datadog.initialize(this, intent.getTrackingConsent(), config)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_tracking_layout)
    }
}
