/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di

import com.datadog.benchmark.sample.di.common.DispatchersModule
import com.datadog.benchmark.sample.navigation.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.RumAutoLocationDetailsFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Scope

internal interface RumAutoLocationDetailsComponentDependencies {
    val rickAndMortyNetworkService: RickAndMortyNetworkService
    val rumAutoScenarioNavigator: RumAutoScenarioNavigator
}

@Scope
internal annotation class RumAutoLocationDetailsScope

@RumAutoLocationDetailsScope
@Component(
    dependencies = [
        RumAutoLocationDetailsComponentDependencies::class
    ],
    modules = [
        DispatchersModule::class
    ]
)
internal interface RumAutoLocationDetailsComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: RumAutoLocationDetailsComponentDependencies,
            @BindsInstance viewModelScope: CoroutineScope,
            @BindsInstance location: Location
        ): RumAutoLocationDetailsComponent
    }

    fun inject(fragment: RumAutoLocationDetailsFragment)
}
