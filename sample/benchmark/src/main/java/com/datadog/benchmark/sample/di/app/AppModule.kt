/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import android.content.SharedPreferences
import com.datadog.benchmark.sample.BenchmarkApplication
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
internal interface AppModule {
    @Binds
    @Singleton
    fun bindApplicationContext(benchmarkApplication: BenchmarkApplication): Context

    companion object {
        @Provides
        @Singleton
        fun provideSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("BenchmarkAppPrefs", Context.MODE_PRIVATE)
        }

        @Provides
        @Singleton
        fun provideBenchmarkConfig(
            preferences: SharedPreferences,
        ): BenchmarkConfig {
            return BenchmarkConfig(run = SyntheticsRun.Instrumented, scenario = SyntheticsScenario.RumAuto)
//            return BenchmarkConfig.fromPrefs(preferences)
        }
    }
}

