/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.wear.sample

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.tracing.AndroidTracer
import io.opentracing.util.GlobalTracer

/**
 * The main [Application] for the sample WearOs project.
 */
class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
    }

    private fun initializeDatadog() {
        Datadog.initialize(
            this,
            createDatadogCredentials(),
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.enableRumDebugging(true)
        Datadog.setUserInfo(
            "wear 42",
            null,
            null
        )

        GlobalTracer.registerIfAbsent(
            AndroidTracer.Builder()
                .setServiceName(BuildConfig.APPLICATION_ID)
                .build()
        )
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    private fun createDatadogCredentials(): Credentials {
        return Credentials(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            envName = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR,
            rumApplicationId = BuildConfig.DD_RUM_APPLICATION_ID
        )
    }

    @Suppress("MagicNumber")
    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
            .sampleTelemetry(100f)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .trackInteractions()
            .trackLongTasks(250L)

        try {
            configBuilder.useSite(DatadogSite.valueOf(BuildConfig.DD_SITE_NAME))
        } catch (e: IllegalArgumentException) {
            Log.e("WearApplication", "Error setting site to ${BuildConfig.DD_SITE_NAME}")
        }

        if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
            configBuilder.useCustomLogsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
            configBuilder.useCustomCrashReportsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
        }
        if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
            configBuilder.useCustomTracesEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
        }
        if (BuildConfig.DD_OVERRIDE_RUM_URL.isNotBlank()) {
            configBuilder.useCustomRumEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL)
        }

        return configBuilder.build()
    }
}
