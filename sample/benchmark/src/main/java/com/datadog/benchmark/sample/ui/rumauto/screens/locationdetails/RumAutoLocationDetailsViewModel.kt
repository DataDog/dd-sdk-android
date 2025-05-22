/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails

import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.navigation.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.CharactersRowItem
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.DetailsHeaderItem
import com.datadog.benchmark.sample.ui.rumauto.screens.common.details.DetailsInfoItem
import com.datadog.benchmark.sample.ui.rumauto.screens.locationdetails.di.RumAutoLocationDetailsScope
import com.datadog.benchmark.sample.utils.BenchmarkAsyncTask
import com.datadog.benchmark.sample.utils.StateMachine
import com.datadog.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class RumAutoLocationDetailsState(
    val location: Location,
    val residentsLoadingTask: BenchmarkAsyncTask<KtorHttpResponse<List<Character>>, ResidentsLoadingTask>
) {
    class ResidentsLoadingTask(val ids: List<String>)
}

internal sealed interface RumAutoLocationDetailsAction {
    data class ResidentsLoadingFinished(val response: KtorHttpResponse<List<Character>>, val task: RumAutoLocationDetailsState.ResidentsLoadingTask): RumAutoLocationDetailsAction
    data class OnCharacterClicked(val character: Character): RumAutoLocationDetailsAction
}

@RumAutoLocationDetailsScope
internal class RumAutoLocationDetailsViewModel @Inject constructor(
    private val navigator: RumAutoScenarioNavigator,
    private val location: Location,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
    private val viewModelScope: CoroutineScope,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
) {

    private val stateMachine = StateMachine.create(
        initialState = RumAutoLocationDetailsState(
            residentsLoadingTask = run {
                val task = RumAutoLocationDetailsState.ResidentsLoadingTask(characterIds(location))
                val job = launchResidentsLoadingTask(task)
                BenchmarkAsyncTask.Loading(job = job, key = task)
            },
            location = location
        ),
        dispatcher = defaultDispatcher,
        scope = viewModelScope,
        processAction = ::processAction
    )

    val state: Flow<List<BaseRecyclerViewItem>> get() = stateMachine.state.map {
        withContext(defaultDispatcher) {
            it.toViewState()
        }
    }

    fun dispatch(action: RumAutoLocationDetailsAction) {
        stateMachine.dispatch(action)
    }

    private fun processAction(prev: RumAutoLocationDetailsState, action: RumAutoLocationDetailsAction): RumAutoLocationDetailsState {
        return when (action) {
            is RumAutoLocationDetailsAction.OnCharacterClicked -> {
                viewModelScope.launch {
                    navigator.openCharacterScreen(action.character)
                }
                prev
            }
            else -> prev.copy(
                residentsLoadingTask = processTask(prev, action)
            )
        }
    }

    private fun processTask(prev: RumAutoLocationDetailsState, action: RumAutoLocationDetailsAction): BenchmarkAsyncTask<KtorHttpResponse<List<Character>>, RumAutoLocationDetailsState.ResidentsLoadingTask> {
        return when (action) {
            is RumAutoLocationDetailsAction.ResidentsLoadingFinished -> {
                BenchmarkAsyncTask.Result(action.response, action.task)
            }
            else -> prev.residentsLoadingTask
        }
    }

    private fun characterIds(location: Location): List<String> {
        return location.residents.mapNotNull { it.split("/").lastOrNull() }
    }

    private fun launchResidentsLoadingTask(task: RumAutoLocationDetailsState.ResidentsLoadingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val response = rickAndMortyNetworkService.getCharacters(task.ids)
            dispatch(RumAutoLocationDetailsAction.ResidentsLoadingFinished(response, task))
        }
    }

    private fun RumAutoLocationDetailsState.toViewState(): List<BaseRecyclerViewItem> {
        return buildList {
            add(DetailsHeaderItem(text = location.name, key = "header"))
            add(DetailsInfoItem(startText = "Type", endText = location.type, key = "type"))
            add(DetailsInfoItem(startText = "Dimension", endText = location.dimension, key = "dimension"))
            add(DetailsInfoItem(startText = "Created", endText = location.created, key = "created"))

            add(DetailsHeaderItem(text = "Residents: ${location.residents.count()}", key = "residents_count"))

            residentsLoadingTask
                .optionalResult
                ?.optionalResult
                ?.chunked(3)
                ?.forEach { chunk ->
                    add(CharactersRowItem(characters = chunk, key = "residents_row_${chunk.joinToString(",") { it.id.toString() }}"))
                }
        }
    }
}
