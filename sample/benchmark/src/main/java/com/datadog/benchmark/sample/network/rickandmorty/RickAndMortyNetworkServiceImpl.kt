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
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.network.safeGet
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import javax.inject.Inject

internal class RickAndMortyNetworkServiceImpl @Inject constructor(
    private val httpClient: HttpClient,
): RickAndMortyNetworkService {

    override suspend fun getCharacter(id: Int): KtorHttpResponse<Character> {
        val url = URLBuilder(BASE_URL).apply {
            appendPathSegments(CHARACTER_PATH, id.toString())
        }.build()

        return httpClient.safeGet<Character>(url)
    }

    override suspend fun getLocation(id: Int): KtorHttpResponse<Location> {
        val url = URLBuilder(BASE_URL).apply {
            appendPathSegments(LOCATION_PATH, id.toString())
        }.build()

        return httpClient.safeGet<Location>(url)
    }

    override suspend fun getEpisode(id: Int): KtorHttpResponse<Episode> {
        val url = URLBuilder(BASE_URL).apply {
            appendPathSegments(EPISODE_PATH, id.toString())
        }.build()

        return httpClient.safeGet<Episode>(url)
    }

    override suspend fun getCharacters(nextPageUrl: String?): KtorHttpResponse<CharacterResponse> {
        val url = if (nextPageUrl != null) {
            Url(nextPageUrl)
        } else {
            URLBuilder(BASE_URL).apply {
                appendPathSegments(CHARACTER_PATH)
            }.build()
        }

        return httpClient.safeGet(url)
    }

    override suspend fun getEpisodes(ids: List<String>): KtorHttpResponse<List<Episode>> {
        val url = URLBuilder(BASE_URL).apply {
            appendPathSegments(EPISODE_PATH, ids.joinToString(","))
        }.build()

        return httpClient.safeGet<List<Episode>>(url)
    }
}

private const val BASE_URL = "https://rickandmortyapi.com/api"
private const val CHARACTER_PATH = "character"
private const val LOCATION_PATH = "location"
private const val EPISODE_PATH = "episode"
