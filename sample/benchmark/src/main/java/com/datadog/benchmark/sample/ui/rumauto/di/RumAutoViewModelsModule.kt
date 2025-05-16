/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.di

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.datadog.benchmark.sample.di.activity.ViewModelQualifier
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersViewModel
import dagger.Module
import dagger.Provides

@Module
internal interface RumAutoViewModelsModule {
    companion object {
        @Provides
        @ViewModelQualifier(RumAutoCharactersViewModel::class)
        fun provideRumAutoCharactersViewModelFactory(
            rickAndMortyNetworkService: RickAndMortyNetworkService,
        ) = viewModelFactory {
            initializer {
                RumAutoCharactersViewModel(
                    rickAndMortyNetworkService = rickAndMortyNetworkService
                )
            }
        }
    }
}
