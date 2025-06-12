/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityScope
import com.datadog.benchmark.sample.navigation.navigate
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.sample.benchmark.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@BenchmarkActivityScope
internal class RumAutoScenarioNavigator @Inject constructor() {
    private val navControllerState: MutableStateFlow<NavController?> = MutableStateFlow(null)

    val currentTab: Flow<RumAutoScenarioTab>
        get() = navControllerState
            .flatMapLatest { navController ->
                navController?.currentBackStackEntryFlow ?: emptyFlow()
            }
            .mapNotNull { entry ->
                entry.destination.hierarchy.mapNotNull { it.id.toRumAutoScenarioTab() }.firstOrNull()
            }

    fun setNavController(navController: NavController) {
        navControllerState.value = navController
    }

    fun openTab(tab: RumAutoScenarioTab) {
        navControllerState.value?.apply {
            popBackStack()
            navigate(tab.toFragmentId())
        }
    }

    fun openCharacterScreen(character: Character) {
        navControllerState.value?.navigate(R.id.character_detail_fragment, character)
    }

    fun openEpisodeScreen(episode: Episode) {
        navControllerState.value?.navigate(R.id.episode_detail_fragment, episode)
    }

    fun openLocationDetailsScreen(location: Location) {
        navControllerState.value?.navigate(R.id.location_details_fragment, location)
    }
}

private fun RumAutoScenarioTab.toFragmentId(): Int {
    return when (this) {
        RumAutoScenarioTab.CHARACTERS -> R.id.characters_tab_navigation
        RumAutoScenarioTab.EPISODES -> R.id.episodes_tab_navigation
        RumAutoScenarioTab.LOCATIONS -> R.id.locations_tab_navigation
    }
}

private fun Int.toRumAutoScenarioTab(): RumAutoScenarioTab? {
    return when (this) {
        R.id.characters_tab_navigation -> RumAutoScenarioTab.CHARACTERS
        R.id.episodes_tab_navigation -> RumAutoScenarioTab.EPISODES
        R.id.locations_tab_navigation -> RumAutoScenarioTab.LOCATIONS
        else -> null
    }
}
