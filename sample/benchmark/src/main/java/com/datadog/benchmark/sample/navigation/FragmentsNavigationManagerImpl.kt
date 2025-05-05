/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.ui.logscustom.LogsFragment
import com.datadog.benchmark.sample.ui.logsheavytraffic.LogsHeavyTrafficHostFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayAppcompatFragment
import com.datadog.benchmark.sample.ui.sessionreplay.SessionReplayMaterialFragment
import javax.inject.Inject

internal class FragmentsNavigationManagerImpl @Inject constructor(
    private val config: BenchmarkConfig
) : FragmentsNavigationManager {
    private var navController: NavController? = null

    override fun setNavController(navController: NavController) {
        if (this.navController == null) {
            this.navController = navController
            navController.graph = navController.createNavGraph(config.scenario)
        }
    }

    override fun navigateToSessionReplayMaterial() {
        navController?.navigate(SESSION_REPLAY_METERIAL_FRAGMENT_KEY)
    }

    override fun navigateToSessionReplayAppCompat() {
        navController?.navigate(SESSION_REPLAY_APPCOMPAT_FRAGMENT_KEY)
    }
}

private fun NavController.createNavGraph(scenario: SyntheticsScenario?): NavGraph {
    return createGraph(startDestination = createStartDestination(scenario)) {
        navGraph(scenario = scenario)
    }
}

private fun createStartDestination(scenario: SyntheticsScenario?): String {
    return when (scenario) {
        null,
        SyntheticsScenario.SessionReplay,
        SyntheticsScenario.Upload -> SESSION_REPLAY_METERIAL_FRAGMENT_KEY
        SyntheticsScenario.SessionReplayCompose -> error("Using fragments for SessionReplayCompose scenario")
        SyntheticsScenario.LogsCustom -> LOGS_FRAGMENT_KEY
        SyntheticsScenario.LogsHeavyTraffic -> LOGS_HEAVY_TRAFFIC_FRAGMENT_KEY
        SyntheticsScenario.Rum, // TODO RUM-9510
        SyntheticsScenario.Trace -> SESSION_REPLAY_METERIAL_FRAGMENT_KEY // TODO RUM-9509
    }
}

private fun NavGraphBuilder.navGraph(scenario: SyntheticsScenario?) {
    return when (scenario) {
        null,
        SyntheticsScenario.SessionReplay,
        SyntheticsScenario.Upload -> navGraphSessionReplay()
        SyntheticsScenario.SessionReplayCompose -> error("Using fragments for SessionReplayCompose scenario")
        SyntheticsScenario.LogsCustom -> navGraphLogs()
        SyntheticsScenario.LogsHeavyTraffic -> navGraphLogsHeavyTraffic()
        SyntheticsScenario.Rum, // TODO RUM-9510
        SyntheticsScenario.Trace -> navGraphSessionReplay() // TODO RUM-9509
    }
}

private fun NavGraphBuilder.navGraphLogsHeavyTraffic() {
    fragment<LogsHeavyTrafficHostFragment>(route = LOGS_HEAVY_TRAFFIC_FRAGMENT_KEY) {
        label = "logs heavy traffic fragment"
    }
}

private fun NavGraphBuilder.navGraphLogs() {
    fragment<LogsFragment>(route = LOGS_FRAGMENT_KEY) {
        label = "logs fragment"
    }
}

private fun NavGraphBuilder.navGraphSessionReplay() {
    fragment<SessionReplayMaterialFragment>(route = SESSION_REPLAY_METERIAL_FRAGMENT_KEY) {
        label = "session replay"
    }

    fragment<SessionReplayAppcompatFragment>(route = SESSION_REPLAY_APPCOMPAT_FRAGMENT_KEY) {
        label = "session replay"
    }
}

private const val LOGS_FRAGMENT_KEY = "logs_fragment"
private const val LOGS_HEAVY_TRAFFIC_FRAGMENT_KEY = "logs_heavy_traffic_fragment"
private const val SESSION_REPLAY_METERIAL_FRAGMENT_KEY = "fragment_session_replay_material"
private const val SESSION_REPLAY_APPCOMPAT_FRAGMENT_KEY = "fragment_session_replay_appcompat"
