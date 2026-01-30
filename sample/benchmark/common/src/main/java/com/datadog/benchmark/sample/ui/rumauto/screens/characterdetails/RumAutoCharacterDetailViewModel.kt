/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.utils.BenchmarkAsyncTask
import com.datadog.benchmark.sample.utils.StateMachine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RumAutoCharacterState(
    val character: Character,
    val episodesTask: BenchmarkAsyncTask<KtorHttpResponse<List<Episode>>, EpisodesTaskKey>?
) {
    class EpisodesTaskKey(val character: Character)
}

sealed interface RumAutoCharacterAction {
    object LoadEpisodes : RumAutoCharacterAction
    data class EpisodesLoadingFinished(
        val key: RumAutoCharacterState.EpisodesTaskKey,
        val response: KtorHttpResponse<List<Episode>>
    ) : RumAutoCharacterAction
}

class RumAutoCharacterDetailsViewModel(
    private val defaultDispatcher: CoroutineDispatcher,
    private val character: Character,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService
) : ViewModel() {
    private val stateMachine = StateMachine.create(
        scope = viewModelScope,
        initialState = RumAutoCharacterState(character, null),
        dispatcher = defaultDispatcher,
        processAction = ::processAction
    )

    val state = stateMachine.state

    fun dispatch(action: RumAutoCharacterAction) {
        stateMachine.dispatch(action)
    }

    private fun processAction(prev: RumAutoCharacterState, action: RumAutoCharacterAction): RumAutoCharacterState {
        return prev.copy(
            episodesTask = processLoadEpisodesTask(prev, action)
        )
    }

    private fun processLoadEpisodesTask(
        prev: RumAutoCharacterState,
        action: RumAutoCharacterAction
    ): BenchmarkAsyncTask<KtorHttpResponse<List<Episode>>, RumAutoCharacterState.EpisodesTaskKey>? {
        return when (action) {
            is RumAutoCharacterAction.LoadEpisodes -> {
                if (prev.episodesTask == null) {
                    val key = RumAutoCharacterState.EpisodesTaskKey(prev.character)
                    val job = launchEpisodesLoading(key)

                    BenchmarkAsyncTask.Loading(job, key)
                } else {
                    prev.episodesTask
                }
            }

            is RumAutoCharacterAction.EpisodesLoadingFinished -> {
                BenchmarkAsyncTask.Result(action.response, action.key)
            }
        }
    }

    private fun launchEpisodesLoading(key: RumAutoCharacterState.EpisodesTaskKey): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val response = rickAndMortyNetworkService.getEpisodes(
                ids = key.character.episode.mapNotNull { episodeIdFromUrl(it) }
            )

            dispatch(RumAutoCharacterAction.EpisodesLoadingFinished(key, response))
        }
    }
}

private fun episodeIdFromUrl(url: String): String? {
    return url.split("/").lastOrNull()
}
