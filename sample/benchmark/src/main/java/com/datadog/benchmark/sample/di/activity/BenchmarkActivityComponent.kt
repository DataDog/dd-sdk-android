/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.activity

import com.datadog.android.api.SdkCore
import com.datadog.benchmark.sample.DatadogFeaturesInitializer
import com.datadog.benchmark.sample.MainActivity
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.ui.logscustom.LogsFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayAppcompatFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayMaterialFragment
import dagger.BindsInstance
import dagger.Component
import javax.inject.Scope

@Scope
internal annotation class BenchmarkActivityScope

internal interface BenchmarkActivityComponentDependencies {
    val sdkCore: SdkCore
    val datadogFeaturesInitializer: DatadogFeaturesInitializer
}

@Component(
    dependencies = [
        BenchmarkActivityComponentDependencies::class
    ],
    modules = [
        BenchmarkActivityModule::class,
        ViewModelsModule::class,
        DatadogActivityModule::class
    ]
)
@BenchmarkActivityScope
internal interface BenchmarkActivityComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: BenchmarkActivityComponentDependencies,
            @BindsInstance config: BenchmarkConfig,
            @BindsInstance mainActivity: MainActivity
        ): BenchmarkActivityComponent
    }

    fun inject(mainActivity: MainActivity)

    fun inject(sessionReplayAppcompatFragment: SessionReplayAppcompatFragment)
    fun inject(sessionReplayMaterialFragment: SessionReplayMaterialFragment)
    fun inject(logsFragment: LogsFragment)
}
