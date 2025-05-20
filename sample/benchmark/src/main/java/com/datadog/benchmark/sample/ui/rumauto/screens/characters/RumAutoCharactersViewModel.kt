/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.benchmark.sample.navigation.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.CharacterResponse
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersScreenState.PageLoadingTask
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersScreenState.PageLoadingTaskResult
import com.datadog.benchmark.sample.utils.StateMachine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface RumAutoCharactersScreenAction {
    data class PageLoadingFinished(
        val task: PageLoadingTask,
        val response: KtorHttpResponse<CharacterResponse>
    ) : RumAutoCharactersScreenAction {
        override fun isValid(state: RumAutoCharactersScreenState): Boolean {
            return state.task?.first === task
        }
    }

    object LoadNextPage : RumAutoCharactersScreenAction
    data class VisibleItemsChanged(val items: Set<String>): RumAutoCharactersScreenAction

    data class CharacterItemClicked(val character: Character): RumAutoCharactersScreenAction

    object EndReached : RumAutoCharactersScreenAction

    fun isValid(state: RumAutoCharactersScreenState): Boolean {
        return true
    }
}

internal data class RumAutoCharactersScreenState(
    val pages: List<Page>,
    val task: Pair<PageLoadingTask, PageLoadingTaskResult>?
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
    private val rumAutoScenarioNavigator: RumAutoScenarioNavigator
): ViewModel() {

    private val stateMachine = StateMachine.create(
        initialState = RumAutoCharactersScreenState.INITIAL.copy(
            task = run {
                val task = PageLoadingTask(null)
                task to PageLoadingTaskResult.Loading(loadNextPage(task))
            }
        ),
        processAction = ::processAction,
        dispatcher = defaultDispatcher,
        scope = viewModelScope
    )

    val state: StateFlow<RumAutoCharactersScreenState> = stateMachine.state

    fun dispatch(action: RumAutoCharactersScreenAction) {
        stateMachine.dispatch(action)
    }

    private fun processAction(prev: RumAutoCharactersScreenState, action: RumAutoCharactersScreenAction): RumAutoCharactersScreenState {
        if (!action.isValid(prev)) {
            return prev
        }

        return when (action) {
            is RumAutoCharactersScreenAction.CharacterItemClicked -> {
                viewModelScope.launch {
                    rumAutoScenarioNavigator.openCharacterScreen(action.character)
                }
                prev
            }
            else -> prev.copy(
                pages = processPages(prev, action),
                task = processTask(prev, action)
            )
        }

    }

    private fun processTask(prev: RumAutoCharactersScreenState, action: RumAutoCharactersScreenAction): Pair<PageLoadingTask, PageLoadingTaskResult>? {
        return when (action) {
            RumAutoCharactersScreenAction.EndReached -> {
                if (prev.task != null) {
                    val (_, result) = prev.task

                    when (result) {
                        is PageLoadingTaskResult.Loading -> prev.task
                        is PageLoadingTaskResult.Result -> {
                            val newTask = PageLoadingTask(result.response.optionalResult?.info?.next)
                            newTask to PageLoadingTaskResult.Loading(loadNextPage(newTask))
                        }
                    }

                } else {
                    null
                }
            }
            is RumAutoCharactersScreenAction.PageLoadingFinished -> {
                action.task to PageLoadingTaskResult.Result(action.response)
            }
            else -> prev.task
        }
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

    private fun loadNextPage(task: PageLoadingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val response = rickAndMortyNetworkService.getCharacters(task.nextPageUrl)
            stateMachine.dispatch(RumAutoCharactersScreenAction.PageLoadingFinished(task, response))
        }
    }
}
