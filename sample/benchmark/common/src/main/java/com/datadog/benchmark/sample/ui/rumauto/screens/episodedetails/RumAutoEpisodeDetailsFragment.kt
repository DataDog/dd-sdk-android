/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import com.datadog.benchmark.sample.activities.scenarios.benchmarkActivityComponent
import com.datadog.benchmark.sample.navigation.args
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.CharacterItem
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails.di.DaggerRumAutoEpisodeDetailsComponent
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails.di.RumAutoEpisodeDetailsComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel
import com.datadog.benchmark.sample.utils.recycler.applyNewItems
import com.datadog.sample.benchmark.databinding.FragmentRumAutoEpisodeDetailsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class RumAutoEpisodeDetailsFragment : Fragment() {

    private val episode: Episode by args()

    private val component: RumAutoEpisodeDetailsComponent by componentHolderViewModel {
        DaggerRumAutoEpisodeDetailsComponent.factory().create(
            deps = requireActivity().benchmarkActivityComponent,
            viewModelScope = viewModelScope,
            episode = episode
        )
    }

    @Inject
    lateinit var adapter: RumAutoEpisodeDetailsAdapter

    @Inject
    lateinit var viewModel: RumAutoEpisodeDetailsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        component.inject(this)

        val binding: FragmentRumAutoEpisodeDetailsBinding = FragmentRumAutoEpisodeDetailsBinding.inflate(
            inflater,
            container,
            false
        )

        @Suppress("MagicNumber")
        binding.characterDetailsRecycler.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.characterDetailsRecycler.adapter = adapter

        viewModel.state
            .onEach { state ->
                binding.episodeDetailsTitle.text = state.episode.name
                binding.episodeDetailsCode.text = state.episode.episodeCode
                binding.episodeDetailsAirDate.text = state.episode.airDate
                binding.episodeDetailsCreated.text = state.episode.created

                val characters = state
                    .charactersLoadingTask
                    .optionalResult
                    ?.optionalResult
                    ?.map { character ->
                        CharacterItem(
                            character = character,
                            key = character.id.toString()
                        )
                    } ?: emptyList()

                adapter.applyNewItems(characters)
            }
            .launchIn(lifecycleScope)

        return binding.root
    }
}
