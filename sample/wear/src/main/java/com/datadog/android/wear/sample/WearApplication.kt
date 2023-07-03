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
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import io.opentracing.util.GlobalTracer
import timber.log.Timber

/**
 * The main [Application] for the sample WearOs project.
 */
class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
    }

    @Suppress("MagicNumber")
    private fun initializeDatadog() {
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.initialize(
            this,
            createDatadogCredentials(),
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )

        val rumConfig = RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
            .setTelemetrySampleRate(100f)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .trackUserInteractions()
            .trackLongTasks(250L)
            .apply {
                if (BuildConfig.DD_OVERRIDE_RUM_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL)
                }
            }
            .build()
        Rum.enable(rumConfig)

        val logsConfig = LogsConfiguration.Builder()
            .apply {
                if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
                }
            }
            .build()
        Logs.enable(logsConfig)

        val tracesConfig = TraceConfiguration.Builder()
            .apply {
                if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
                }
            }
            .build()
        Trace.enable(tracesConfig)

        Datadog.setUserInfo(
            UserInfo(
                id = "wear 42",
                name = null,
                email = null
            )
        )

        GlobalTracer.registerIfAbsent(
            AndroidTracer.Builder()
                .setService(BuildConfig.APPLICATION_ID)
                .build()
        )
    }

    private fun createDatadogCredentials(): Credentials {
        return Credentials(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR
        )
    }

    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            crashReportsEnabled = true
        )

        try {
            configBuilder.useSite(DatadogSite.valueOf(BuildConfig.DD_SITE_NAME))
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
        }

        return configBuilder.build()
    }
}
