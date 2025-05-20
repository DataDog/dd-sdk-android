/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.navigation

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityScope
import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioTab
import com.datadog.benchmark.sample.ui.rumauto.screens.characterdetail.RumAutoCharacterDetailFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.docs.RumAutoDocsFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.episodes.RumAutoEpisodesFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.locations.RumAutoLocationsFragment
import com.datadog.sample.benchmark.R
import javax.inject.Inject

@BenchmarkActivityScope
internal class RumAutoScenarioNavigator @Inject constructor() {
    private var navController: NavController? = null

    fun setNavController(navController: NavController) {
        this.navController = navController

        navController.graph = navController.createGraph(startDestination = CHARACTERS_TAB_FRAGMENT_KEY) {
            fragment<RumAutoCharactersFragment>(CHARACTERS_TAB_FRAGMENT_KEY) {
                label = "Characters screen"
            }
            fragment<RumAutoEpisodesFragment>(EPISODES_TAB_FRAGMENT_KEY) {
                label = "Episodes screen"
            }
            fragment<RumAutoLocationsFragment>(LOCATIONS_TAB_FRAGMENT_KEY) {
                label = "Locations screen"
            }
            fragment<RumAutoDocsFragment>(DOCS_TAB_FRAGMENT_KEY) {
                label = "Docs screen"
            }
            fragment<RumAutoCharacterDetailFragment>(R.id.character_detail_fragment) {
                label = "Character detail screen"
            }
        }
    }

    fun openTab(tab: RumAutoScenarioTab) {
        navController?.navigate(tab.toFragmentKey()) {
            launchSingleTop = true
        }
    }

    fun openCharacterScreen(characterId: Int) {
        val bundle = Bundle().apply {
            putInt("characterId", characterId)
        }

        navController?.navigate(R.id.character_detail_fragment, bundle)
    }
}

private fun RumAutoScenarioTab.toFragmentKey(): String {
    return when (this) {
        RumAutoScenarioTab.CHARACTERS -> CHARACTERS_TAB_FRAGMENT_KEY
        RumAutoScenarioTab.EPISODES -> EPISODES_TAB_FRAGMENT_KEY
        RumAutoScenarioTab.LOCATIONS -> LOCATIONS_TAB_FRAGMENT_KEY
        RumAutoScenarioTab.DOCS -> DOCS_TAB_FRAGMENT_KEY
    }
}

private const val CHARACTERS_TAB_FRAGMENT_KEY = "characters_tab_fragment"
private const val EPISODES_TAB_FRAGMENT_KEY = "episodes_tab_fragment"
private const val LOCATIONS_TAB_FRAGMENT_KEY = "locations_tab_fragment"
private const val DOCS_TAB_FRAGMENT_KEY = "docs_tab_fragment"
private const val CHARACTER_DETAIL_FRAGMENT_KEY = "character_detail_fragment"

