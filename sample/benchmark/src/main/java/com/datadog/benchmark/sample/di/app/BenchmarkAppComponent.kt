/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import com.datadog.benchmark.sample.BenchmarkApplication
import com.datadog.benchmark.sample.BenchmarkGlideModule
import com.datadog.benchmark.sample.activities.LaunchActivity
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityComponentDependencies

internal interface BenchmarkAppComponent : BenchmarkActivityComponentDependencies {
    fun inject(benchmarkApplication: BenchmarkApplication)
    fun inject(launchActivity: LaunchActivity)
    fun inject(glideModule: BenchmarkGlideModule)
}
