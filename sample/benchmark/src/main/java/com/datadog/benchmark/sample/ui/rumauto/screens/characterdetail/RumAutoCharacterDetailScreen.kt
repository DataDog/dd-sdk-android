/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import com.datadog.benchmark.sample.ui.common.ExpandableListCard

@Composable
internal fun RumAutoCharacterScreen(
    state: RumAutoCharacterState,
    modifier: Modifier = Modifier,
    dispatch: (RumAutoCharacterAction) -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        AsyncImage(
            model = state.character.image,
            contentDescription = state.character.name,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(text = state.character.name)
        Text(text = state.character.status.toString())
        Text(text = state.character.species)
        Text(text = state.character.gender.toString())
        Text(text = state.character.created)

        ExpandableListCard(
            title = "Appears in ${state.character.episode.count()} episodes",
            items = state.episodesTask?.optionalResult?.optionalResult?.map { it.name } ?: emptyList(),
            onTitleClicked = { isExpanded ->
                if (isExpanded) {
                    dispatch(RumAutoCharacterAction.LoadEpisodes)
                }
            }
        )
    }
}
