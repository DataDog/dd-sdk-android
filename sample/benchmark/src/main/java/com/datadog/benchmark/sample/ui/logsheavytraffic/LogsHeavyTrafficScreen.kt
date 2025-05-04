/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

@Composable
internal fun LogsHeavyTrafficScreen(
    modifier: Modifier,
    state: LogsHeavyTrafficScreenState,
    dispatch: (LogsHeavyTrafficScreenAction) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val visibleItemChanges = snapshotFlow {
            lazyListState
                .layoutInfo
                .visibleItemsInfo
                .mapNotNull { it.key as? String }
                .toSet()
        }

        visibleItemChanges
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .collect { dispatch(LogsHeavyTrafficScreenAction.VisibleItemsChanged(it)) }
    }

    Column {
        Button(onClick = { dispatch(LogsHeavyTrafficScreenAction.OpenSettings) }) {
            Text(text = "Open settings")
        }

        LazyColumn(modifier = modifier, state = lazyListState, horizontalAlignment = Alignment.CenterHorizontally) {
            items(state.imageUrls, key = { it }) { item ->
                AsyncImage(
                    model = item,
                    contentDescription = "imageUrl $item"
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LogsHeavyTrafficScreenPreview() {
    LogsHeavyTrafficScreen(
        modifier = Modifier.fillMaxSize(),
        state = LogsHeavyTrafficScreenState(
            imageUrls = List(1) { "https://picsum.photos/800/600?random=${it}" },
            loggingConfig = LogsHeavyTrafficScreenState.LoggingConfig.INITIAL
        ),
        dispatch = {}
    )
}
