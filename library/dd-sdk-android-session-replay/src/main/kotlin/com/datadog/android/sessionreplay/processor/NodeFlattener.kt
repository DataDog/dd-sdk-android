/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.processor

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.Node
import java.util.LinkedList
import java.util.Stack

internal class NodeFlattener {

    internal fun flattenNode(root: Node): List<MobileSegment.Wireframe> {
        val stack = Stack<Node>()
        val list = LinkedList<MobileSegment.Wireframe>()
        stack.push(root)
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            list.addAll(node.wireframes)
            for (i in node.children.count() - 1 downTo 0) {
                stack.push(node.children[i])
            }
        }
        return filterOutInvalidWireframes(list)
    }

    private fun filterOutInvalidWireframes(wireframes: List<MobileSegment.Wireframe>):
        List<MobileSegment.Wireframe> {
        return wireframes.filterIndexed { index, wireframe ->
            isValidWireframe(wireframe, wireframes.drop(index + 1))
        }
    }

    private fun isValidWireframe(
        wireframe: MobileSegment.Wireframe,
        topWireframes: List<MobileSegment.Wireframe>
    ): Boolean {
        val wireframeBounds = wireframe.bounds()
        if (wireframeBounds.width <= 0 || wireframeBounds.height <= 0) {
            return false
        }
        topWireframes.forEach {
            if (it.bounds().isCovering(wireframeBounds)) {
                return false
            }
        }
        return true
    }

    private fun Bounds.isCovering(other: Bounds): Boolean {
        return this.x <= other.x &&
            x + width >= other.x + other.width &&
            y <= other.y &&
            y + height >= other.y + other.height
    }
}
