/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datadog.benchmark.sample.activities.scenarios.benchmarkActivityComponent
import com.datadog.benchmark.sample.navigation.args
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import javax.inject.Inject

class RumAutoCharacterDetailFragment : Fragment() {

    private val character: Character by args()

    @Inject
    lateinit var viewModelFactory: AssistedRumAutoCharacterDetailViewModelFactory

    private val viewModel: RumAutoCharacterDetailsViewModel by viewModels {
        viewModelFactory.create(character)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity().benchmarkActivityComponent.inject(this)

        return ComposeView(requireContext()).apply {
            setContent {
                val state by viewModel.state.collectAsStateWithLifecycle()
                RumAutoCharacterScreen(state, dispatch = viewModel::dispatch)
            }
        }
    }
}
