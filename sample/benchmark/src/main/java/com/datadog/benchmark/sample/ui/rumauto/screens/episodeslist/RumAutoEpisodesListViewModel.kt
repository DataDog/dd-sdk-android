/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodeslist

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.EpisodeResponse
import com.datadog.benchmark.sample.network.rickandmorty.models.LocationResponse
import com.datadog.benchmark.sample.ui.rumauto.screens.episodeslist.RumAutoEpisodesListState.EpisodesTaskKey
import com.datadog.benchmark.sample.utils.BenchmarkAsyncTask
import com.datadog.benchmark.sample.utils.StateMachine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class RumAutoEpisodesListViewModelFactory @Inject constructor(
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default)
    private val defaultDispatcher: CoroutineDispatcher,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
): ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RumAutoEpisodesListViewModel(
            defaultDispatcher = defaultDispatcher,
            rickAndMortyNetworkService = rickAndMortyNetworkService
        ) as T
    }
}

internal sealed interface RumAutoEpisodesListAction {
    data class EpisodesListLoadingFinished(
        val response: KtorHttpResponse<EpisodeResponse>,
        val task: EpisodesTaskKey
    ): RumAutoEpisodesListAction

    object EndReached: RumAutoEpisodesListAction
}

internal data class RumAutoEpisodesListState(
    val pages: List<Page>,
    val episodesListTask: BenchmarkAsyncTask<KtorHttpResponse<EpisodeResponse>, EpisodesTaskKey>?,
) {
    class EpisodesTaskKey(val pageUrl: String?)

    data class Page(val response: EpisodeResponse)
}

internal class RumAutoEpisodesListViewModel(
    private val defaultDispatcher: CoroutineDispatcher,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
): ViewModel() {

    private val stateMachine = StateMachine.create(
        initialState = RumAutoEpisodesListState(
            episodesListTask = run {
                val task = EpisodesTaskKey(null)
                val job = launchNextPageLoadingTask(task)
                BenchmarkAsyncTask.Loading(job = job, key = task)
            },
            pages = emptyList()
        ),
        dispatcher = defaultDispatcher,
        scope = viewModelScope,
        processAction = ::processAction
    )

    val state = stateMachine.state

    fun dispatch(action: RumAutoEpisodesListAction) {
        stateMachine.dispatch(action)
    }

    private fun processAction(prev: RumAutoEpisodesListState, action: RumAutoEpisodesListAction): RumAutoEpisodesListState {
        return prev.copy(
            episodesListTask = processTask(prev, action),
            pages = processPages(prev, action)
        )
    }

    private fun processTask(prev: RumAutoEpisodesListState, action: RumAutoEpisodesListAction): BenchmarkAsyncTask<KtorHttpResponse<EpisodeResponse>, EpisodesTaskKey>? {
        return when (action) {
            is RumAutoEpisodesListAction.EpisodesListLoadingFinished -> {
                BenchmarkAsyncTask.Result(action.response, action.task)
            }
            is RumAutoEpisodesListAction.EndReached -> {
                when (prev.episodesListTask) {
                    is BenchmarkAsyncTask.Loading<*> -> prev.episodesListTask
                    is BenchmarkAsyncTask.Result<*, *> -> {
                        val nextPage = prev.episodesListTask.optionalResult?.optionalResult?.info?.next
                        if (nextPage != null) {
                            val task = EpisodesTaskKey(pageUrl = nextPage)
                            val job = launchNextPageLoadingTask(task)
                            BenchmarkAsyncTask.Loading(job = job, key = task)
                        } else {
                            prev.episodesListTask
                        }
                    }
                    null -> {
                        val task = EpisodesTaskKey(pageUrl = null)
                        val job = launchNextPageLoadingTask(task)
                        BenchmarkAsyncTask.Loading(job = job, key = task)
                    }
                }
            }
            else -> prev.episodesListTask
        }
    }

    private fun processPages(prev: RumAutoEpisodesListState, action: RumAutoEpisodesListAction): List<RumAutoEpisodesListState.Page> {
        return when (action) {
            is RumAutoEpisodesListAction.EpisodesListLoadingFinished -> {
                val newPage = action.response.optionalResult?.let { characters -> RumAutoEpisodesListState.Page(characters) }
                if (newPage != null) {
                    prev.pages + newPage
                } else {
                    prev.pages
                }
            }
            else -> prev.pages
        }
    }

    private fun launchNextPageLoadingTask(task: EpisodesTaskKey): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val response = rickAndMortyNetworkService.getEpisodes(nextPageUrl = task.pageUrl)
            delay(3000)
            dispatch(RumAutoEpisodesListAction.EpisodesListLoadingFinished(response, task))
        }
    }
}
