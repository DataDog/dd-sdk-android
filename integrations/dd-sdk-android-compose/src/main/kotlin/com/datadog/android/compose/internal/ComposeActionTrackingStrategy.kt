/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose.internal

import android.content.Context
import android.view.View
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.compose.internal.utils.LayoutNodeUtils
import com.datadog.android.compose.internal.utils.LayoutNodeUtils.TargetNode
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.Node
import com.datadog.android.rum.tracking.ViewTarget
import java.util.LinkedList
import java.util.Queue

/**
 * Implementation of [ActionTrackingStrategy] to track actions in Jetpack Compose.
 */
internal class ComposeActionTrackingStrategy(
    private val layoutNodeUtils: LayoutNodeUtils = LayoutNodeUtils()
) : ActionTrackingStrategy {

    private var sdkCore: SdkCore? = null

    override fun register(sdkCore: SdkCore, context: Context) {
        this.sdkCore = sdkCore
    }

    override fun unregister(context: Context?) {
        this.sdkCore = null
    }

    override fun findTargetForTap(view: View, x: Float, y: Float): ViewTarget? {
        return (view as? Owner)?.let {
            iterateLayoutNodes(view as Owner, x, y, false)
        }
    }

    override fun findTargetForScroll(view: View, x: Float, y: Float): ViewTarget? {
        return (view as? Owner)?.let {
            iterateLayoutNodes(view as Owner, x, y, true)
        }
    }

    @Suppress("LoopWithTooManyJumpStatements", "NestedBlockDepth")
    private fun iterateLayoutNodes(
        owner: Owner,
        x: Float,
        y: Float,
        isScrollEvent: Boolean
    ): ViewTarget? {
        val queue: Queue<LayoutNode> = LinkedList()
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // Any exception will be caught.
            queue.add(owner.root)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // All exceptions should be caught and return in case of crash.
            logAddQueueException(e)
            // The iteration of the layout nodes tree has failed, return null.
            return null
        }

        var currentNode: TargetNode? = null
        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue
            if (node.isPlaced && hitTest(node, x, y)) {
                layoutNodeUtils.resolveLayoutNode(node)?.let { target ->
                    if (target.isScrollable && isScrollEvent) {
                        currentNode = target
                    }
                    if (target.isClickable && !isScrollEvent) {
                        currentNode = target
                    }
                }
            }
            queue.addAll(node.zSortedChildren.asMutableList())
        }
        return currentNode?.let {
            ViewTarget(node = Node(name = it.tag, customAttributes = it.customAttributes))
        }
    }

    private fun hitTest(layoutNode: LayoutNode, x: Float, y: Float): Boolean {
        return layoutNodeUtils.getLayoutNodeBoundsInWindow(layoutNode)?.let { bounds ->
            x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
        } ?: false
    }

    private fun logAddQueueException(e: Exception) {
        (sdkCore as? FeatureSdkCore)?.internalLogger?.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { "Failed to add layout node into the processing queue." },
            throwable = e
        )
    }
}
