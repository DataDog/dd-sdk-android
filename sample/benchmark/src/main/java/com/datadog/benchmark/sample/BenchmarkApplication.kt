/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.DatadogMeter
import com.datadog.sample.benchmark.BuildConfig
import com.datadog.sample.benchmark.R

internal class BenchmarkApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        initDatadogSessionReplay()
        enableDatadogMeter()
    }

    private fun enableDatadogMeter() {
        DatadogMeter.create(
            DatadogExporterConfiguration.Builder(BuildConfig.BM_API_KEY)
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setApplicationName("Benchmark Application")
                .setApplicationVersion(BuildConfig.VERSION_NAME)
                .setIntervalInSeconds(METER_INTERVAL_IN_SECONDS)
                .build()
        ).startGauges()
    }

    private fun initDatadogSessionReplay() {
        Datadog.initialize(
            this,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )
        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig)
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.BM_RUM_APPLICATION_ID)
            .useViewTrackingStrategy(
                NavigationViewTrackingStrategy(
                    R.id.nav_host_fragment,
                    true,
                    BenchmarkNavigationPredicate()
                )
            )
            .setTelemetrySampleRate(SAMPLE_RATE_TELEMETRY)
            .trackUserInteractions()
            .trackLongTasks(THRESHOLD_LONG_TASK_INTERVAL)
            .trackNonFatalAnrs(true)
            .setViewEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setActionEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setResourceEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setErrorEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setLongTaskEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .build()
    }

    @SuppressLint("LogNotTimber")
    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            clientToken = BuildConfig.BM_CLIENT_TOKEN,
            env = BuildConfig.BUILD_TYPE
        )
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)

        configBuilder.setBackpressureStrategy(
            BackPressureStrategy(
                CAPACITY_BACK_PRESSURE_STRATEGY,
                { Log.w("BackPressure", "THRESHOLD REACHED!") },
                { Log.e("BackPressure", "ITEM DROPPED $it!") },
                BackPressureMitigation.IGNORE_NEWEST
            )
        )

        return configBuilder.build()
    }

    companion object {
        private const val METER_INTERVAL_IN_SECONDS = 10L

        private const val ATTR_IS_MAPPED = "is_mapped"
        private const val SAMPLE_RATE_TELEMETRY = 100f
        private const val THRESHOLD_LONG_TASK_INTERVAL = 250L
        private const val CAPACITY_BACK_PRESSURE_STRATEGY = 32
    }
}
