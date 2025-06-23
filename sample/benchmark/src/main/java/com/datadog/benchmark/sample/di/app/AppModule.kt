/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import com.datadog.benchmark.sample.BenchmarkApplication
import com.datadog.benchmark.sample.BenchmarkConfigHolder
import com.datadog.benchmark.sample.config.BenchmarkConfig
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
        fun provideBenchmarkConfig(
            holder: BenchmarkConfigHolder
        ): BenchmarkConfig {
            return holder.config
        }
    }
}
