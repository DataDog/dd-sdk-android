/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datadog.benchmark.sample.activities.scenarios.benchmarkActivityComponent
import javax.inject.Inject

internal class RumAutoCharactersFragment : Fragment() {

    @Inject
    internal lateinit var viewModelFactory: RumAutoCharactersViewModelFactory

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().benchmarkActivityComponent.inject(this)

        val viewModel: RumAutoCharactersViewModel by viewModels {
            viewModelFactory
        }

        return ComposeView(requireActivity()).apply {
            setContent {
                val state by viewModel.state.collectAsStateWithLifecycle()

                RumAutoCharactersScreen(
                    modifier = Modifier.fillMaxSize().semantics {
                        testTagsAsResourceId = true
                    },
                    state = state,
                    dispatch = viewModel::dispatch
                )
            }
        }
    }
}
