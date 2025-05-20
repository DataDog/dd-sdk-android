/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodeslist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.ui.rumauto.screens.characters.RumAutoCharactersScreenAction
import com.datadog.benchmark.sample.utils.LastItemTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
@Composable
internal fun RumAutoEpisodesListScreen(
    modifier: Modifier,
    allItems: List<Episode>,
    dispatch: (RumAutoEpisodesListAction) -> Unit
) {
//    val allItems by remember { derivedStateOf { state.pages.flatMap { it.response.results } } }

    val lazyListState: LazyListState = rememberLazyListState()

//    LastItemTracker(
//        allItems = allItems,
//        lazyListState = lazyListState,
//        onEndReached = { dispatch(RumAutoEpisodesListAction.EndReached) },
//        itemId = { it.id }
//    )

    LaunchedEffect(allItems) {
        val lastItemChanges = snapshotFlow {
            val lastItem = lazyListState
                .layoutInfo
                .visibleItemsInfo
                .lastOrNull()
                ?.key

            val indexOfLastVisible = allItems.indexOfFirst { it.id == lastItem }

            indexOfLastVisible to allItems.lastIndex
        }

        lastItemChanges
            .distinctUntilChangedBy { it.first }
            .debounce(2.seconds)
            .flowOn(Dispatchers.Default)
            .collect { (indexOfLastVisible, lastIndex) ->
                if (indexOfLastVisible == lastIndex) {
                    dispatch(RumAutoEpisodesListAction.EndReached)
                }
            }
    }

    LazyColumn(modifier = modifier) {
        items(allItems, key = { it.id }) { item ->
            Column {
                Text(text = item.name)
                Row {
                    Text(text = item.episodeCode)
                    Text(text = item.airDate)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
