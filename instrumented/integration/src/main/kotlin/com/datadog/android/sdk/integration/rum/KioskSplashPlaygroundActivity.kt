/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig

internal class KioskSplashPlaygroundActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = RuntimeConfig.credentials()
        val config = RuntimeConfig.configBuilder()
            .useCustomLogsEndpoint(RuntimeConfig.logsEndpointUrl)
            .useCustomTracesEndpoint(RuntimeConfig.tracesEndpointUrl)
            .useCustomRumEndpoint(RuntimeConfig.rumEndpointUrl)
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
            .disableInteractionTracking()
            .build()

        Datadog.initialize(
            this,
            credentials,
            config,
            TrackingConsent.GRANTED
        )
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())

        setContentView(R.layout.kiosk_splash_layout)

        val endSessionButton: Button = findViewById(R.id.end_session)
        endSessionButton.setOnClickListener {
            GlobalRum.get().stopSession()
        }

        val startKioskButton: Button = findViewById(R.id.start_kiosk)
        startKioskButton.setOnClickListener {
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, KioskTrackedPlaygroundActivity::class.java)
        startActivity(intent)
    }
}
