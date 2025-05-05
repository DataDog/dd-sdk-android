/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import com.datadog.benchmark.DatadogBaseMeter
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.DatadogSdkMeter
import com.datadog.benchmark.DatadogVitalsMeter
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.sample.benchmark.BuildConfig
import dagger.Module
import dagger.Provides

@Module
internal interface DatadogActivityModule {
    companion object {
        @Provides
        @BenchmarkActivityScope
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
    }
}

private const val METER_INTERVAL_IN_SECONDS = 10L
private const val BENCHMARK_APPLICATION_NAME = "Benchmark Application"
