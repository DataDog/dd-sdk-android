/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
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
import com.datadog.android.compose.ExperimentalTrackingApi
import com.datadog.android.compose.InteractionType
import com.datadog.android.compose.TrackInteractionEffect
import kotlin.math.roundToInt

@Suppress("MagicNumber")
@OptIn(ExperimentalTrackingApi::class)
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
@OptIn(ExperimentalTrackingApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ItemRow(index: Int, onDismissed: () -> Unit) {
    val anchoredDraggableState = rememberAnchoredDraggableState()

    if (anchoredDraggableState.currentValue == DragStates.DISMISSED) {
        onDismissed.invoke()
    } else {
        val swipeOrientation = Orientation.Horizontal
        val interactionSource = remember { MutableInteractionSource() }
            .apply {
                TrackInteractionEffect(
                    targetName = "Item row",
                    interactionSource = this,
                    interactionType = InteractionType.Swipe(
                        anchoredDraggableState,
                        orientation = swipeOrientation
                    ),
                    attributes = mapOf("item" to index)
                )
            }

        Box(
            modifier = Modifier
                .anchoredDraggable(
                    interactionSource = interactionSource,
                    state = anchoredDraggableState,
                    orientation = swipeOrientation,
                    startDragImmediately = false
                )
                .offset {
                    IntOffset(
                        x = anchoredDraggableState
                            .requireOffset()
                            .roundToInt(),
                        y = 0
                    )
                }
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun rememberAnchoredDraggableState(): AnchoredDraggableState<DragStates> {
    val density = LocalDensity.current
    val screenSize = LocalConfiguration.current.screenWidthDp.dp
    val decayAnimationSpec = rememberSplineBasedDecay<Float>()
    return remember {
        AnchoredDraggableState(
            initialValue = DragStates.VISIBLE,
            anchors = DraggableAnchors {
                DragStates.VISIBLE at 0f
                DragStates.DISMISSED at with(density) { screenSize.toPx() }
            },
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 500.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = decayAnimationSpec
        )
    }
}

private enum class DragStates {
    VISIBLE,
    DISMISSED
}
