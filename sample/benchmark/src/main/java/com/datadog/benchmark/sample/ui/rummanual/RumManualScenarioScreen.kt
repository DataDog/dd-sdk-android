/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rummanual

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datadog.android.rum.RumActionType
import com.datadog.benchmark.sample.ui.common.ExpandedItemView
import com.datadog.benchmark.sample.ui.common.ValueChooserItemView

@Composable
internal fun RumManualScenarioScreen(
    modifier: Modifier,
    state: RumManualScenarioState,
    dispatch: (RumManualScenarioScreenAction) -> Unit
) {
    Column(modifier = modifier) {
        ExpandedItemView(
            titleText = "Select RUM event",
            items = RumManualScenarioState.RumEventType.values().toList(),
            headerText = state.config.eventType.name,
            itemTextFactory = { it.name },
            onClick = { dispatch(RumManualScenarioScreenAction.SelectEventType(it)) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Event configuration", style = MaterialTheme.typography.titleMedium)
        TextField(
            value = state.config.viewName,
            onValueChange = { dispatch(RumManualScenarioScreenAction.ChangeViewName(it)) }
        )

        when (state.config.eventType) {
            RumManualScenarioState.RumEventType.VIEW -> { }
            RumManualScenarioState.RumEventType.ACTION -> ActionEventConfiguration(state.config, dispatch)
            RumManualScenarioState.RumEventType.RESOURCE -> ResourceEventConfiguration(state.config, dispatch)
            RumManualScenarioState.RumEventType.ERROR -> ErrorEventConfiguration(state.config, dispatch)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Sending configuration", style = MaterialTheme.typography.titleMedium)

        ValueChooserItemView(
            titleText = "Events per batch",
            currentValue = state.config.eventsPerBatch.toString(),
            increaseClick = { dispatch(RumManualScenarioScreenAction.IncreaseEventsPerBatch) },
            decreaseClick = { dispatch(RumManualScenarioScreenAction.DecreaseEventsPerBatch) }
        )

        ValueChooserItemView(
            titleText = "Interval (sec):",
            currentValue = state.config.eventsIntervalSeconds.toString(),
            increaseClick = { dispatch(RumManualScenarioScreenAction.IncreaseEventsInterval) },
            decreaseClick = { dispatch(RumManualScenarioScreenAction.DecreaseEventsInterval) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Repeat sending events", modifier = Modifier.weight(1f))

            Switch(
                checked = state.config.isRepeating,
                onCheckedChange = { isChecked ->
                    dispatch(RumManualScenarioScreenAction.SetIsRepeating(isChecked))
                }
            )
        }
        
        if (state.sendingTask == null) {
            Button(onClick = { dispatch(RumManualScenarioScreenAction.SendClicked) }) {
                Text(text = "Send")
            }
        } else {
            Button(onClick = { dispatch(RumManualScenarioScreenAction.CancelClicked) }) {
                Text(text = "Cancel")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Events sent ${state.eventsSent}")
    }
}

@Composable
private fun ActionEventConfiguration(
    config: RumManualScenarioState.Config,
    dispatch: (RumManualScenarioScreenAction) -> Unit
) {
    ExpandedItemView(
        titleText = "Action type",
        items = RumActionType.values().toList(),
        headerText = config.actionType.name,
        itemTextFactory = { it.name },
        onClick = { dispatch(RumManualScenarioScreenAction.SelectActionType(it)) }
    )

    TextField(
        value = config.actionUrl,
        onValueChange = { dispatch(RumManualScenarioScreenAction.ChangeActionUrl(it)) }
    )
}

@Composable
private fun ResourceEventConfiguration(
    config: RumManualScenarioState.Config,
    dispatch: (RumManualScenarioScreenAction) -> Unit
) {
    TextField(
        value = config.resourceUrl,
        onValueChange = { dispatch(RumManualScenarioScreenAction.ChangeResourceUrl(it)) }
    )
}

@Composable
private fun ErrorEventConfiguration(
    config: RumManualScenarioState.Config,
    dispatch: (RumManualScenarioScreenAction) -> Unit
) {
    TextField(
        value = config.errorMessage,
        onValueChange = { dispatch(RumManualScenarioScreenAction.ChangeErrorMessage(it)) }
    )
}

@Preview(showBackground = true)
@Composable
internal fun RumManualScenarioScreenPreview() {
    RumManualScenarioScreen(
        modifier = Modifier.fillMaxSize(),
        state = RumManualScenarioState.INITIAL.copy(config = RumManualScenarioState.INITIAL.config.copy(eventType = RumManualScenarioState.RumEventType.ERROR)),
        dispatch = {}
    )
}
