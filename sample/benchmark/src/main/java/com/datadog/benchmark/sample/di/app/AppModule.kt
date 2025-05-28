/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import android.content.Context
import com.datadog.benchmark.sample.BenchmarkApplication
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
internal interface AppModule {
    @Binds
    @Singleton
    fun bindApplicationContext(benchmarkApplication: BenchmarkApplication): Context
}
