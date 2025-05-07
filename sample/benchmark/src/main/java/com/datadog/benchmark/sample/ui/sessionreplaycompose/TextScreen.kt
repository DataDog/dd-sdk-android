/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.sessionreplaycompose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun TextScreen() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SampleTypography()
        SampleTextField()
        SampleSwitch()
        SampleRadioButton()
        SampleCheckbox()
    }
}

@Composable
private fun SampleTypography(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = "Material H3 - onBackground",
            style = MaterialTheme.typography.h3,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colors.onBackground
        )
        Text(
            text = "Material H4 - onSurface",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colors.onSurface
        )

        Text(
            text = "Material H6 - Red",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(16.dp),
            color = Color.Red
        )

        Text(
            text = "Material Body 1 - Green",
            style = MaterialTheme.typography.body1.copy(
                color = Color.Blue
            ),
            modifier = Modifier.padding(16.dp),
            color = Color.Green
        )

        Text(
            text = "Material Caption - Blue",
            style = MaterialTheme.typography.caption.copy(
                color = Color.Blue
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SampleTextField(modifier: Modifier = Modifier) {
    Column(modifier) {
        var input1 by remember { mutableStateOf("Hello") }
        TextField(
            value = input1,
            onValueChange = { input1 = it },
            label = { Text("TextField") }
        )
        var input2 by remember { mutableStateOf("Hello") }

        OutlinedTextField(
            value = input2,
            onValueChange = { input2 = it },
            label = { Text("OutlinedTextField") }
        )
    }
}

@Composable
private fun SampleSwitch(modifier: Modifier = Modifier) {
    var checked by remember { mutableStateOf(false) }
    Switch(
        modifier = modifier,
        checked = checked,
        onCheckedChange = {
            checked = it
        }
    )
}

@Composable
private fun SampleRadioButton(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(false) }
    RadioButton(
        modifier = modifier,
        selected = selected,
        onClick = {
            selected = !selected
        }
    )
}

@Composable
private fun SampleCheckbox(modifier: Modifier = Modifier) {
    var checked by remember { mutableStateOf(false) }
    Checkbox(
        modifier = modifier,
        checked = checked,
        onCheckedChange = {
            checked = it
        }
    )
}

@Composable
@Preview(showBackground = true)
internal fun PreviewTextScreen() {
    TextScreen()
}
