/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodedetail

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.datadog.benchmark.sample.benchmarkActivityComponent
import com.datadog.sample.benchmark.databinding.FragmentRumAutoEpisodeDetailsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class RumAutoEpisodeDetailsFragment: Fragment() {

    @Inject
    internal lateinit var adapter: RumAutoEpisodeDetailsAdapter

    @Inject
    internal lateinit var viewModelFactory: AssistedRumAutoEpisodeDetailsViewModelFactory

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().benchmarkActivityComponent.inject(this)

        val binding: FragmentRumAutoEpisodeDetailsBinding = FragmentRumAutoEpisodeDetailsBinding.inflate(inflater, container, false)

        binding.characterDetailsRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.characterDetailsRecycler.adapter = adapter

        // TODO WAHAHA
        val viewModel by viewModels<RumAutoEpisodeDetailsViewModel> { viewModelFactory.create(arguments?.getParcelable("episode")!!) }

        viewModel.state
            .onEach {
                adapter.items = it
                adapter.notifyDataSetChanged()
            }
            .launchIn(lifecycleScope)

        return binding.root
    }
}
