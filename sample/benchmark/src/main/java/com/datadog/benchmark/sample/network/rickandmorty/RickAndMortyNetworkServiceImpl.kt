/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty

import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.safeGet
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.path
import javax.inject.Inject

internal class RickAndMortyNetworkServiceImpl @Inject constructor(
    private val httpClient: HttpClient,
): RickAndMortyNetworkService {

    override suspend fun getCharacter(id: Int): Character? {
        val url = URLBuilder(BASE_URL).apply {
            path(CHARACTER_PATH)
            path(id.toString())
        }.build()

        return httpClient.safeGet<Character>(url).optionalResult
    }
}

private const val BASE_URL = "https://rickandmortyapi.com/api"
private const val CHARACTER_PATH = "character"
