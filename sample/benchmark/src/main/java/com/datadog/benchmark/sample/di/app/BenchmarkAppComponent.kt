/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import com.datadog.benchmark.sample.BenchmarkApplication
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityComponentDependencies
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Component(
    modules = [
        AppModule::class
    ]
)
@Singleton
internal interface BenchmarkAppComponent : BenchmarkActivityComponentDependencies {
    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance application: BenchmarkApplication
        ): BenchmarkAppComponent
    }

    fun inject(benchmarkApplication: BenchmarkApplication)
}
