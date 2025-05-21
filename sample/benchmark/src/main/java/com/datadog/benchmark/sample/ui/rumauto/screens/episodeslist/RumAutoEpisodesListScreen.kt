/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodeslist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.network.rickandmorty.models.Episode
import com.datadog.benchmark.sample.utils.LastItemTracker

@Composable
internal fun RumAutoEpisodesListScreen(
    modifier: Modifier,
    isLoading: Boolean,
    allItems: List<Episode>,
    dispatch: (RumAutoEpisodesListAction) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()

    LastItemTracker(
        allItems = allItems,
        lazyListState = lazyListState,
        onEndReached = { dispatch(RumAutoEpisodesListAction.EndReached) },
        itemId = { it.id }
    )

    Box(modifier = modifier) {
        if (isLoading) {
            CircularProgressIndicator()
        }

        LazyColumn(state = lazyListState) {
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


}
