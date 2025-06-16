/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails

import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.ui.rumauto.screens.episodedetails.di.RumAutoEpisodeDetailsScope
import com.datadog.benchmark.sample.utils.BenchmarkAsyncTask
import com.datadog.benchmark.sample.utils.StateMachine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class RumAutoEpisodeDetailsState(
    val episode: Episode,
    val charactersLoadingTask: BenchmarkAsyncTask<KtorHttpResponse<List<Character>>, CharactersLoadingTask>
) {
    class CharactersLoadingTask(val ids: List<String>)
}

internal sealed interface RumAutoEpisodeDetailsAction {
    data class CharactersLoadingTaskFinished(
        val result: KtorHttpResponse<List<Character>>,
        val task: RumAutoEpisodeDetailsState.CharactersLoadingTask
    ) : RumAutoEpisodeDetailsAction

    data class OnCharacterClicked(val character: Character) : RumAutoEpisodeDetailsAction
}

@RumAutoEpisodeDetailsScope
internal class RumAutoEpisodeDetailsViewModel @Inject constructor(
    private val episode: Episode,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
    private val viewModelScope: CoroutineScope,
    private val rumAutoScenarioNavigator: RumAutoScenarioNavigator
) {
    private val stateMachine = StateMachine.create(
        scope = viewModelScope,
        dispatcher = defaultDispatcher,
        initialState = RumAutoEpisodeDetailsState(
            episode = episode,
            charactersLoadingTask = run {
                val task = RumAutoEpisodeDetailsState.CharactersLoadingTask(characterIds(episode))
                val job = launchCharactersLoadingTask(task)
                BenchmarkAsyncTask.Loading(job, task)
            }
        ),
        processAction = ::processAction
    )

    val state: StateFlow<RumAutoEpisodeDetailsState> = stateMachine.state

    fun dispatch(action: RumAutoEpisodeDetailsAction) {
        stateMachine.dispatch(action)
    }

    private fun processAction(
        prev: RumAutoEpisodeDetailsState,
        action: RumAutoEpisodeDetailsAction
    ): RumAutoEpisodeDetailsState {
        return when (action) {
            is RumAutoEpisodeDetailsAction.CharactersLoadingTaskFinished -> {
                prev.copy(
                    charactersLoadingTask = BenchmarkAsyncTask.Result(action.result, action.task)
                )
            }

            is RumAutoEpisodeDetailsAction.OnCharacterClicked -> {
                viewModelScope.launch {
                    rumAutoScenarioNavigator.openCharacterScreen(action.character)
                }
                prev
            }
        }
    }

    private fun launchCharactersLoadingTask(task: RumAutoEpisodeDetailsState.CharactersLoadingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val result = rickAndMortyNetworkService.getCharacters(task.ids)
            dispatch(RumAutoEpisodeDetailsAction.CharactersLoadingTaskFinished(result, task))
        }
    }

    private fun characterIds(episode: Episode): List<String> {
        return episode.characters.mapNotNull { it.split("/").lastOrNull() }
    }
}
