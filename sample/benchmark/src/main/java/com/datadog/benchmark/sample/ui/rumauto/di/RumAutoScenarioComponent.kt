/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.di

import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioHostFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.docs.RumAutoDocsFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.episodes.RumAutoEpisodesFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.locations.RumAutoLocationsFragment
import dagger.Component
import io.ktor.client.HttpClient
import javax.inject.Scope

internal interface RumAutoScenarioComponentDependencies {
    val httpClient: HttpClient
}

@Scope
internal annotation class RumAutoScenarioScope

@RumAutoScenarioScope
@Component(
    dependencies = [RumAutoScenarioComponentDependencies::class]
)
internal interface RumAutoScenarioComponent {
    @Component.Factory
    interface Factory {
        fun create(deps: RumAutoScenarioComponentDependencies): RumAutoScenarioComponent
    }

    fun inject(rumAutoScenarioFragment: RumAutoScenarioHostFragment)
    fun inject(rumAutoCharactersFragment: RumAutoCharactersFragment)
    fun inject(rumAutoEpisodesFragment: RumAutoEpisodesFragment)
    fun inject(rumAutoLocationsFragment: RumAutoLocationsFragment)
    fun inject(rumAutoDocsFragment: RumAutoDocsFragment)
}
