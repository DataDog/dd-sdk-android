/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.fragment.FragmentNavigator
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityScope
import com.datadog.benchmark.sample.ui.logscustom.LogsFragment
import com.datadog.benchmark.sample.ui.logsheavytraffic.LogsHeavyTrafficHostFragment
import com.datadog.benchmark.sample.ui.profiling.ProfilingScenarioFragment
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

@Suppress("TooManyFunctions")
@BenchmarkActivityScope
internal class NavigationGraphInitializer @Inject constructor() {

    fun initialize(navController: NavController, scenario: SyntheticsScenario?) {
        val builder = ScenarioGraphBuilder(navController.navigatorProvider, createStartDestination(scenario))
        builder.navGraph(scenario)
        navController.graph = builder.graph
    }

    private fun createStartDestination(scenario: SyntheticsScenario?): Int {
        return when (scenario) {
            null,
            SyntheticsScenario.SessionReplay,
            SyntheticsScenario.Upload -> R.id.fragment_session_replay_material

            SyntheticsScenario.SessionReplayCompose -> error("Using fragments for SessionReplayCompose scenario")
            SyntheticsScenario.LogsCustom -> R.id.logs_fragment
            SyntheticsScenario.LogsHeavyTraffic -> R.id.logs_heavy_traffic_fragment
            SyntheticsScenario.RumAuto,
            SyntheticsScenario.Timeseries -> R.id.characters_tab_navigation
            SyntheticsScenario.RumManual -> R.id.fragment_rum_manual_scenario
            SyntheticsScenario.Trace -> R.id.fragment_trace_scenario
            SyntheticsScenario.Profiling -> R.id.fragment_profiling
        }
    }

    private fun ScenarioGraphBuilder.navGraph(scenario: SyntheticsScenario?) {
        when (scenario) {
            null,
            SyntheticsScenario.SessionReplay,
            SyntheticsScenario.Upload -> navGraphSessionReplay()

            SyntheticsScenario.SessionReplayCompose -> error("Using fragments for SessionReplayCompose scenario")
            SyntheticsScenario.LogsCustom -> navGraphLogs()
            SyntheticsScenario.LogsHeavyTraffic -> navGraphLogsHeavyTraffic()
            SyntheticsScenario.RumAuto,
            SyntheticsScenario.Timeseries -> navGraphRumAutoScenario()
            SyntheticsScenario.RumManual -> navGraphRumManualScenario()
            SyntheticsScenario.Trace -> navGraphTraceScenario()
            SyntheticsScenario.Profiling -> navGraphProfilingScenario()
        }
    }

    private fun ScenarioGraphBuilder.navGraphLogsHeavyTraffic() {
        fragment<LogsHeavyTrafficHostFragment>(R.id.logs_heavy_traffic_fragment, "logs heavy traffic fragment")
    }

    private fun ScenarioGraphBuilder.navGraphLogs() {
        fragment<LogsFragment>(R.id.logs_fragment, "logs fragment")
    }

    private fun ScenarioGraphBuilder.navGraphSessionReplay() {
        fragment<SessionReplayMaterialFragment>(R.id.fragment_session_replay_material, "session replay")

        fragment<SessionReplayAppcompatFragment>(R.id.fragment_session_replay_appcompat, "session replay")
    }

    private fun ScenarioGraphBuilder.navGraphProfilingScenario() {
        fragment<ProfilingScenarioFragment>(R.id.fragment_profiling, "profiling scenario")
    }

    private fun ScenarioGraphBuilder.navGraphTraceScenario() {
        fragment<TraceScenarioFragment>(R.id.fragment_trace_scenario, "trace scenario")
    }

    private fun ScenarioGraphBuilder.navGraphRumManualScenario() {
        fragment<RumManualScenarioFragment>(R.id.fragment_rum_manual_scenario, "rum manual scenario")
    }

    private fun ScenarioGraphBuilder.navGraphRumAutoScenario() {
        navigation(R.id.characters_tab_navigation, R.id.characters_tab_fragment) {
            fragment<RumAutoCharactersFragment>(R.id.characters_tab_fragment, "Characters screen")
            characterDetailsFragment()
        }

        navigation(R.id.locations_tab_navigation, R.id.locations_tab_fragment) {
            fragment<RumAutoLocationsFragment>(R.id.locations_tab_fragment, "Locations screen")
            fragment<RumAutoLocationDetailsFragment>(R.id.location_details_fragment, "Episode detail screen")
            characterDetailsFragment()
        }

        navigation(R.id.episodes_tab_navigation, R.id.episodes_tab_fragment) {
            fragment<RumAutoEpisodesListFragment>(R.id.episodes_tab_fragment, "Episodes screen")
            fragment<RumAutoEpisodeDetailsFragment>(R.id.episode_detail_fragment, "Episode detail screen")
            characterDetailsFragment()
        }
    }

    private fun ScenarioGraphBuilder.characterDetailsFragment() {
        fragment<RumAutoCharacterDetailFragment>(R.id.character_detail_fragment, "Character detail screen")
    }

    private class ScenarioGraphBuilder(
        private val navigators: NavigatorProvider,
        @IdRes startDestination: Int,
        @IdRes id: Int = 0
    ) {

        val graph: NavGraph = navigators.getNavigator(NavGraphNavigator::class.java).createDestination().apply {
            this.id = id
            setStartDestination(startDestination)
        }

        inline fun <reified T : Fragment> fragment(@IdRes id: Int, label: String) {
            val destination = navigators.getNavigator(FragmentNavigator::class.java).createDestination().apply {
                this.id = id
                this.label = label
                setClassName(T::class.java.name)
            }
            graph.addDestination(destination)
        }

        fun navigation(@IdRes id: Int, @IdRes startDestination: Int, build: ScenarioGraphBuilder.() -> Unit) {
            val nested = ScenarioGraphBuilder(navigators, startDestination, id)
            nested.build()
            graph.addDestination(nested.graph)
        }
    }
}
