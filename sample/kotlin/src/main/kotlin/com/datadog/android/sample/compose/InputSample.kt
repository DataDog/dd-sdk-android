/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun InputSample() {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        var input1 by remember { mutableStateOf("Input of Text Field") }
        TextField(
            value = input1,
            onValueChange = { input1 = it },
            label = { Text("TextField") }
        )

        var input2 by remember { mutableStateOf("Input of Outlined Text Field") }
        OutlinedTextField(
            value = input2,
            textStyle = TextStyle(
                color = Color.Red
            ),
            onValueChange = { input2 = it },
            label = { Text("OutlinedTextField") }
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PreviewInputSample() {
    InputSample()
}
