/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

internal class RumAutoEpisodesListViewModelFactory @Inject constructor(
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
    private val defaultDispatcher: CoroutineDispatcher,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
    private val rumAutoScenarioNavigator: RumAutoScenarioNavigator
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RumAutoEpisodesListViewModel(
            defaultDispatcher = defaultDispatcher,
            rickAndMortyNetworkService = rickAndMortyNetworkService,
            rumAutoScenarioNavigator = rumAutoScenarioNavigator
        ) as T
    }
}
