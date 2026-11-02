/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.internal.recorder.Node
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Stack

internal class NodeFlattener(private val wireframeUtils: WireframeUtils = WireframeUtils()) {

    internal fun flattenNode(root: Node): List<MobileSegment.Wireframe> {
        val stack = Stack<Node>()
        val list = ArrayList<MobileSegment.Wireframe>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            node.wireframes.toList()
                .map { wireframe ->
                    val clip = wireframeUtils.resolveWireframeClip(wireframe, node.parents)
                    val withClip = wireframe.copy(clip = clip)
                    node.heatmapIdentifier?.let { withClip.copyWithPermanentId(it.rawValue) } ?: withClip
                }
                .forEach { list.add(it) }

            for (i in node.children.count() - 1 downTo 0) {
                stack.push(node.children[i])
            }
        }
        return filterOutInvalidWireframes(list)
    }

    private fun filterOutInvalidWireframes(wireframes: List<MobileSegment.Wireframe>): List<MobileSegment.Wireframe> {
        // The local list is complete before filtering, so its tail views remain valid.
        return wireframes.filterIndexed { index, wireframe ->
            wireframeUtils.checkWireframeIsValid(wireframe) &&
                !wireframeUtils.checkWireframeIsCovered(wireframe, wireframes.subList(index + 1, wireframes.size))
        }
    }
}
