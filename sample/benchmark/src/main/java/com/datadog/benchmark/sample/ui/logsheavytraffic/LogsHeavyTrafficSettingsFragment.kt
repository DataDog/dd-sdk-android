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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

internal class LogsHeavyTrafficSettingsFragment : Fragment() {
    @Inject
    internal lateinit var viewModel: LogsHeavyTrafficViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        (parentFragment?.parentFragment as LogsHeavyTrafficHostFragment).component.inject(this)

        return ComposeView(requireActivity()).apply {
            setContent {
                val state by viewModel.logsHeavyTrafficState.collectAsStateWithLifecycle()

                val config by remember { derivedStateOf { state.loggingConfig } }

                LogsHeavyTrafficSettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    dispatch = viewModel::dispatch,
                    config = config
                )
            }
        }
    }
}
