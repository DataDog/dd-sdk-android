/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalMaterialApi::class)

package com.datadog.android.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.Text
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.datadog.android.compose.InteractionType
import com.datadog.android.compose.TrackInteractionEffect
import kotlin.math.roundToInt

@Suppress("MagicNumber")
@Preview
@Composable
internal fun InteractionSampleView() {
    val collection = remember { mutableStateListOf<Int>().apply { addAll(1..100) } }

    val scrollState = rememberLazyListState().apply {
        TrackInteractionEffect(
            targetName = "Items list",
            interactionSource = interactionSource,
            interactionType = InteractionType.Scroll(this, Orientation.Vertical)
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), state = scrollState) {
        items(collection, key = { it }) { index ->
            ItemRow(index = index, onDismissed = { collection.remove(index) })
        }
    }
}

@Suppress("MagicNumber")
@Composable
internal fun ItemRow(index: Int, onDismissed: () -> Unit) {
    val swipeableState = rememberSwipeableState(DragStates.VISIBLE)

    val startState = DragStates.VISIBLE
    val terminalState = DragStates.DISMISSED

    if (swipeableState.currentValue == terminalState) {
        onDismissed.invoke()
    } else {
        val screenSize = LocalConfiguration.current.screenWidthDp.dp
        val swipeAnchors = mapOf(
            0f to startState,
            with(LocalDensity.current) { screenSize.toPx() } to terminalState
        )
        val swipeOrientation = Orientation.Horizontal

        val interactionSource = remember {
            MutableInteractionSource()
        }.apply {
            TrackInteractionEffect(
                targetName = "Item row",
                interactionSource = this,
                interactionType = InteractionType.Swipe(
                    swipeableState,
                    orientation = swipeOrientation
                ),
                attributes = mapOf("item" to index)
            )
        }

        Box(
            modifier = Modifier
                .swipeable(
                    interactionSource = interactionSource,
                    state = swipeableState,
                    orientation = swipeOrientation,
                    anchors = swipeAnchors,
                    thresholds = { _, _ -> FractionalThreshold(0.5f) }
                )
                .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
        ) {
            Text(
                text = "Item: $index",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Cyan)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

private enum class DragStates {
    VISIBLE,
    DISMISSED
}
