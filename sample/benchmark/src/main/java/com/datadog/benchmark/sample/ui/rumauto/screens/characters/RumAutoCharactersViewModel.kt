/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.lifecycle.ViewModel
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService

internal class RumAutoCharactersViewModel(
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
): ViewModel() {
}

