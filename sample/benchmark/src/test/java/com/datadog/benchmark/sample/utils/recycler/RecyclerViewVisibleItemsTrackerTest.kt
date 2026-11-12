/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.utils.recycler

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecyclerViewVisibleItemsTrackerTest {

    @Mock
    lateinit var mockRecyclerView: RecyclerView

    @Mock
    lateinit var stubLayoutManager: LinearLayoutManager

    @Mock
    lateinit var stubAdapter: ListAdapter<String, RecyclerView.ViewHolder>

    @Test
    fun `M track committed items and remove listener W trackRecyclerViewVisibleItems() {list grows}`(
        forge: Forge
    ) = runTest {
        // Given
        val fakeItems = forge.aList(size = 3) { anAlphabeticalString() }
        whenever(stubAdapter.currentList).thenReturn(emptyList())
        whenever(stubLayoutManager.findFirstVisibleItemPosition()).thenReturn(RecyclerView.NO_POSITION)
        whenever(stubLayoutManager.findLastVisibleItemPosition()).thenReturn(RecyclerView.NO_POSITION)
        val fakeEmissions = mutableListOf<List<String>>()
        val testedFlow = trackRecyclerViewVisibleItems(mockRecyclerView, stubLayoutManager, stubAdapter)

        // When
        val collection = testedFlow.onEach { fakeEmissions.add(it) }.launchIn(backgroundScope)
        runCurrent()
        val scrollListener = argumentCaptor<RecyclerView.OnScrollListener>().apply {
            verify(mockRecyclerView).addOnScrollListener(capture())
        }.firstValue
        whenever(stubAdapter.currentList).thenReturn(fakeItems.take(2))
        whenever(stubLayoutManager.findFirstVisibleItemPosition()).thenReturn(0)
        whenever(stubLayoutManager.findLastVisibleItemPosition()).thenReturn(1)
        scrollListener.onScrolled(mockRecyclerView, 0, 0)
        runCurrent()
        scrollListener.onScrolled(mockRecyclerView, 0, 0)
        runCurrent()
        whenever(stubAdapter.currentList).thenReturn(fakeItems)
        whenever(stubLayoutManager.findFirstVisibleItemPosition()).thenReturn(1)
        whenever(stubLayoutManager.findLastVisibleItemPosition()).thenReturn(2)
        scrollListener.onScrolled(mockRecyclerView, 0, 1)
        runCurrent()
        collection.cancelAndJoin()

        // Then
        assertThat(fakeEmissions).containsExactly(emptyList(), fakeItems.take(2), fakeItems.drop(1))
        verify(mockRecyclerView).removeOnScrollListener(scrollListener)
    }
}
