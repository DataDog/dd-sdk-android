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
import com.datadog.android.log.LogsFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumFeature
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TracingFeature
import com.datadog.android.v2.api.context.UserInfo
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
        val sdkCore = Datadog.initialize(
            this,
            createDatadogCredentials(),
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        ) ?: return

        val rumFeature = RumFeature.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
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
        sdkCore.registerFeature(rumFeature)

        val logsFeature = LogsFeature.Builder()
            .apply {
                if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
                }
            }
            .build()
        sdkCore.registerFeature(logsFeature)

        val tracingFeature = TracingFeature.Builder()
            .apply {
                if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
                    useCustomEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
                }
            }
            .build()
        sdkCore.registerFeature(tracingFeature)

        sdkCore.setUserInfo(
            UserInfo(
                id = "wear 42",
                name = null,
                email = null
            )
        )

        GlobalTracer.registerIfAbsent(
            AndroidTracer.Builder(sdkCore)
                .setService(BuildConfig.APPLICATION_ID)
                .build()
        )
        GlobalRum.registerIfAbsent(sdkCore, RumMonitor.Builder(sdkCore).build())
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
