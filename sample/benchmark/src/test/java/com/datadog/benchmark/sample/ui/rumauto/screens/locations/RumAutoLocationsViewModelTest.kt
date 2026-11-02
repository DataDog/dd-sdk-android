/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.locations

import com.datadog.benchmark.sample.network.KtorHttpResponse
import com.datadog.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import com.datadog.benchmark.sample.network.rickandmorty.models.Location
import com.datadog.benchmark.sample.network.rickandmorty.models.LocationResponse
import com.datadog.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@OptIn(ExperimentalCoroutinesApi::class)
internal class RumAutoLocationsViewModelTest {

    @Mock
    lateinit var mockNetworkService: RickAndMortyNetworkService

    @Mock
    lateinit var mockNavigator: RumAutoScenarioNavigator

    @Test
    fun `M append next page and navigate to clicked location W dispatch()`(forge: Forge) = runTest {
        // Given
        val fakeLocations = forge.aList(size = 3) {
            Location(
                id = anInt(min = 1),
                name = anAlphabeticalString(),
                type = anAlphabeticalString(),
                dimension = anAlphabeticalString(),
                residents = emptyList(),
                url = aStringMatching("https://[a-z]+[.]com/location/[0-9]+"),
                created = anAlphabeticalString()
            )
        }.mapIndexed { index, location -> location.copy(id = index + 1) }
        val fakeNextUrl = forge.aStringMatching("https://[a-z]+[.]com/location[?]page=2")
        val fakeFirstPage = LocationResponse(
            LocationResponse.PageInfo(count = 3, pages = 2, next = fakeNextUrl, prev = null),
            fakeLocations.take(2)
        )
        val fakeLastPage = LocationResponse(
            LocationResponse.PageInfo(count = 3, pages = 2, next = null, prev = null),
            fakeLocations.drop(2)
        )
        whenever(mockNetworkService.getLocations(null)).thenReturn(KtorHttpResponse.Success(fakeFirstPage))
        whenever(mockNetworkService.getLocations(fakeNextUrl)).thenReturn(KtorHttpResponse.Success(fakeLastPage))
        val testedViewModel = RumAutoLocationsViewModel(
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            mockNetworkService,
            mockNavigator
        )
        var fakeItems = emptyList<RumAutoLocationItem>()
        testedViewModel.state.onEach { fakeItems = it }.launchIn(backgroundScope)
        runCurrent()

        // When
        testedViewModel.dispatch(RumAutoLocationsAction.VisibleItemsChanged(fakeItems))
        runCurrent()
        testedViewModel.dispatch(RumAutoLocationsAction.VisibleItemsChanged(fakeItems.takeLast(1)))
        testedViewModel.dispatch(RumAutoLocationsAction.OnLocationClicked(fakeLocations.last().id))
        runCurrent()

        // Then
        assertThat(fakeItems.map { it.locationId }).containsExactlyElementsOf(fakeLocations.map { it.id })
        verify(mockNetworkService).getLocations(null)
        verify(mockNetworkService).getLocations(fakeNextUrl)
        verifyNoMoreInteractions(mockNetworkService)
        verify(mockNavigator).openLocationDetailsScreen(fakeLocations.last())
    }
}
