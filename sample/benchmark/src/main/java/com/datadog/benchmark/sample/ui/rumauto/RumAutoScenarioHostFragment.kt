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
import androidx.fragment.app.Fragment
import com.datadog.benchmark.sample.benchmarkActivityComponent
import com.datadog.benchmark.sample.ui.rumauto.di.DaggerRumAutoScenarioComponent
import com.datadog.benchmark.sample.ui.rumauto.di.RumAutoScenarioComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel

internal class RumAutoScenarioHostFragment: Fragment() {

    val component: RumAutoScenarioComponent by componentHolderViewModel {
        DaggerRumAutoScenarioComponent
            .factory()
            .create(requireActivity().benchmarkActivityComponent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }
}
