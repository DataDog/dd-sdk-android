/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locations

import com.datadog.benchmark.sample.di.common.CoroutineDispatcherQualifier
import com.datadog.benchmark.sample.di.common.CoroutineDispatcherType
import com.datadog.benchmark.sample.navigation.RumAutoScenarioNavigator
import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.LocationResponse
import com.datadog.benchmark.sample.ui.rumauto.screens.locations.di.RumAutoLocationsScope
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

internal data class RumAutoLocationsState(
    val pageLoadingTask: BenchmarkAsyncTask<KtorHttpResponse<LocationResponse>, PageLoadingTask>,
    val pages: List<Page>
) {
    class PageLoadingTask(val nextUrl: String?)
    data class Page(val result: LocationResponse)
}

internal sealed interface RumAutoLocationsAction {
    data class OnLocationClicked(val locationId: Int): RumAutoLocationsAction
    data class LocationsPageLoadingFinished(val response: KtorHttpResponse<LocationResponse>, val task: RumAutoLocationsState.PageLoadingTask): RumAutoLocationsAction
}

@RumAutoLocationsScope
internal class RumAutoLocationsViewModel @Inject constructor(
    private val viewModelScope: CoroutineScope,
    @CoroutineDispatcherQualifier(CoroutineDispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
    private val rickAndMortyNetworkService: RickAndMortyNetworkService,
    private val rumAutoScenarioNavigator: RumAutoScenarioNavigator,
) {

    private val stateMachine = StateMachine.create(
        initialState = RumAutoLocationsState(
            pageLoadingTask = run {
                val task = RumAutoLocationsState.PageLoadingTask(null)
                val job = launchNextPageLoadingTask(task)
                BenchmarkAsyncTask.Loading(job = job, key = task)
            },
            pages = emptyList()
        ),
        dispatcher = defaultDispatcher,
        scope = viewModelScope,
        processAction = ::processAction
    )

    fun dispatch(action: RumAutoLocationsAction) {
        stateMachine.dispatch(action)
    }

    val state: Flow<List<BaseRecyclerViewItem>> get() = stateMachine.state.map {
        withContext(defaultDispatcher) {
            it.toViewState()
        }
    }

    private fun processAction(prev: RumAutoLocationsState, action: RumAutoLocationsAction): RumAutoLocationsState {
        return when (action) {
            is RumAutoLocationsAction.OnLocationClicked -> {
                prev.pages
                    .flatMap { it.result.results }
                    .firstOrNull { it.id == action.locationId }
                    ?.let { location ->
                        viewModelScope.launch {
                            rumAutoScenarioNavigator.openLocationDetailsScreen(location)
                        }
                    }
                prev
            }
            else -> prev.copy(
                pageLoadingTask = processTask(prev, action),
                pages = processPages(prev, action)
            )
        }
    }

    private fun processTask(prev: RumAutoLocationsState, action: RumAutoLocationsAction): BenchmarkAsyncTask<KtorHttpResponse<LocationResponse>, RumAutoLocationsState.PageLoadingTask> {
        return when (action) {
            is RumAutoLocationsAction.LocationsPageLoadingFinished -> {
                BenchmarkAsyncTask.Result(action.response, action.task)
            }
            else -> prev.pageLoadingTask
        }
    }

    private fun processPages(prev: RumAutoLocationsState, action: RumAutoLocationsAction): List<RumAutoLocationsState.Page> {
        return when (action) {
            is RumAutoLocationsAction.LocationsPageLoadingFinished -> {
                val newPage = action.response.optionalResult?.let { locations -> RumAutoLocationsState.Page(locations) }
                if (newPage != null) {
                    prev.pages + newPage
                } else {
                    prev.pages
                }
            }
            else -> prev.pages
        }
    }

    private fun launchNextPageLoadingTask(task: RumAutoLocationsState.PageLoadingTask): Job {
        return viewModelScope.launch(defaultDispatcher) {
            val response = rickAndMortyNetworkService.getLocations(nextPageUrl = task.nextUrl)
            dispatch(RumAutoLocationsAction.LocationsPageLoadingFinished(response, task))
        }
    }

    private fun RumAutoLocationsState.toViewState(): List<BaseRecyclerViewItem> {
        return pages
            .flatMap { it.result.results }
            .map { location ->
                RumAutoLocationItem(
                    title = location.name,
                    firstSubtitle = location.type,
                    secondSubtitle = location.dimension,
                    locationId = location.id,
                    key = location.id.toString()
                )
            }
    }
}
