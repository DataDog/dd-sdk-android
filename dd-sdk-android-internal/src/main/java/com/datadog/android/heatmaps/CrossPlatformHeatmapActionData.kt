/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.heatmaps

/**
 * Heatmap data for a RUM action, used by cross-platform SDKs to correlate a tap action with
 * a specific UI element.
 *
 * @property elementPath the path segments from the root component down to the tapped element
 *   (e.g. `["root", "container", "submitButton"]`), using the same naming convention as the
 *   cross-platform Session Replay layer so that the resulting identifier matches the wireframe.
 * @property viewUrl the RUM view URL returned by `_RumInternalProxy.getCurrentViewUrl()` at tap time.
 * @property positionX the x-coordinate of the tap relative to the target element, in dp.
 * @property positionY the y-coordinate of the tap relative to the target element, in dp.
 * @property targetWidth the width of the tapped element, in dp, or null if unavailable.
 * @property targetHeight the height of the tapped element, in dp, or null if unavailable.
 */
data class CrossPlatformHeatmapActionData(
    val elementPath: List<String>,
    val viewUrl: String,
    val positionX: Long,
    val positionY: Long,
    val targetWidth: Long?,
    val targetHeight: Long?
)
