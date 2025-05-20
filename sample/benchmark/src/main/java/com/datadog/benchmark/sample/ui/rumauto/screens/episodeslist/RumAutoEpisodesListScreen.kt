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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RumAutoEpisodesListScreen(
    modifier: Modifier,
    state: RumAutoEpisodesListState,
) {
//    val allItems by remember { derivedStateOf { state.pages.flatMap { it.response.results } } }

    LazyColumn(modifier = modifier) {
        items(state.pages.flatMap { it.response.results }, key = { it.id }) { item ->
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
