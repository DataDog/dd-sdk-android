/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.webview.WebViewBrowserSnapshotHandler
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.LinkedList

internal class SnapshotProducer(
    private val treeViewTraversal: TreeViewTraversal,
    private val webViewBrowserSnapshotHandler: WebViewBrowserSnapshotHandler,
    private val optionSelectorDetector: OptionSelectorDetector =
        ComposedOptionSelectorDetector(listOf(DefaultOptionSelectorDetector()))
) {

    @MainThread
    fun produce(
        rootView: View,
        systemInformation: SystemInformation,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): Node? {
        return convertViewToNode(
            rootView,
            MappingContext(systemInformation),
            LinkedList(),
            recordedDataQueueRefs
        )
    }

    @Suppress("ComplexMethod", "ReturnCount")
    @MainThread
    private fun convertViewToNode(
        view: View,
        mappingContext: MappingContext,
        parents: LinkedList<MobileSegment.Wireframe>,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): Node? {
        val traversedTreeView = treeViewTraversal.traverse(view, mappingContext, recordedDataQueueRefs)
        val nextTraversalStrategy = traversedTreeView.nextActionStrategy
        val resolvedWireframes = traversedTreeView.mappedWireframes
        // in case the view is a WebView and the RUM native view was changed in the meantime,
        // we need to trigger a full snapshot of the web page through the browser sdk for the
        // player to have a starting point from which to apply the browser mutation when
        // required to play the new native RUM view
        if (view is WebView) {
            webViewBrowserSnapshotHandler.triggerFullSnapshotIfNeeded(view)
        }
        if (nextTraversalStrategy == TraversalStrategy.STOP_AND_DROP_NODE) {
            return null
        }
        if (nextTraversalStrategy == TraversalStrategy.STOP_AND_RETURN_NODE) {
            return Node(wireframes = resolvedWireframes, parents = parents)
        }

        val childNodes = LinkedList<Node>()
        if (view is ViewGroup &&
            view.childCount > 0 &&
            nextTraversalStrategy == TraversalStrategy.TRAVERSE_ALL_CHILDREN
        ) {
            val childMappingContext = resolveChildMappingContext(view, mappingContext)
            val parentsCopy = LinkedList(parents).apply { addAll(resolvedWireframes) }
            for (i in 0 until view.childCount) {
                val viewChild = view.getChildAt(i) ?: continue
                convertViewToNode(viewChild, childMappingContext, parentsCopy, recordedDataQueueRefs)?.let {
                    childNodes.add(it)
                }
            }
        }

        return Node(
            children = childNodes,
            wireframes = resolvedWireframes,
            parents = parents
        )
    }

    private fun resolveChildMappingContext(
        parent: ViewGroup,
        parentMappingContext: MappingContext
    ): MappingContext {
        return if (optionSelectorDetector.isOptionSelector(parent)) {
            parentMappingContext.copy(hasOptionSelectorParent = true)
        } else {
            parentMappingContext
        }
    }
}
