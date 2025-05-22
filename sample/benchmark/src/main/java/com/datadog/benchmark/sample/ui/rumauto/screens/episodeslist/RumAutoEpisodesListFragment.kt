/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodeslist

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datadog.android.api.SdkCore
import com.datadog.benchmark.sample.benchmarkActivityComponent
import com.datadog.benchmark.sample.utils.BenchmarkAsyncTask
import javax.inject.Inject

internal class RumAutoEpisodesListFragment: Fragment() {
    @Inject
    internal lateinit var viewModelFactory: RumAutoEpisodesListViewModelFactory

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        requireActivity().benchmarkActivityComponent.inject(this)

        val viewModel by viewModels<RumAutoEpisodesListViewModel> { viewModelFactory }

        return ComposeView(requireContext()).apply {
            setContent {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // TODO WAHAHA think
                val allItems by remember { derivedStateOf { state.pages.flatMap { it.response.results } } }

                RumAutoEpisodesListScreen(
                    modifier = Modifier.fillMaxSize(),
                    allItems = allItems,
                    dispatch = viewModel::dispatch,
                    isLoading = state.episodesListTask is BenchmarkAsyncTask.Loading
                )
            }
        }
    }
}
