/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(FlowPreview::class)

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun RumAutoCharactersScreen(
    modifier: Modifier,
    allItems: List<Character>,
    dispatch: (RumAutoCharactersScreenAction) -> Unit
) {
    val lazyListState: LazyListState = rememberLazyListState()

    LaunchedEffect(allItems) {
        val lastItemChanges = snapshotFlow {
            val lastItem = lazyListState
                .layoutInfo
                .visibleItemsInfo
                .lastOrNull()
                ?.key as? Int

            val indexOfLastVisible = allItems.indexOfFirst { it.id == lastItem }

            indexOfLastVisible to allItems.lastIndex
        }

        lastItemChanges
            .distinctUntilChangedBy { it.first }
            .debounce(2.seconds)
            .flowOn(Dispatchers.Default)
            .collect { (indexOfLastVisible, lastIndex) ->
                if (indexOfLastVisible == lastIndex) {
                    dispatch(RumAutoCharactersScreenAction.EndReached)
                }
            }
    }

    LazyColumn(modifier = modifier, state = lazyListState) {
        items(items = allItems, key = { it.id }) { item ->
            CharacterItemView(
                modifier = Modifier.fillMaxWidth(),
                character = item
            )
        }
    }
}
