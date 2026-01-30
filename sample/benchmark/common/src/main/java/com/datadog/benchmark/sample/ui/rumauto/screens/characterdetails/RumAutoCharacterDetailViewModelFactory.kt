/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher

class RumAutoCharacterDetailViewModelFactory @AssistedInject constructor(
    @Assisted private val character: Character,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
    private val defaultDispatcher: CoroutineDispatcher,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RumAutoCharacterDetailsViewModel(
            defaultDispatcher = defaultDispatcher,
            character = character,
            rickAndMortyNetworkService = rickAndMortyNetworkService
        ) as T
    }
}

@AssistedFactory
interface AssistedRumAutoCharacterDetailViewModelFactory {
    fun create(character: Character): RumAutoCharacterDetailViewModelFactory
}
