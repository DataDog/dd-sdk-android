/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import androidx.navigation.navigation
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityScope
import com.datadog.benchmark.sample.ui.logscustom.LogsFragment
import com.datadog.benchmark.sample.ui.logsheavytraffic.LogsHeavyTrafficHostFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.characterdetails.RumAutoCharacterDetailFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails.RumAutoEpisodeDetailsFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.episodes.RumAutoEpisodesListFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.RumAutoLocationDetailsFragment
import com.datadog.benchmark.sample.ui.rumauto.screens.locations.RumAutoLocationsFragment
import com.datadog.benchmark.sample.ui.rummanual.RumManualScenarioFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayAppcompatFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayMaterialFragment
import com.datadog.benchmark.sample.ui.trace.TraceScenarioFragment
import com.datadog.sample.benchmark.R
import javax.inject.Inject

@Suppress("DEPRECATION")
@BenchmarkActivityScope
internal class NavigationGraphInitializer @Inject constructor() {

    fun initialize(navController: NavController, scenario: SyntheticsScenario?) {
        navController.graph = navController.createGraph(startDestination = createStartDestination(scenario)) {
            navGraph(scenario = scenario)
        }
    }

    private fun createStartDestination(scenario: SyntheticsScenario?): Int {
        return when (scenario) {
            null,
            SyntheticsScenario.SessionReplay,
            SyntheticsScenario.Upload -> R.id.fragment_session_replay_material

            SyntheticsScenario.SessionReplayCompose -> error("Using fragments for SessionReplayCompose scenario")
            SyntheticsScenario.LogsCustom -> R.id.logs_fragment
            SyntheticsScenario.LogsHeavyTraffic -> R.id.logs_heavy_traffic_fragment
            SyntheticsScenario.RumAuto -> R.id.characters_tab_navigation
            SyntheticsScenario.RumManual -> R.id.fragment_rum_manual_scenario
            SyntheticsScenario.Trace -> R.id.fragment_trace_scenario
        }
    }

    private fun NavGraphBuilder.navGraph(scenario: SyntheticsScenario?) {
        when (scenario) {
            null,
            SyntheticsScenario.SessionReplay,
            SyntheticsScenario.Upload -> navGraphSessionReplay()

            SyntheticsScenario.SessionReplayCompose -> error("Using fragments for SessionReplayCompose scenario")
            SyntheticsScenario.LogsCustom -> navGraphLogs()
            SyntheticsScenario.LogsHeavyTraffic -> navGraphLogsHeavyTraffic()
            SyntheticsScenario.RumAuto -> navGraphRumAutoScenario()
            SyntheticsScenario.RumManual -> navGraphRumManualScenario()
            SyntheticsScenario.Trace -> navGraphTraceScenario()
        }
    }

    private fun NavGraphBuilder.navGraphLogsHeavyTraffic() {
        fragment<LogsHeavyTrafficHostFragment>(R.id.logs_heavy_traffic_fragment) {
            label = "logs heavy traffic fragment"
        }
    }

    private fun NavGraphBuilder.navGraphLogs() {
        fragment<LogsFragment>(R.id.logs_fragment) {
            label = "logs fragment"
        }
    }

    private fun NavGraphBuilder.navGraphSessionReplay() {
        fragment<SessionReplayMaterialFragment>(R.id.fragment_session_replay_material) {
            label = "session replay"
        }

        fragment<SessionReplayAppcompatFragment>(R.id.fragment_session_replay_appcompat) {
            label = "session replay"
        }
    }

    private fun NavGraphBuilder.navGraphTraceScenario() {
        fragment<TraceScenarioFragment>(R.id.fragment_trace_scenario) {
            label = "trace scenario"
        }
    }

    private fun NavGraphBuilder.navGraphRumManualScenario() {
        fragment<RumManualScenarioFragment>(R.id.fragment_rum_manual_scenario) {
            label = "rum manual scenario"
        }
    }

    private fun NavGraphBuilder.navGraphRumAutoScenario() {
        navigation(R.id.characters_tab_navigation, R.id.characters_tab_fragment) {
            fragment<RumAutoCharactersFragment>(R.id.characters_tab_fragment) {
                label = "Characters screen"
            }
            characterDetailsFragment()
        }

        navigation(R.id.locations_tab_navigation, R.id.locations_tab_fragment) {
            fragment<RumAutoLocationsFragment>(R.id.locations_tab_fragment) {
                label = "Locations screen"
            }
            fragment<RumAutoLocationDetailsFragment>(R.id.location_details_fragment) {
                label = "Episode detail screen"
            }
            characterDetailsFragment()
        }

        navigation(R.id.episodes_tab_navigation, R.id.episodes_tab_fragment) {
            fragment<RumAutoEpisodesListFragment>(R.id.episodes_tab_fragment) {
                label = "Episodes screen"
            }
            fragment<RumAutoEpisodeDetailsFragment>(R.id.episode_detail_fragment) {
                label = "Episode detail screen"
            }
            characterDetailsFragment()
        }
    }

    private fun NavGraphBuilder.characterDetailsFragment() {
        fragment<RumAutoCharacterDetailFragment>(R.id.character_detail_fragment) {
            label = "Character detail screen"
        }
    }
}
