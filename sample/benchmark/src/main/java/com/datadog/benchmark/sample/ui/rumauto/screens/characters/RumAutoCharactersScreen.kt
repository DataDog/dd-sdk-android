/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun RumAutoCharactersScreen(
    modifier: Modifier,
    state: RumAutoCharactersScreenState,
    dispatch: (RumAutoCharactersScreenAction) -> Unit
) {
    Column(modifier = modifier) {
        LazyColumn {
            items(state.pages.flatMap { it.response.results }, key = { it.id }) { item ->
                Text(text = item.name)
            }
        }
    }
}
