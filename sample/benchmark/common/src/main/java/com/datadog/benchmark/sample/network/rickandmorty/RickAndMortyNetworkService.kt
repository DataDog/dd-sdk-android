/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty

import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.CharacterResponse
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.network.rickandmorty.models.EpisodeResponse
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.network.rickandmorty.models.LocationResponse

interface RickAndMortyNetworkService {
    suspend fun getCharacter(id: Int): KtorHttpResponse<Character>
    suspend fun getCharacters(nextPageUrl: String?): KtorHttpResponse<CharacterResponse>
    suspend fun getCharacters(ids: List<String>): KtorHttpResponse<List<Character>>

    suspend fun getLocation(id: Int): KtorHttpResponse<Location>
    suspend fun getLocations(nextPageUrl: String?): KtorHttpResponse<LocationResponse>

    suspend fun getEpisode(id: Int): KtorHttpResponse<Episode>
    suspend fun getEpisodes(ids: List<String>): KtorHttpResponse<List<Episode>>
    suspend fun getEpisodes(nextPageUrl: String?): KtorHttpResponse<EpisodeResponse>
}
