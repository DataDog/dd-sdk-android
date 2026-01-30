/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(FlowPreview::class)

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.utils.LastItemTracker
import kotlinx.coroutines.FlowPreview

@Composable
fun RumAutoCharactersScreen(
    modifier: Modifier,
    state: RumAutoCharactersScreenState,
    dispatch: (RumAutoCharactersScreenAction) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()

    LastItemTracker(
        allItems = state.allCharacters,
        lazyListState = lazyListState,
        itemId = { it.id },
        onEndReached = { dispatch(RumAutoCharactersScreenAction.EndReached) }
    )

    LazyColumn(modifier = modifier, state = lazyListState) {
        items(items = state.allCharacters, key = { it.id }) { item ->
            CharacterItemView(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .clickable { dispatch(RumAutoCharactersScreenAction.CharacterItemClicked(item)) },
                character = item
            )
        }
    }
}
