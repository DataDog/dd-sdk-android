/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import com.datadog.android.api.SdkCore
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.di.app.applyCommonConfiguration
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
internal interface OkHttpModule {

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(
            context: Context,
            config: BenchmarkConfig,
            sdkCore: dagger.Lazy<SdkCore>
        ): OkHttpClient {
            return OkHttpClient.Builder().apply {
                applyCommonConfiguration(context)

                if (config.scenario == SyntheticsScenario.RumAuto &&
                    config.run == SyntheticsRun.Instrumented
                ) {
                    val interceptor = DatadogInterceptor.Builder(emptyMap()).apply {
                        setSdkInstanceName(sdkCore.get().name)
                    }.build()

                    addInterceptor(interceptor)
                }
            }.build()
        }
    }
}
