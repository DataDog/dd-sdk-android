/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalMaterialApi::class, ExperimentalTrackingApi::class)

package com.datadog.android.compose

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.compose.internal.TapActionTracker
import com.datadog.android.compose.internal.trackScroll
import com.datadog.android.compose.internal.trackSwipe
import com.datadog.android.rum.GlobalRumMonitor
import kotlin.coroutines.CoroutineContext

/**
 * Creates a proxy around click listener, which will report clicks to Datadog.
 *
 * @param targetName Name of the click target.
 * @param attributes Additional custom attributes to attach to the action. Attributes can be
 * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 * @param onClick Click listener.
 */
@ExperimentalTrackingApi
@Composable
fun trackClick(
    targetName: String,
    attributes: Map<String, Any?> = remember { emptyMap() },
    sdkCore: SdkCore = Datadog.getInstance(),
    onClick: () -> Unit
): () -> Unit {
    val onTapState = rememberUpdatedState(newValue = onClick)
    return remember(targetName, attributes) {
        TapActionTracker(targetName, attributes, onTapState, GlobalRumMonitor.get(sdkCore))
    }
}

/**
 * When [TrackInteractionEffect] enters composition, it will start tracking interactions (swipe or
 * scroll) emitted by the given interaction source in the composition's [CoroutineContext].
 * Tracking will be cancelled once effect leaves the composition.
 *
 * For tracking clicks check [trackClick].
 *
 * @param targetName Name of the tracking target.
 * @param interactionSource [InteractionSource] which hosts the flow of interactions happening.
 * @param interactionType Type of the interaction, either [InteractionType.Scroll]
 * or [InteractionType.Swipe]
 * @param attributes Additional custom attributes to attach to the action. Attributes can be
 * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@ExperimentalTrackingApi
@Composable
fun TrackInteractionEffect(
    targetName: String,
    interactionSource: InteractionSource,
    interactionType: InteractionType,
    attributes: Map<String, Any?> = emptyMap(),
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(interactionSource, interactionType, isRtl) {
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        when (interactionType) {
            is InteractionType.Swipe<*> -> trackSwipe(
                rumMonitor,
                targetName,
                interactionSource,
                interactionType,
                isRtl,
                attributes
            )

            is InteractionType.Scroll -> trackScroll(
                rumMonitor,
                targetName,
                interactionSource,
                interactionType,
                isRtl,
                attributes
            )
        }
    }
}
