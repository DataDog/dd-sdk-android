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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.datadog.benchmark.sample.benchmarkActivityComponent
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetail.di.DaggerRumAutoEpisodeDetailsComponent
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetail.di.RumAutoEpisodeDetailsComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel
import com.datadog.benchmark.sample.utils.recycler.applyNewItems
import com.datadog.sample.benchmark.databinding.FragmentRumAutoEpisodeDetailsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class RumAutoEpisodeDetailsFragment: Fragment() {

    internal val component: RumAutoEpisodeDetailsComponent by componentHolderViewModel {
        // TODO WAHAHA
        val episode = arguments?.getParcelable<Episode>("episode")!!
        DaggerRumAutoEpisodeDetailsComponent.factory().create(
            deps = requireActivity().benchmarkActivityComponent,
            viewModelScope = viewModelScope,
            episode = episode
        )
    }

    @Inject
    internal lateinit var adapter: RumAutoEpisodeDetailsAdapter

    @Inject
    internal lateinit var viewModel: RumAutoEpisodeDetailsViewModel

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        component.inject(this)

        val binding: FragmentRumAutoEpisodeDetailsBinding = FragmentRumAutoEpisodeDetailsBinding.inflate(inflater, container, false)

        binding.characterDetailsRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        binding.characterDetailsRecycler.adapter = adapter

        viewModel.state
            .onEach {
                adapter.applyNewItems(it)
            }
            .launchIn(lifecycleScope)

        return binding.root
    }
}
