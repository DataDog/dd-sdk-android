/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import kotlin.math.ceil
import kotlin.math.floor

/** Capture and live refresh share the same geometry, budget checks, and HIDE precedence. */
internal class GranularComposeTouchPrivacy(
    private val semanticsUtils: SemanticsUtils,
    private val windowOffset: ComposeWindowOffset,
    private val shouldContinue: () -> Boolean
) {
    val areas = mutableMapOf<Rect, TouchPrivacy>()

    fun captureNode(node: SemanticsNode) {
        semanticsUtils.getTouchPrivacyOverride(node)?.let { privacy ->
            val bounds = node.boundsInRoot
            val area = Rect(
                floor(bounds.left.toDouble()).toInt() + windowOffset.xPx,
                floor(bounds.top.toDouble()).toInt() + windowOffset.yPx,
                ceil(bounds.right.toDouble()).toInt() + windowOffset.xPx,
                ceil(bounds.bottom.toDouble()).toInt() + windowOffset.yPx
            )
            if (areas[area] != TouchPrivacy.HIDE) areas[area] = privacy
        }
    }

    @Suppress("ReturnCount") // Expiry discards the entire tentative map.
    fun captureSubtree(node: SemanticsNode): Boolean {
        if (!shouldContinue()) return false
        if (node.hasAvailablePosition()) {
            captureNode(node)
            for (child in node.children) {
                if (!captureSubtree(child)) return false
            }
        }
        return shouldContinue()
    }
}

/** A valid leaf may be at (0,0); placement state distinguishes it from unavailable geometry. */
internal fun SemanticsNode.hasAvailablePosition(): Boolean = layoutInfo.isAttached && layoutInfo.isPlaced
