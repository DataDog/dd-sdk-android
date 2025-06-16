/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.Logger
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.benchmark.DatadogBaseMeter
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.DatadogSdkMeter
import com.datadog.benchmark.DatadogVitalsMeter
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.sample.benchmark.BuildConfig
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
internal interface DatadogModule {
    companion object {
        @Provides
        @Singleton
        fun provideSdkCore(
            context: Context,
            config: BenchmarkConfig
        ): SdkCore {
            if (config.run == SyntheticsRun.Baseline) {
                return Datadog.getInstance() // returns NoOpInternalSdkCore under the hood
            }

            return Datadog.initialize(
                DATADOG_SDK_INSTANCE_NAME,
                context,
                createDatadogConfiguration(),
                TrackingConsent.GRANTED
            )!!
        }

        @Provides
        @Singleton
        fun provideDatadogMeter(config: BenchmarkConfig): DatadogBaseMeter {
            val exporterConfig = DatadogExporterConfiguration.Builder(BuildConfig.BENCHMARK_API_KEY)
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setApplicationName(BENCHMARK_APPLICATION_NAME)
                .setRun(config.getRun())
                .setScenario(config.getScenario())
                .setApplicationVersion(BuildConfig.VERSION_NAME)
                .setIntervalInSeconds(METER_INTERVAL_IN_SECONDS)
                .build()

            return if (config.scenario == SyntheticsScenario.Upload) {
                DatadogSdkMeter.create(exporterConfig)
            } else {
                DatadogVitalsMeter.create(exporterConfig)
            }
        }

        @Provides
        @Singleton
        fun provideLogger(sdkCore: SdkCore): Logger {
            return Logger.Builder(sdkCore)
                .setName("benchmarkLogger")
                .setLogcatLogsEnabled(true)
                .build()
        }

        @Provides
        @Singleton
        fun provideRumMonitor(sdkCore: SdkCore): RumMonitor {
            return GlobalRumMonitor.get(sdkCore = sdkCore)
        }
    }
}

@SuppressLint("LogNotTimber")
private fun createDatadogConfiguration(): Configuration {
    val configBuilder = Configuration.Builder(
        clientToken = BuildConfig.BENCHMARK_CLIENT_TOKEN,
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

internal const val DATADOG_SDK_INSTANCE_NAME = "benchmark_datadog_sdk"

// the same as the default one
private const val CAPACITY_BACK_PRESSURE_STRATEGY = 1024

private const val METER_INTERVAL_IN_SECONDS = 10L
private const val BENCHMARK_APPLICATION_NAME = "Benchmark Application"
