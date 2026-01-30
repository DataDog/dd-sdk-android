/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun <T> DropDownMenuView(
    headerText: String,
    items: List<T>,
    textFactory: @Composable (T) -> Unit,
    onClickAction: (T) -> Unit
) {
    Box {
        var expanded by remember { mutableStateOf(false) }

        Button(
            modifier = Modifier,
            onClick = { expanded = !expanded }
        ) {
            Text(headerText)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach {
                DropdownMenuItem(
                    text = { textFactory(it) },
                    onClick = {
                        onClickAction(it)
                        expanded = false
                    }
                )
            }
        }
    }
}
