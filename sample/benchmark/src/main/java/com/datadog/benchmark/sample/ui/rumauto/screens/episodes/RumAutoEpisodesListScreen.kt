/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.episodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.utils.LastItemTracker

@Composable
internal fun RumAutoEpisodesListScreen(
    modifier: Modifier,
    state: RumAutoEpisodesListState,
    dispatch: (RumAutoEpisodesListAction) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()

    LastItemTracker(
        allItems = state.allEpisodes,
        lazyListState = lazyListState,
        onEndReached = { dispatch(RumAutoEpisodesListAction.EndReached) },
        itemId = { it.id }
    )

    Box(modifier = modifier) {
        LazyColumn(state = lazyListState) {
            items(state.allEpisodes, key = { it.id }) { item ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.clickable { dispatch(RumAutoEpisodesListAction.EpisodeClicked(item)) }
                ) {
                    Text(text = item.name, style = MaterialTheme.typography.labelLarge)
                    Row {
                        Text(text = item.episodeCode, modifier = Modifier.weight(1.0f))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = item.airDate)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider(
                    modifier = Modifier.height(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
            }
        }
    }
}
