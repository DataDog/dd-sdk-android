/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.datadog.benchmark.sample.network.rickandmorty.models.Character

@Composable
fun CharacterItemView(modifier: Modifier, character: Character) {
    Row(modifier = modifier) {
        AsyncImage(
            model = character.image,
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(text = character.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "${character.species} ${character.status}", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = character.origin.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CharacterItemViewPreview() {
    CharacterItemView(
        modifier = Modifier.fillMaxWidth(),
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
        )
    )
}
