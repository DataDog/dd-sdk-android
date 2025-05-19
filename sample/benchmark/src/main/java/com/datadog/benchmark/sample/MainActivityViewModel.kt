/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityComponent
import com.datadog.benchmark.sample.di.activity.DaggerBenchmarkActivityComponent

internal class MainActivityViewModel(
    val component: BenchmarkActivityComponent
) : ViewModel()

internal class MainActivityViewModelFactory(
    private val application: Application,
    private val activity: MainActivity
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        val config = BenchmarkConfig.resolveSyntheticsBundle(activity.intent.extras)

//        val config = BenchmarkConfig(run = SyntheticsRun.Instrumented, scenario = SyntheticsScenario.RumAuto)

        val benchmarkActivityComponent = DaggerBenchmarkActivityComponent.factory().create(
            deps = application.benchmarkAppComponent,
        )

        return MainActivityViewModel(benchmarkActivityComponent) as T
    }
}
