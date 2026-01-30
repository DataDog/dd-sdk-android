/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityComponent
import com.datadog.benchmark.sample.di.activity.DaggerBenchmarkActivityComponent

class MainActivityViewModel(
    val component: BenchmarkActivityComponent
) : ViewModel()

class MainActivityViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val benchmarkActivityComponent = DaggerBenchmarkActivityComponent.factory().create(
            deps = application.benchmarkAppComponent
        )

        return MainActivityViewModel(benchmarkActivityComponent) as T
    }
}
