/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
internal fun <T> ExpandedItemView(
    titleText: String,
    items: List<T>,
    headerText: String,
    itemTextFactory: (T) -> String,
    onClick: (T) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = titleText, modifier = Modifier.weight(1f))

        DropDownMenuView(
            headerText = headerText,
            items = items,
            textFactory = { Text(text = itemTextFactory(it)) },
            onClickAction = { onClick(it) }
        )
    }
}

@Composable
internal fun <T> DropDownMenuView(
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

@Composable
internal fun ValueChooserItemView(
    titleText: String,
    currentValue: String,
    increaseClick: () -> Unit,
    decreaseClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = titleText, modifier = Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentValue)

            Spacer(modifier = Modifier.width(32.dp))

            Button(
                onClick = decreaseClick
            ) {
                Text(text = "-")
            }
            Button(
                onClick = increaseClick
            ) {
                Text(text = "+")
            }
        }
    }
}

