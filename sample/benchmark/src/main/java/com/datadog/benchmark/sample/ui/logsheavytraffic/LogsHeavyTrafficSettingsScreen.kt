/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logsheavytraffic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.ui.ALL_LOG_LEVELS
import com.datadog.benchmark.sample.ui.LogPayloadSize
import com.datadog.benchmark.sample.ui.common.ExpandedItemView
import com.datadog.benchmark.sample.ui.common.ValueChooserItemView
import com.datadog.benchmark.sample.ui.stringRepresentation

@Composable
internal fun LogsHeavyTrafficSettingsScreen(
    modifier: Modifier,
    config: LogsHeavyTrafficScreenState.LoggingConfig,
    dispatch: (LogsHeavyTrafficScreenAction) -> Unit
) {
    Column(modifier = modifier) {
        Button(onClick = { dispatch(LogsHeavyTrafficScreenAction.CloseSettings) }) {
            Text(text = "Close")
        }

        TextField(
            value = config.logMessage,
            onValueChange = { dispatch(LogsHeavyTrafficScreenAction.ChangeLogMessage(it)) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExpandedItemView(
            titleText = "Log level",
            items = ALL_LOG_LEVELS,
            headerText = config.logLevel.stringRepresentation(),
            itemTextFactory = { it.stringRepresentation() },
            onClick = { dispatch(LogsHeavyTrafficScreenAction.SelectLogLevel(it)) }
        )

        ExpandedItemView(
            titleText = "Payload Size",
            items = LogPayloadSize.values().toList(),
            headerText = config.payloadSize.toString(),
            itemTextFactory = { it.toString() },
            onClick = { dispatch(LogsHeavyTrafficScreenAction.SelectPayloadSize(it)) }
        )

        ValueChooserItemView(
            titleText = "Logs per image",
            currentValue = config.logsPerImage.toString(),
            increaseClick = { dispatch(LogsHeavyTrafficScreenAction.IncreaseLogsPerImage) },
            decreaseClick = { dispatch(LogsHeavyTrafficScreenAction.IncreaseLogsPerImage) }
        )
    }
}
