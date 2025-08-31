/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.uitestappxml

import android.app.Application
import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration

class UiTestApp: Application() {
    lateinit var sdkCore: SdkCore

    override fun onCreate() {
        super.onCreate()

        sdkCore = initializeDatadog(this)
        enableRum()
        enableLogs()
    }

    private fun initializeDatadog(context: Context): SdkCore {
        return Datadog.initialize(
            context,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )!!
    }

    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            clientToken = BuildConfig.UITEST_CLIENT_TOKEN,
            env = BuildConfig.BUILD_TYPE
        )
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)

        configBuilder.setBackpressureStrategy(
            BackPressureStrategy(
                CAPACITY_BACK_PRESSURE_STRATEGY,
                { Log.w("BackPressure", "Threshold reached") },
                { Log.e("BackPressure", "Item dropped: $it") },
                BackPressureMitigation.IGNORE_NEWEST
            )
        )

        return configBuilder.build()
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.UITEST_RUM_APPLICATION_ID).apply {
            trackUserInteractions()
            trackLongTasks()
            trackNonFatalAnrs(true)
        }.build()
    }

    private fun enableRum() {
        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig, sdkCore = sdkCore)
    }

    private fun enableLogs() {
        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig, sdkCore)
    }
}

// the same as the default one
private const val CAPACITY_BACK_PRESSURE_STRATEGY = 1024
