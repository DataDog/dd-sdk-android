/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import java.util.IdentityHashMap

/** Resolves real semantics once per capture, instead of rediscovering descendants at every visual wrapper. */
internal object ComposeSemanticIndex {
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned stack and map, bounded by the caller's deadline.
    fun build(root: SemanticsNode, shouldContinue: () -> Boolean): Map<LayoutInfo, SemanticsNode>? {
        val nodes = IdentityHashMap<LayoutInfo, SemanticsNode>()
        val pending = ArrayDeque<SemanticsNode>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            if (!shouldContinue()) return null
            val node = pending.removeLast()
            // Synthetic accessibility children can share their real parent's LayoutInfo. Keep
            // the actual boundary visited first, rather than substituting a fake node's config.
            if (nodes.containsKey(node.layoutInfo)) continue
            nodes[node.layoutInfo] = node
            val children = node.children
            for (index in children.indices.reversed()) pending.addLast(children[index])
        }
        return nodes
    }
}
