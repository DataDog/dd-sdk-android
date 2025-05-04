/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logscustom

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.ui.ALL_LOG_LEVELS
import com.datadog.benchmark.sample.ui.LogPayloadSize
import com.datadog.benchmark.sample.ui.common.ExpandedItemView
import com.datadog.benchmark.sample.ui.common.ValueChooserItemView
import com.datadog.benchmark.sample.ui.stringRepresentation

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

@Preview(showBackground = true)
@Composable
internal fun LogsScreenPreview() {
    LogsScreen(
        modifier = Modifier.fillMaxSize(),
        state = LogsScreenState.INITIAL,
        dispatch = {}
    )
}

