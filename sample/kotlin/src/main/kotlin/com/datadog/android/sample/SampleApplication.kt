/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.tracing.Tracer
import io.opentracing.util.GlobalTracer

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val configBuilder = DatadogConfig.Builder(BuildConfig.DD_CLIENT_TOKEN)
            .setServiceName("android-sample-kotlin")
            .setEnvironmentName("staging")

        if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
            configBuilder.useCustomLogsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
            configBuilder.useCustomCrashReportsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
        }
        if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
            configBuilder.useCustomTracesEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
        }

        // Initialise Datadog
        Datadog.initialize(this, configBuilder.build())
        Datadog.setVerbosity(Log.VERBOSE)

        // initialize the tracer here
        GlobalTracer.registerIfAbsent(Tracer.Builder().build())
    }
}
