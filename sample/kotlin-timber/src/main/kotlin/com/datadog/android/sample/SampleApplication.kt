/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.log.Logger
import com.datadog.android.timber.DatadogTree
import timber.log.Timber

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val logsEndpointUrl =
            if (BuildConfig.DD_OVERRIDE_LOGS_URL.isEmpty()) Datadog.DATADOG_US_LOGS
            else BuildConfig.DD_OVERRIDE_LOGS_URL
        val tracesEndpointUrl =
            if (BuildConfig.DD_OVERRIDE_TRACES_URL.isEmpty()) Datadog.DATADOG_US_TRACES
            else BuildConfig.DD_OVERRIDE_TRACES_URL

        // Initialise Datadog
        Datadog.initialize(
            this,
            BuildConfig.DD_CLIENT_TOKEN,
            logsEndpointUrl,
            tracesEndpointUrl
        )
        Datadog.setVerbosity(Log.VERBOSE)

        // Initialise Logger
        val logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .build()

        logger.addTag("flavor", BuildConfig.FLAVOR)
        logger.addTag("build_type", BuildConfig.BUILD_TYPE)

        Timber.plant(DatadogTree(logger))
    }
}
