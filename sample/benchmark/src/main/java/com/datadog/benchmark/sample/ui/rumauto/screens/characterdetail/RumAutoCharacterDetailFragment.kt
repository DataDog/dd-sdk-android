/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datadog.benchmark.sample.benchmarkActivityComponent
import com.datadog.benchmark.sample.di.activity.ViewModelQualifier
import javax.inject.Inject

internal class RumAutoCharacterDetailFragment: Fragment() {

    @Inject
    @ViewModelQualifier(RumAutoCharacterDetailsViewModel::class)
    internal lateinit var viewModelFactory: ViewModelProvider.Factory


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireActivity().benchmarkActivityComponent.inject(this)

        val viewModel: RumAutoCharacterDetailsViewModel by viewModels { viewModelFactory }

        val characterId = arguments?.getInt("characterId")

        return ComposeView(requireContext()).apply {
            setContent {
                 val state by viewModel.state.collectAsStateWithLifecycle()
                RumAutoCharacterScreen("${state.message} $characterId")
            }
        }
    }
}
