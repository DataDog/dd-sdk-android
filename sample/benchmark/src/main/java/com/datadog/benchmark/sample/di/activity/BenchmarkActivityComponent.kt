/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MethodOverloading")

package com.datadog.benchmark.sample.di.activity

import android.content.Context
import com.datadog.android.api.SdkCore
import com.datadog.android.log.Logger
import com.datadog.android.rum.RumMonitor
import com.datadog.benchmark.DatadogBaseMeter
import com.datadog.benchmark.sample.BenchmarkConfigHolder
import com.datadog.benchmark.sample.MainActivity
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.di.common.DispatchersModule
import com.datadog.benchmark.sample.ui.logscustom.LogsFragment
import com.datadog.benchmark.sample.ui.logsheavytraffic.di.LogsHeavyTrafficComponentDependencies
import com.datadog.benchmark.sample.ui.rumauto.screens.characterdetail.RumAutoCharacterDetailFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.episodeslist.RumAutoEpisodesListFragment
import com.datadog.benchmark.sample.ui.rummanual.RumManualScenarioFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayAppcompatFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayMaterialFragment
import com.datadog.benchmark.sample.ui.trace.TraceScenarioFragment
import com.datadog.trace.api.Tracer
import dagger.Component
import javax.inject.Scope

@Scope
internal annotation class BenchmarkActivityScope

internal interface BenchmarkActivityComponentDependencies {
    val context: Context
    val benchmarkConfig: BenchmarkConfig

    val sdkCore: SdkCore
    val logger: Logger
    val rumMonitor: RumMonitor
    val datadogBaseMeter: DatadogBaseMeter
}

@Component(
    dependencies = [
        BenchmarkActivityComponentDependencies::class
    ],
    modules = [
        BenchmarkActivityModule::class,
        ViewModelsModule::class,
        BenchmarkActivityModule::class,
        DispatchersModule::class,
        OpenTelemetryModule::class,
        OkHttpModule::class
    ]
)
@BenchmarkActivityScope
internal interface BenchmarkActivityComponent :
    LogsHeavyTrafficComponentDependencies
{
    @Component.Factory
    interface Factory {
        fun create(
            deps: BenchmarkActivityComponentDependencies,
        ): BenchmarkActivityComponent
    }

    fun inject(mainActivity: MainActivity)

    fun inject(sessionReplayAppcompatFragment: SessionReplayAppcompatFragment)
    fun inject(sessionReplayMaterialFragment: SessionReplayMaterialFragment)
    fun inject(logsFragment: LogsFragment)
    fun inject(traceScenarioFragment: TraceScenarioFragment)
    fun inject(rumManualScenarioFragment: RumManualScenarioFragment)

    fun inject(rumAutoCharactersFragment: RumAutoCharactersFragment)
    fun inject(rumAutoCharacterDetailFragment: RumAutoCharacterDetailFragment)
    fun inject(rumAutoEpisodesListFragment: RumAutoEpisodesListFragment)
}
