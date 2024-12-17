/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun SelectorSample() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SampleSlider()
        SampleBottomSheetDialog()
        SampleDropDownMenu()
    }
}

@Composable
private fun SampleSlider() {
    var value by remember { mutableFloatStateOf(0.0f) }
    Text("Slider")
    Slider(
        value = value,
        colors = SliderDefaults.colors(
            thumbColor = Color.Red,
            activeTrackColor = Color.Green
        ),
        onValueChange = {
            value = it
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleBottomSheetDialog() {
    var expanded by remember { mutableStateOf(false) }
    Button(onClick = {
        expanded = !expanded
    }) {
        Text(text = "Modal Bottom sheet")
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "This is a modal bottom sheet"
            )
        }
    }
}

@Composable
private fun SampleDropDownMenu() {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Button(onClick = {
            expanded = !expanded
        }) {
            Text("Drop Down Menu")
        }
        if (expanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                DropdownMenuItem(
                    text = {
                        Text("item 1")
                    },
                    onClick = {}
                )
                DropdownMenuItem(
                    text = {
                        Text("item 2")
                    },
                    onClick = {}
                )
                DropdownMenuItem(
                    text = {
                        Text("item 3")
                    },
                    onClick = {}
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
internal fun PreviewSelelctorSample() {
    SelectorSample()
}
