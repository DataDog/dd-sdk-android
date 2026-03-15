/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.rum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.android.rum.GlobalRumMonitor

@Composable
internal fun FeatureFlagEvaluationScreen() {
    var flags by remember { mutableStateOf(listOf(FlagEntry("", false))) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Feature Flag Evaluations",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            flags.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = entry.name,
                        onValueChange = { newName ->
                            flags = flags.toMutableList().also { it[index] = entry.copy(name = newName) }
                        },
                        label = { Text("Flag name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = entry.value,
                        onCheckedChange = { newValue ->
                            flags = flags.toMutableList().also { it[index] = entry.copy(value = newValue) }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { flags = flags + FlagEntry("", false) }
            ) {
                Text("+ Add Flag")
            }

            Button(
                onClick = {
                    val flagMap = flags
                        .filter { it.name.isNotBlank() }
                        .associate { it.name to it.value }
                    if (flagMap.isNotEmpty()) {
                        GlobalRumMonitor.get().addFeatureFlagEvaluations(flagMap)
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

private data class FlagEntry(val name: String, val value: Boolean)

@Preview(showBackground = true)
@Composable
internal fun PreviewFeatureFlagEvaluationScreen() {
    MaterialTheme {
        FeatureFlagEvaluationScreen()
    }
}
