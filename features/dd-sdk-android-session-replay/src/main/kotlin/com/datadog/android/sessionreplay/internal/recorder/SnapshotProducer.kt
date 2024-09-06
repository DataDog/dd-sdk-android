/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import java.util.LinkedList

internal class SnapshotProducer(
    private val imageWireframeHelper: ImageWireframeHelper,
    private val treeViewTraversal: TreeViewTraversal,
    private val optionSelectorDetector: OptionSelectorDetector
) {

    @UiThread
    fun produce(
        rootView: View,
        systemInformation: SystemInformation,
        privacy: SessionReplayPrivacy,
        imagePrivacy: ImagePrivacy,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): Node? {
        return convertViewToNode(
            rootView,
            MappingContext(
                systemInformation = systemInformation,
                imageWireframeHelper = imageWireframeHelper,
                privacy = privacy,
                imagePrivacy = imagePrivacy
            ),
            LinkedList(),
            recordedDataQueueRefs
        )
    }

    @Suppress("ComplexMethod", "ReturnCount")
    @UiThread
    private fun convertViewToNode(
        view: View,
        mappingContext: MappingContext,
        parents: LinkedList<MobileSegment.Wireframe>,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): Node? {
        return withinSRBenchmarkSpan(view::class.java.simpleName, view is ViewGroup) {
            val traversedTreeView = treeViewTraversal.traverse(view, mappingContext, recordedDataQueueRefs)
            val nextTraversalStrategy = traversedTreeView.nextActionStrategy
            val resolvedWireframes = traversedTreeView.mappedWireframes
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
            Node(
                children = childNodes,
                wireframes = resolvedWireframes,
                parents = parents
            )
        }
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
