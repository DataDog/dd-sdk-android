/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.CharactersRowItem
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.DetailsHeaderItem
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.DetailsInfoItem
import com.datadog.benchmark.sample.utils.BenchmarkAsyncTask
import com.datadog.benchmark.sample.utils.StateMachine
import com.datadog.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RumAutoEpisodeDetailsViewModelFactory @AssistedInject constructor(
    @Assisted private val episode: Episode,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RumAutoEpisodeDetailsViewModel(
            episode = episode,
            rickAndMortyNetworkService = rickAndMortyNetworkService,
            defaultDispatcher = defaultDispatcher
        ) as T
    }
}

@AssistedFactory
internal interface AssistedRumAutoEpisodeDetailsViewModelFactory {
    fun create(episode: Episode): RumAutoEpisodeDetailsViewModelFactory
}

internal data class RumAutoEpisodeDetailsState(
    val episode: Episode,
    val charactersLoadingTask: BenchmarkAsyncTask<KtorHttpResponse<List<Character>>, CharactersLoadingTask>
) {
    class CharactersLoadingTask(val ids: List<String>)
}

internal sealed interface RumAutoEpisodeDetailsAction {
    data class CharactersLoadingTaskFinished(val result: KtorHttpResponse<List<Character>>, val task: RumAutoEpisodeDetailsState.CharactersLoadingTask): RumAutoEpisodeDetailsAction
}

internal class RumAutoEpisodeDetailsViewModel(
    private val episode: Episode,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
    private val defaultDispatcher: CoroutineDispatcher,
): ViewModel() {
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

    val state: Flow<List<BaseRecyclerViewItem>> = stateMachine.state.map {
        withContext(defaultDispatcher) {
            it.toViewState()
        }
    }

    fun dispatch(action: RumAutoEpisodeDetailsAction) {
        stateMachine.dispatch(action)
    }

    private fun processAction(prev: RumAutoEpisodeDetailsState, action: RumAutoEpisodeDetailsAction): RumAutoEpisodeDetailsState {
        return when (action) {
            is RumAutoEpisodeDetailsAction.CharactersLoadingTaskFinished -> {
                prev.copy(
                    charactersLoadingTask = BenchmarkAsyncTask.Result(action.result, action.task)
                )
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

    private fun RumAutoEpisodeDetailsState.toViewState(): List<BaseRecyclerViewItem> {
        return buildList {
            add(DetailsHeaderItem(text = episode.name, "header"))
            add(DetailsInfoItem(startText = "Episode", endText = episode.episodeCode, key = "code"))
            add(DetailsInfoItem(startText = "Air date", endText = episode.airDate, key = "info_air_date"))
            add(DetailsInfoItem(startText = "Created", endText = episode.created, key = "created"))
            add(DetailsHeaderItem(text = "Characters: ${episode.characters.count()}", key = "characters_count"))

            charactersLoadingTask
                .optionalResult
                ?.optionalResult
                ?.chunked(3)
                ?.forEach { chunk ->
                    add(CharactersRowItem(chunk, "characters_row"))
                }
        }
    }
}
