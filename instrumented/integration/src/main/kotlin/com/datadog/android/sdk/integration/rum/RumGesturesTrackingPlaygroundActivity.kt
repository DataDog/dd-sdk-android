package com.datadog.android.sdk.integration.rum

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig

internal class RumGesturesTrackingPlaygroundActivity : Activity() {

    lateinit var button: Button
    lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gestures_tracking_layout)
        // attach the gestures tracker
        val config = DatadogConfig.Builder(RuntimeConfig.DD_TOKEN, RuntimeConfig.APP_ID)
            .useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .useCustomRumEndpoint(RuntimeConfig.rumEndpointUrl)
            .trackGestures()
            .build()

        Datadog.initialize(this, config)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())

        button = findViewById(R.id.button)
        textView = findViewById(R.id.textView)
    }
}
