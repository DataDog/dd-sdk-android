/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.CharacterResponse
import com.datadog.benchmark.sample.utils.StateMachine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface RumAutoCharactersScreenAction {
    data class PageLoadingFinished(val task: RumAutoCharactersScreenState.PageLoadingTask, val response: KtorHttpResponse<CharacterResponse>) : RumAutoCharactersScreenAction
}

internal data class RumAutoCharactersScreenState(
    val pages: List<Page>,
    val task: Pair<PageLoadingTask, PageLoadingTaskResult>?,
) {
    class PageLoadingTask(val nextPageUrl: String?)

    sealed interface PageLoadingTaskResult {
        data class Result(val response: KtorHttpResponse<CharacterResponse>) : PageLoadingTaskResult
        data class Loading(val job: Job) : PageLoadingTaskResult
    }

    data class Page(val response: CharacterResponse)

    companion object {
        val INITIAL = RumAutoCharactersScreenState(
            pages = emptyList(),
            task = null,
        )
    }
}

internal class RumAutoCharactersViewModel(
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
    private val defaultDispatcher: CoroutineDispatcher,
): ViewModel() {

    private val stateMachine = StateMachine.create<RumAutoCharactersScreenAction, RumAutoCharactersScreenState>(
        initialState = RumAutoCharactersScreenState.INITIAL.copy(
            task = run {
                val task = RumAutoCharactersScreenState.PageLoadingTask(null)
                task to RumAutoCharactersScreenState.PageLoadingTaskResult.Loading(loadNextPage(task))
            }
        ),
        processAction = { prev, action ->
            prev.copy(
                pages = processPages(prev, action)
            )
        },
        dispatcher = defaultDispatcher,
        scope = viewModelScope
    )

    val state: StateFlow<RumAutoCharactersScreenState> = stateMachine.state

    fun dispatch(action: RumAutoCharactersScreenAction) {
        stateMachine.dispatch(action)
    }

    private fun processPages(prev: RumAutoCharactersScreenState, action: RumAutoCharactersScreenAction): List<RumAutoCharactersScreenState.Page> {
        return when (action) {
            is RumAutoCharactersScreenAction.PageLoadingFinished -> {
                val newPage = action.response.optionalResult?.let { characters -> RumAutoCharactersScreenState.Page(characters) }
                if (newPage != null) {
                    prev.pages + newPage
                } else {
                    prev.pages
                }
            }
            else -> prev.pages
        }
    }

    private fun loadNextPage(task: RumAutoCharactersScreenState.PageLoadingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val response = rickAndMortyNetworkService.getCharacters(task.nextPageUrl)
            stateMachine.dispatch(RumAutoCharactersScreenAction.PageLoadingFinished(task, response))
        }
    }
}
