/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto.screens.characters

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun RumAutoCharactersScreen(modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = "RumAutoCharactersScreen")
        Button(onClick = {}) {
            Text(text = "PRESS ME")
        }
    }
}
