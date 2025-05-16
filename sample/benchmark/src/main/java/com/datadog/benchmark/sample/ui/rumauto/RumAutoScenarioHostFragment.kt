/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.datadog.benchmark.sample.benchmarkActivityComponent
import com.datadog.benchmark.sample.ui.rumauto.di.DaggerRumAutoScenarioComponent
import com.datadog.benchmark.sample.ui.rumauto.di.RumAutoScenarioComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel
import com.datadog.sample.benchmark.R
import javax.inject.Inject


internal class RumAutoScenarioHostFragment: Fragment() {

    val component: RumAutoScenarioComponent by componentHolderViewModel {
        DaggerRumAutoScenarioComponent
            .factory()
            .create(requireActivity().benchmarkActivityComponent)
    }

    @Inject
    internal lateinit var navigator: RumAutoScenarioNavigator

    @Inject
    internal lateinit var viewModel: RumAutoHostViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        component.inject(this)

        val rootView = inflater.inflate(R.layout.fragment_rum_auto_host, container, false)
        rootView.findViewById<ComposeView>(R.id.rum_auto_bottom_navbar).setContent {
            RumAutoBottomNavBar(viewModel::dispatch)
        }
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        navigator.setNavController(findChildNavController())
    }

    private fun findChildNavController(): NavController {
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.child_nav_host_fragment) as NavHostFragment

        return navHostFragment.navController
    }
}
