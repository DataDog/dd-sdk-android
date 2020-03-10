package com.datadog.android.sdk.integration.rum

import android.app.Activity
import android.os.Bundle
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.rum.ActivityViewTrackingStrategy
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig

internal class RumActivityTrackingPlaygroundActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_tracking_layout)

        // use the activity view tracking strategy
        val config = DatadogConfig.Builder(RuntimeConfig.DD_TOKEN, RuntimeConfig.APP_ID)
            .useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .useCustomRumEndpoint(RuntimeConfig.rumEndpointUrl)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy())
            .build()

        Datadog.initialize(this, config)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }
}
