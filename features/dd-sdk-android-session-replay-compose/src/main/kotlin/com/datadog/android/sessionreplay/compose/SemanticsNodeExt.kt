/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode

/**
 * Checks if the [SemanticsNode] is a leaf node, meaning it has no children.
 */
fun SemanticsNode.isLeafNode(): Boolean {
    return this.children.isEmpty()
}

/**
 * Checks if the [SemanticsNode] is positioned at the origin (0, 0) in the root coordinate space.
 */
fun SemanticsNode.isPositionedAtOrigin(): Boolean {
    return this.positionInRoot == Offset.Zero
}
