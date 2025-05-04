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
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

internal class LogsHeavyTrafficSettingsFragment: Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewModel = (parentFragment?.parentFragment as LogsHeavyTrafficHostFragment).viewModel

        return ComposeView(requireActivity()).apply {
            setContent {
                LogsHeavyTrafficSettingsScreen(viewModel::dispatch)
            }
        }
    }
}
