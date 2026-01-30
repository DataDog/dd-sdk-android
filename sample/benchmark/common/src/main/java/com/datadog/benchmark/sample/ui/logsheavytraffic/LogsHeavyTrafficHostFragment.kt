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
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.datadog.benchmark.sample.activities.scenarios.benchmarkActivityComponent
import com.datadog.benchmark.sample.ui.logsheavytraffic.di.DaggerLogsHeavyTrafficComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel
import com.datadog.sample.benchmark.R
import javax.inject.Inject

class LogsHeavyTrafficHostFragment : Fragment() {
    val component by componentHolderViewModel {
        DaggerLogsHeavyTrafficComponent.factory().create(
            deps = requireActivity().benchmarkActivityComponent,
            viewModelScope = viewModelScope
        )
    }

    @Inject
    lateinit var navigationManager: LogsHeavyTrafficNavigationManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        component.inject(this)

        return inflater.inflate(R.layout.fragment_logs_heavy_traffic_host, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigationManager.setNavController(findChildNavController())
    }

    private fun findChildNavController(): NavController {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.child_nav_host_fragment) as NavHostFragment

        return navHostFragment.navController
    }
}
