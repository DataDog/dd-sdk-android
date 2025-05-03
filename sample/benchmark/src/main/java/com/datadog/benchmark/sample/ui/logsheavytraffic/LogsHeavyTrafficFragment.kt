/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.datadog.benchmark.sample.MainActivity
import com.datadog.benchmark.sample.ui.logsheavytraffic.di.DaggerLogsHeavyTrafficComponent
import com.datadog.sample.benchmark.R

internal class LogsHeavyTrafficFragment: Fragment() {

    val viewModel by viewModels<LogsHeavyTrafficViewModel> {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.child_nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        DaggerLogsHeavyTrafficComponent.factory().create(
            deps = (requireActivity() as MainActivity).benchmarkActivityComponent,
            navController = navController
        ).viewModelFactory
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        (requireActivity() as MainActivity).benchmarkActivityComponent.inject(this)

        val view = inflater.inflate(R.layout.fragment_logs_heavy_traffic, container, false)

        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setContent {
                val state by viewModel.states().collectAsStateWithLifecycle()

                LogsHeavyTrafficScreen(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    dispatch = viewModel::dispatch
                )
            }
        }

        return view
    }
}
