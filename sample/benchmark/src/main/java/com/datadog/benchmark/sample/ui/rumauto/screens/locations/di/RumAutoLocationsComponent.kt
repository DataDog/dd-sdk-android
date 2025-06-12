/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locations.di

import com.datadog.benchmark.sample.di.common.DispatchersModule
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.ui.rumauto.screens.locations.RumAutoLocationsFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Scope

internal interface RumAutoLocationsComponentDependencies {
    val rickAndMortyNetworkService: RickAndMortyNetworkService
    val rumAutoScenarioNavigator: RumAutoScenarioNavigator
}

@Scope
internal annotation class RumAutoLocationsScope

@RumAutoLocationsScope
@Component(
    dependencies = [
        RumAutoLocationsComponentDependencies::class
    ],
    modules = [
        DispatchersModule::class
    ]
)
internal interface RumAutoLocationsComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: RumAutoLocationsComponentDependencies,
            @BindsInstance viewModelScope: CoroutineScope
        ): RumAutoLocationsComponent
    }

    fun inject(fragment: RumAutoLocationsFragment)
}
