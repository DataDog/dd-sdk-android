/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.tracing.AndroidTracer
import io.opentracing.util.GlobalTracer

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val environment = "staging"
        val configBuilder =
            if (BuildConfig.DD_RUM_APPLICATION_ID.isNotBlank()) {
                DatadogConfig.Builder(
                    BuildConfig.DD_CLIENT_TOKEN,
                    environment,
                    BuildConfig.DD_RUM_APPLICATION_ID
                )
            } else {
                DatadogConfig.Builder(BuildConfig.DD_CLIENT_TOKEN, environment)
            }
        configBuilder
            .setServiceName("android-sample-kotlin")
            .useViewTrackingStrategy(NavigationViewTrackingStrategy(R.id.nav_host_fragment, true))
            .trackGestures()

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

        // Initialise Datadog
        Datadog.initialize(this, configBuilder.build())
        Datadog.setVerbosity(Log.VERBOSE)

        // initialize the tracer here
        GlobalTracer.registerIfAbsent(AndroidTracer.Builder().build())
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }
}
