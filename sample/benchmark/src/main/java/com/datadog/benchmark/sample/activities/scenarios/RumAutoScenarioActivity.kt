/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.activities.scenarios

import android.app.Activity
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import androidx.tracing.Trace
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityComponent
import com.datadog.benchmark.sample.navigation.NavigationGraphInitializer
import com.datadog.benchmark.sample.ui.rumauto.RumAutoBottomNavBar
import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import com.datadog.sample.benchmark.R
import com.datadog.sample.benchmark.databinding.FragmentRumAutoHostBinding
import javax.inject.Inject

internal class RumAutoScenarioActivity : BaseScenarioActivity() {
    @Inject
    internal lateinit var rumAutoScenarioNavigator: RumAutoScenarioNavigator

    @Inject
    internal lateinit var navigationGraphInitializer: NavigationGraphInitializer

    @Inject
    internal lateinit var benchmarkConfig: BenchmarkConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        benchmarkActivityComponent.inject(this)

        val binding = FragmentRumAutoHostBinding.inflate(layoutInflater)

        supportActionBar?.hide()
        setContentView(binding.root)

        binding.rumAutoBottomNavbar.setContent {
            val currentTab by rumAutoScenarioNavigator.currentTab.collectAsStateWithLifecycle(null)

            currentTab?.let { tab ->
                RumAutoBottomNavBar(tab) {
                    rumAutoScenarioNavigator.openTab(it)
                }
            }
        }

        Trace.endAsyncSection("wahaha_section", 0)
    }

    override fun onResume() {
        super.onResume()

        val navController = findNavController(R.id.nav_host_fragment)
        navigationGraphInitializer.initialize(navController, benchmarkConfig.scenario)

        rumAutoScenarioNavigator.setNavController(findNavController(R.id.nav_host_fragment))
    }
}

internal val Activity.benchmarkActivityComponent: BenchmarkActivityComponent
    get() = (this as BaseScenarioActivity).viewModel.component
