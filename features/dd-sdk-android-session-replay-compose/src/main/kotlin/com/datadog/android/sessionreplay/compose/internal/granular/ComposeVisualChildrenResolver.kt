/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import java.lang.reflect.Method

/** Keeps layout-only drawing and clipping nodes in paint order, alongside real semantics nodes. */
internal class ComposeVisualChildrenResolver {
    private val layoutClass = ComposeReflection.LayoutNodeClass
    private val sortedChildren = getter("getZSortedChildren")
    private val configuration = getter("getSemanticsConfiguration") ?: getter("getCollapsedSemantics")
    private val constructor = ComposeReflection.SemanticsNodeClass?.constructors?.firstOrNull {
        it.parameterTypes.contentEquals(
            arrayOf(
                Modifier.Node::class.java,
                Boolean::class.javaPrimitiveType,
                layoutClass,
                SemanticsConfiguration::class.java
            )
        )
    }

    private val available = sortedChildren != null && configuration != null && constructor != null

    /** Null retains the original semantics traversal when these optional runtime details are unavailable. */
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Checked optional reflection; reads are on the UI thread.
    fun children(node: SemanticsNode, onVisualNode: (SemanticsNode) -> Unit): List<SemanticsNode>? {
        if (!available || layoutClass?.isInstance(node.layoutInfo) != true) return null
        return try {
            val layouts = layoutChildren(node.layoutInfo) ?: return null
            val semanticChildren = node.children.associateBy { it.layoutInfo }
            val result = ArrayList<SemanticsNode>(layouts.size)
            for (layout in layouts) {
                if (!layout.isPlaced || !layout.coordinates.isAttached) continue
                val semantic = semanticChildren[layout]
                val child = semantic ?: visualNode(layout)?.also(onVisualNode) ?: return null
                result += child
            }
            result
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Checked array extent from Compose's own vector.
    private fun layoutChildren(layout: LayoutInfo): List<LayoutInfo>? {
        val vector = sortedChildren?.invoke(layout) ?: return null
        val content = vector.javaClass.getMethod("getContent").invoke(vector) as? Array<*> ?: return null
        val size = vector.javaClass.getMethod("getSize").invoke(vector) as? Int ?: return null
        if (size !in 0..content.size) return null
        return content.take(size).map { it as? LayoutInfo ?: return null }
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Constructor and node-chain types are checked.
    private fun visualNode(layout: LayoutInfo): SemanticsNode? {
        // Never synthesize over a semantics boundary: that could discard privacy overrides.
        if (configuration?.invoke(layout) != null) return null
        val chain = ComposeReflection.NodesFieldOfLayoutNode?.getSafe(layout) ?: return null
        val head = ComposeReflection.HeadFieldOfNodeChain?.getSafe(chain) as? Modifier.Node ?: return null
        return constructor?.newInstance(head, false, layout, SemanticsConfiguration()) as? SemanticsNode
    }

    private fun getter(name: String): Method? = layoutClass?.methods?.firstOrNull {
        it.parameterTypes.isEmpty() && it.name.substringBefore('$') == name
    }
}
