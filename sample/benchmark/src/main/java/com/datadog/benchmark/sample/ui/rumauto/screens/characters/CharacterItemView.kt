/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.datadog.benchmark.sample.network.rickandmorty.models.Character

@Composable
internal fun CharacterItemView(modifier: Modifier, character: Character) {
    Row(modifier = modifier) {
        AsyncImage(
            model = character.image,
            contentDescription = null,
        )

        Column {
            Text(text = character.name)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CharacterItemViewPreview() {
    CharacterItemView(
        modifier = Modifier.fillMaxSize(),
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
