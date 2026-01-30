/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characterdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.datadog.benchmark.sample.network.rickandmorty.models.Character
import com.datadog.benchmark.sample.ui.common.ExpandableListCard

@Composable
fun RumAutoCharacterScreen(
    state: RumAutoCharacterState,
    modifier: Modifier = Modifier,
    dispatch: (RumAutoCharacterAction) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 8.dp)
    ) {
        AsyncImage(
            model = state.character.image,
            contentDescription = state.character.name,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(200.dp),
            contentScale = ContentScale.FillWidth
        )
        Text(text = state.character.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = state.character.status.toString(), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = state.character.species, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = state.character.gender.toString(), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = state.character.created, style = MaterialTheme.typography.labelLarge)

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

@Preview(showBackground = true)
@Composable
fun RumAutoCharacterScreenPreview() {
    RumAutoCharacterScreen(
        modifier = Modifier.fillMaxSize(),
        state = RumAutoCharacterState(
            character = Character(
                id = 1,
                name = "Rick Sanchez",
                status = Character.Status.ALIVE,
                species = "Human",
                type = "",
                gender = Character.Gender.MALE,
                origin = Character.LocationInfo("Earth (C-137)", ""),
                location = Character.LocationInfo("Citadel of Ricks", ""),
                image = "",
                episode = emptyList(),
                url = "",
                created = ""
            ),
            episodesTask = null
        )
    )
}
