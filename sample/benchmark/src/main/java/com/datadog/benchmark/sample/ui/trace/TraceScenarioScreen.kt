/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.trace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.benchmark.sample.ui.common.ValueChooserItemView

@Composable
internal fun TraceScenarioScreen(
    modifier: Modifier,
    state: TraceScenarioScreenState,
    dispatch: (TraceScenarioScreenAction) -> Unit
) {
    Column(modifier = modifier.padding(8.dp)) {
        TextField(
            value = state.config.spanOperation,
            onValueChange = { dispatch(TraceScenarioScreenAction.ChangeSpanOperation(it)) }
        )
        TextField(
            value = state.config.spanResource,
            onValueChange = { dispatch(TraceScenarioScreenAction.ChangeSpanResource(it)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Is Error", modifier = Modifier.weight(1f))

            Switch(
                checked = state.config.isError,
                onCheckedChange = { isChecked ->
                    dispatch(TraceScenarioScreenAction.SetIsError(isChecked))
                }
            )
        }

        ValueChooserItemView(
            titleText = "Children count:",
            currentValue = state.config.childrenCount.toString(),
            increaseClick = { dispatch(TraceScenarioScreenAction.IncreaseChildrenCount) },
            decreaseClick = { dispatch(TraceScenarioScreenAction.DecreaseChildrenCount) }
        )

        ValueChooserItemView(
            titleText = "Depth:",
            currentValue = state.config.depth.toString(),
            increaseClick = { dispatch(TraceScenarioScreenAction.IncreaseDepth) },
            decreaseClick = { dispatch(TraceScenarioScreenAction.DecreaseDepth) }
        )

        ValueChooserItemView(
            titleText = "Child delay (ms):",
            currentValue = state.config.childDelayMillis.toString(),
            increaseClick = { dispatch(TraceScenarioScreenAction.IncreaseChildDelay) },
            decreaseClick = { dispatch(TraceScenarioScreenAction.DecreaseChildDelay) }
        )

        Row {
            Button(onClick = { dispatch(TraceScenarioScreenAction.SendButtonClicked) }) {
                Text(text = "Send")
            }

            if (state.tracingTasksMap.isNotEmpty()) {
                Button(onClick = { dispatch(TraceScenarioScreenAction.CancelButtonClicked) }) {
                    Text(text = "Cancel")
                }
            }
        }

        Text(text = "Traces sent: ${state.tracesSent}")

        if (state.tracingTasksMap.isNotEmpty()) {
            Text(text = "Number of running tasks: ${state.tracingTasksMap.size}")
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun TraceScenarioScreenPreview() {
    TraceScenarioScreen(
        modifier = Modifier.fillMaxSize(),
        state = TraceScenarioScreenState.INITIAL,
        dispatch = { }
    )
}
