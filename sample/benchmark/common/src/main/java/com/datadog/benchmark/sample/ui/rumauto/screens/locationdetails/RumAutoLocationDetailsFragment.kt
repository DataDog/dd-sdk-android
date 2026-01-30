/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails

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
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.CharacterItem
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di.DaggerRumAutoLocationDetailsComponent
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di.RumAutoLocationDetailsComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel
import com.datadog.benchmark.sample.utils.recycler.applyNewItems
import com.datadog.sample.benchmark.databinding.FragmentRumAutoLocationDetailsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class RumAutoLocationDetailsFragment : Fragment() {

    private val location: Location by args()

    private val component: RumAutoLocationDetailsComponent by componentHolderViewModel {
        DaggerRumAutoLocationDetailsComponent.factory().create(
            deps = requireActivity().benchmarkActivityComponent,
            location = location,
            viewModelScope = viewModelScope
        )
    }

    @Inject
    lateinit var viewModel: RumAutoLocationDetailsViewModel

    @Inject
    lateinit var adapter: RumAutoLocationDetailsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        component.inject(this)

        val binding = FragmentRumAutoLocationDetailsBinding.inflate(inflater, container, false)

        @Suppress("MagicNumber")
        binding.locationDetailsRecycler.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.locationDetailsRecycler.adapter = adapter

        viewModel.state
            .onEach { state ->
                binding.locationDetailsTitle.text = state.location.name
                binding.locationDetailsType.text = state.location.type
                binding.locationDetailsDimension.text = state.location.dimension
                binding.locationDetailsCreated.text = state.location.created

                val characters = state
                    .residentsLoadingTask
                    .optionalResult
                    ?.optionalResult
                    ?.map {
                        CharacterItem(
                            character = it,
                            key = it.id.toString()
                        )
                    } ?: emptyList()

                adapter.applyNewItems(characters)
            }
            .launchIn(lifecycleScope)

        return binding.root
    }
}
