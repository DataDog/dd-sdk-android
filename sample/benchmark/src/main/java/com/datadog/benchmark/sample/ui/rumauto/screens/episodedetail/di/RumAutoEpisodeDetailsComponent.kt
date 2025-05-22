/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodedetail.di

import com.datadog.benchmark.sample.di.common.DispatchersModule
import com.datadog.benchmark.sample.navigation.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetail.RumAutoEpisodeDetailsFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Scope

internal interface RumAutoEpisodeDetailsComponentDependencies {
    val rickAndMortyNetworkService: RickAndMortyNetworkService
    val rumAutoScenarioNavigator: RumAutoScenarioNavigator
}

@Scope
internal annotation class RumAutoEpisodeDetailsScope

@Component(
    dependencies = [
        RumAutoEpisodeDetailsComponentDependencies::class
    ],
    modules = [
        DispatchersModule::class
    ]
)
@RumAutoEpisodeDetailsScope
internal interface RumAutoEpisodeDetailsComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: RumAutoEpisodeDetailsComponentDependencies,
            @BindsInstance viewModelScope: CoroutineScope,
            @BindsInstance episode: Episode,
        ): RumAutoEpisodeDetailsComponent
    }

    fun inject(fragment: RumAutoEpisodeDetailsFragment)
}
