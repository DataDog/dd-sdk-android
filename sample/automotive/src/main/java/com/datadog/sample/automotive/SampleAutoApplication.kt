/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.sample.automotive

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy

class SampleAutoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
    }

    private fun initializeDatadog() {
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.initialize(
            this,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )

        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        GlobalRumMonitor.get().debug = true
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
            .useViewTrackingStrategy(
                ActivityViewTrackingStrategy(true)
            )
            .setTelemetrySampleRate(FULL_SAMPLING_RATE)
            .trackUserInteractions()
            .build()
    }
    private fun createDatadogConfiguration(): Configuration {
        return Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = "test",
            variant = ""
        )
            .useSite(DatadogSite.US1)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()
    }

    companion object {
        private const val FULL_SAMPLING_RATE = 100f
    }
}