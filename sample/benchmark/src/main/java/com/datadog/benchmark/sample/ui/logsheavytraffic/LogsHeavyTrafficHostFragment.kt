/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.datadog.benchmark.sample.MainActivity
import com.datadog.benchmark.sample.ui.logsheavytraffic.di.DaggerLogsHeavyTrafficComponent
import com.datadog.sample.benchmark.R

internal class LogsHeavyTrafficHostFragment: NavHostFragment() {
    val viewModel by viewModels<LogsHeavyTrafficViewModel> {
        DaggerLogsHeavyTrafficComponent.factory().create(
            deps = (requireActivity() as MainActivity).benchmarkActivityComponent,
            navController = findChildNavController()
        ).viewModelFactory
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_logs_heavy_traffic, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findChildNavController().graph = navController.createGraph(startDestination = "logs_heavy_traffic") {
            fragment<LogsHeavyTrafficFragment>("logs_heavy_traffic")
            fragment<LogsHeavyTrafficSettingsFragment>("logs_heavy_traffic_settings")
        }
    }

    private fun findChildNavController(): NavController {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.child_nav_host_fragment) as NavHostFragment

        return navHostFragment.navController
    }
}
