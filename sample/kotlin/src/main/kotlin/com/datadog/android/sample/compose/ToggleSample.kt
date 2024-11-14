/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun ToggleSample() {
    Column(modifier = Modifier.padding(16.dp)) {
        SampleSwitch()
        SampleRadioButton()
        SampleCheckbox()
    }
}

@Composable
private fun SampleSwitch(modifier: Modifier = Modifier) {
    var checked by remember { mutableStateOf(false) }
    SampleButtonContainer(title = "Switch Button") {
        Switch(
            modifier = modifier,
            checked = checked,
            onCheckedChange = {
                checked = it
            }
        )
    }
}

@Composable
private fun SampleRadioButton(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(false) }
    SampleButtonContainer(title = "Radio Button") {
        RadioButton(
            modifier = modifier,
            selected = selected,
            onClick = {
                selected = !selected
            }
        )
    }
}

@Composable
private fun SampleCheckbox(modifier: Modifier = Modifier) {
    var checked by remember { mutableStateOf(false) }
    SampleButtonContainer(title = "Checkbox") {
        Checkbox(
            modifier = modifier,
            checked = checked,
            onCheckedChange = {
                checked = it
            }
        )
    }
}

@Composable
private fun SampleButtonContainer(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color = Color.Gray),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content.invoke()
        Text(
            title,
            color = Color.Black,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun PreviewToggleSample() {
    ToggleSample()
}
