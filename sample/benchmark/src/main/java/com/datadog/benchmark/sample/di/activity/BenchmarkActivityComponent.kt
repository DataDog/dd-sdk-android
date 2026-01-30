/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MethodOverloading", "TooManyFunctions")

package com.datadog.benchmark.sample.di.activity

import android.content.Context
import com.datadog.android.api.SdkCore
import com.datadog.android.log.Logger
import com.datadog.benchmark.sample.observability.ObservabilityRumMonitor
import com.datadog.benchmark.DatadogBaseMeter
import com.datadog.benchmark.sample.activities.scenarios.DefaultScenarioActivity
import com.datadog.benchmark.sample.activities.scenarios.RumAutoScenarioActivity
import com.datadog.benchmark.sample.activities.scenarios.SessionReplayComposeScenarioActivity
import com.datadog.benchmark.sample.activities.scenarios.SessionReplayScenarioActivity
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.di.common.DispatchersModule
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.observability.ObservabilityLogger
import com.datadog.benchmark.sample.ui.logscustom.LogsFragment
import com.datadog.benchmark.sample.ui.logsheavytraffic.di.LogsHeavyTrafficComponentDependencies
import com.datadog.benchmark.sample.ui.rumauto.screens.characterdetails.RumAutoCharacterDetailFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails.di.RumAutoEpisodeDetailsComponentDependencies
import com.datadog.benchmark.sample.ui.rumauto.screens.episodes.RumAutoEpisodesListFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di.RumAutoLocationDetailsComponentDependencies
import com.datadog.benchmark.sample.ui.rumauto.screens.locations.di.RumAutoLocationsComponentDependencies
import com.datadog.benchmark.sample.ui.rummanual.RumManualScenarioFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayAppcompatFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayMaterialFragment
import com.datadog.benchmark.sample.ui.trace.TraceScenarioFragment
import dagger.Component
import com.datadog.benchmark.sample.observability.ObservabilityTracer
import javax.inject.Scope

@Scope
internal annotation class BenchmarkActivityScope

internal interface BenchmarkActivityComponentDependencies {
    val context: Context
    val benchmarkConfig: BenchmarkConfig

    val logger: ObservabilityLogger
    val rumMonitor: ObservabilityRumMonitor
    val datadogBaseMeter: DatadogBaseMeter
    val tracer: ObservabilityTracer

    val rickAndMortyNetworkService: RickAndMortyNetworkService
}

@Component(
    dependencies = [
        BenchmarkActivityComponentDependencies::class
    ],
    modules = [
        BenchmarkActivityModule::class,
        ViewModelsModule::class,
        BenchmarkActivityModule::class,
        DispatchersModule::class
    ]
)
@BenchmarkActivityScope
internal interface BenchmarkActivityComponent :
    LogsHeavyTrafficComponentDependencies,
    RumAutoEpisodeDetailsComponentDependencies,
    RumAutoLocationsComponentDependencies,
    RumAutoLocationDetailsComponentDependencies {
    @Component.Factory
    interface Factory {
        fun create(
            deps: BenchmarkActivityComponentDependencies
        ): BenchmarkActivityComponent
    }

    fun inject(defaultScenarioActivity: DefaultScenarioActivity)
    fun inject(rumAutoScenarioActivity: RumAutoScenarioActivity)
    fun inject(sessionReplayAppcompatActivity: SessionReplayComposeScenarioActivity)
    fun inject(sessionReplayScenarioActivity: SessionReplayScenarioActivity)

    fun inject(sessionReplayAppcompatFragment: SessionReplayAppcompatFragment)
    fun inject(sessionReplayMaterialFragment: SessionReplayMaterialFragment)
    fun inject(logsFragment: LogsFragment)
    fun inject(traceScenarioFragment: TraceScenarioFragment)
    fun inject(rumManualScenarioFragment: RumManualScenarioFragment)

    fun inject(rumAutoCharactersFragment: RumAutoCharactersFragment)
    fun inject(rumAutoCharacterDetailFragment: RumAutoCharacterDetailFragment)
    fun inject(rumAutoEpisodesListFragment: RumAutoEpisodesListFragment)
}
