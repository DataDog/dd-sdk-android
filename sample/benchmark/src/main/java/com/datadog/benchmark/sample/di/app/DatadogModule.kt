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
import com.datadog.android.privacy.TrackingConsent
import com.datadog.sample.benchmark.BuildConfig
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
internal interface DatadogModule {
    companion object {
        @Provides
        @Singleton
        fun provideSdkCore(context: Context): SdkCore {
            return Datadog.initialize(
                context,
                createDatadogConfiguration(),
                TrackingConsent.GRANTED
            )!!
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
            { Log.w("BackPressure", "THRESHOLD REACHED!") },
            { Log.e("BackPressure", "ITEM DROPPED $it!") },
            BackPressureMitigation.IGNORE_NEWEST
        )
    )

    return configBuilder.build()
}

// the same as the default one
private const val CAPACITY_BACK_PRESSURE_STRATEGY = 1024
