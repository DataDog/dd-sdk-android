/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Checks if the [SemanticsNode] is a leaf node, meaning it has no children.
 */
internal fun SemanticsNode.isLeafNode(): Boolean {
    return this.children.isEmpty()
}

/**
 * Checks if the [SemanticsNode] is positioned at the origin (0, 0) in the root coordinate space.
 */
internal fun SemanticsNode.isPositionedAtOrigin(): Boolean {
    return this.positionInRoot == Offset.Zero
}

/**
 * Checks if the [SemanticsNode] is a placed image filling its Compose root, outside any Compose
 * scroll container.
 */
internal fun SemanticsNode.isImageFillingNonScrollableRoot(): Boolean {
    val isPlacedImage = config.getOrNull(SemanticsProperties.Role) == Role.Image &&
        layoutInfo.isAttached && layoutInfo.isPlaced
    val bounds = boundsInRoot
    if (!isPlacedImage || bounds.isEmpty) {
        return false
    }
    // RUM-9030's scrolling guard must also cover viewport-sized images in lazy lists.
    // Exempt root-filling images only outside Compose scroll containers.
    var root = this
    var hasScrollSemantics = root.isScrollContainer()
    while (!hasScrollSemantics) {
        root = root.parent ?: break
        hasScrollSemantics = root.isScrollContainer()
    }
    return !hasScrollSemantics && root !== this && bounds == root.boundsInRoot
}

private fun SemanticsNode.isScrollContainer(): Boolean {
    return config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
        config.contains(SemanticsProperties.HorizontalScrollAxisRange) ||
        config.contains(SemanticsActions.ScrollBy)
}
