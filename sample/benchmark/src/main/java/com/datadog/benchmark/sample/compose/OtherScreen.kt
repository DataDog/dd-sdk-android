/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun OtherScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        SampleSlide()
        SampleDropDownMenu()
    }
}

@Composable
private fun SampleSlide() {
    var value by remember { mutableFloatStateOf(0f) }
    Slider(
        value = value,
        onValueChange = {
            value = it
        }
    )
}

@Composable
private fun SampleDropDownMenu() {
    var expanded by remember { mutableStateOf(false) }
    Button(
        onClick = { expanded = !expanded }
    ) {
        Text("Drop Down Menu")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = !expanded }
    ) {
        DropdownMenuItem(onClick = {}) { Text("Item 1") }
        Divider()
        DropdownMenuItem(onClick = {}) { Text("Item 2") }
        Divider()
        DropdownMenuItem(onClick = {}) { Text("Item 3") }
    }
}

@Composable
@Preview(showBackground = true)
internal fun PreviewOtherScreen() {
    OtherScreen()
}
