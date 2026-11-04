/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import com.datadog.benchmark.sample.activities.scenarios.benchmarkActivityComponent
import com.datadog.benchmark.sample.navigation.args
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.CharacterAdapter
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di.DaggerRumAutoLocationDetailsComponent
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di.RumAutoLocationDetailsComponent
import com.datadog.benchmark.sample.utils.componentHolderViewModel
import com.datadog.sample.benchmark.R
import com.datadog.sample.benchmark.databinding.FragmentRumAutoLocationDetailsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

internal class RumAutoLocationDetailsFragment : Fragment(R.layout.fragment_rum_auto_location_details) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        component.inject(this)

        val binding = FragmentRumAutoLocationDetailsBinding.bind(view)

        val adapter = CharacterAdapter {
            viewModel.dispatch(RumAutoLocationDetailsAction.OnCharacterClicked(it))
        }

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
                    ?: emptyList()

                adapter.submitList(characters)
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }
}
