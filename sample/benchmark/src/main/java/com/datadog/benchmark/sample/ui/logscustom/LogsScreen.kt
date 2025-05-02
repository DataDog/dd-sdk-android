/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logscustom

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.ui.LogPayloadSize

@Suppress("LongMethod")
@Composable
internal fun LogsScreen(
    modifier: Modifier,
    state: LogsScreenState,
    dispatch: (LogsScreenAction) -> Unit
) {
    Column(modifier = modifier.padding(32.dp)) {
        TextField(
            value = state.config.logMessage,
            onValueChange = { dispatch(LogsScreenAction.ChangeLogMessage(it)) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExpandedItemView(
            titleText = "Log level",
            items = ALL_LOG_LEVELS,
            headerText = state.config.logLevel.stringRepresentation(),
            itemTextFactory = { it.stringRepresentation() },
            onClick = { dispatch(LogsScreenAction.SelectLogLevel(it)) }
        )

        ExpandedItemView(
            titleText = "Payload Size",
            items = LogPayloadSize.values().toList(),
            headerText = state.config.payloadSize.toString(),
            itemTextFactory = { it.toString() },
            onClick = { dispatch(LogsScreenAction.SelectPayloadSize(it)) }
        )

        ValueChooserItemView(
            titleText = "Logs per batch",
            currentValue = state.config.logsPerBatch.toString(),
            increaseClick = { dispatch(LogsScreenAction.IncreaseLoggingSpeed) },
            decreaseClick = { dispatch(LogsScreenAction.DecreaseLoggingSpeed) }
        )

        ValueChooserItemView(
            titleText = "Interval (sec)",
            currentValue = state.config.loggingIntervalSeconds.toString(),
            increaseClick = { dispatch(LogsScreenAction.IncreaseLoggingInterval) },
            decreaseClick = { dispatch(LogsScreenAction.DecreaseLoggingInterval) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Repeat logging", modifier = Modifier.weight(1f))

            Switch(
                checked = state.config.repeatLogging,
                onCheckedChange = { isChecked ->
                    dispatch(LogsScreenAction.SetRepeatLogging(isChecked))
                }
            )
        }

        Button(onClick = { dispatch(LogsScreenAction.LogButtonClicked) }) {
            Text(
                text = if (state.loggingTask != null) {
                    "Stop logging"
                } else {
                    "Start logging"
                }
            )
        }
    }
}

@Composable
private fun <T> ExpandedItemView(
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
private fun ValueChooserItemView(
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

@Composable
private fun <T> DropDownMenuView(
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

private fun Int.stringRepresentation(): String {
    return when (this) {
        Log.ERROR -> "ERROR"
        Log.VERBOSE -> "VERBOSE"
        Log.ASSERT -> "ASSERT"
        Log.WARN -> "WARN"
        Log.INFO -> "INFO"
        Log.DEBUG -> "DEBUG"
        else -> "UNKNOWN"
    }
}

@Preview(showBackground = true)
@Composable
internal fun LogsScreenPreview() {
    LogsScreen(
        modifier = Modifier.fillMaxSize(),
        state = LogsScreenState.INITIAL,
        dispatch = {}
    )
}

private val ALL_LOG_LEVELS = listOf(
    Log.ERROR,
    Log.VERBOSE,
    Log.ASSERT,
    Log.WARN,
    Log.INFO,
    Log.DEBUG
)
