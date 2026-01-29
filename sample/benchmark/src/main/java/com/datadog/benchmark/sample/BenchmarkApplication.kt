/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.app.Application
import android.content.Context
import com.datadog.benchmark.sample.di.app.BenchmarkAppComponent
import com.datadog.benchmark.sample.di.app.DaggerBenchmarkAppComponent

open internal class BenchmarkApplication : Application() {

    internal lateinit var benchmarkAppComponent: BenchmarkAppComponent

    override fun onCreate() {
        super.onCreate()

        benchmarkAppComponent = DaggerBenchmarkAppComponent.factory().create(this)
        benchmarkAppComponent.inject(this)
    }
}

internal val Context.benchmarkAppComponent: BenchmarkAppComponent
    get() = (applicationContext as BenchmarkApplication).benchmarkAppComponent
