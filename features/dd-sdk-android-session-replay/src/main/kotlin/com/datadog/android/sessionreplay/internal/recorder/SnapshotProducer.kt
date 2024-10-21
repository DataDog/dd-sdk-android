/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.PrivacyHelper
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
    private val optionSelectorDetector: OptionSelectorDetector,
    private val internalLogger: InternalLogger,
    private val privacyHelper: PrivacyHelper = PrivacyHelper(internalLogger)
) {

    @UiThread
    fun produce(
        rootView: View,
        systemInformation: SystemInformation,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        recordedDataQueueRefs: RecordedDataQueueRefs
    ): Node? {
        return convertViewToNode(
            rootView,
            MappingContext(
                systemInformation = systemInformation,
                imageWireframeHelper = imageWireframeHelper,
                textAndInputPrivacy = textAndInputPrivacy,
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
            val localMappingContext = resolvePrivacyOverrides(view, mappingContext)
            val traversedTreeView = treeViewTraversal.traverse(view, localMappingContext, recordedDataQueueRefs)
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
                val childMappingContext = resolveChildMappingContext(view, localMappingContext)
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

    private fun resolvePrivacyOverrides(view: View, mappingContext: MappingContext): MappingContext {
        val imagePrivacy =
            try {
                val privacy = view.getTag(R.id.datadog_image_privacy) as? String
                if (privacy == null) {
                    mappingContext.imagePrivacy
                } else {
                    ImagePrivacy.valueOf(privacy)
                }
            } catch (e: IllegalArgumentException) {
                privacyHelper.logInvalidPrivacyLevelError(e)
                mappingContext.imagePrivacy
            }

        val textAndInputPrivacy =
            try {
                val privacy = view.getTag(R.id.datadog_text_and_input_privacy) as? String
                if (privacy == null) {
                    mappingContext.textAndInputPrivacy
                } else {
                    TextAndInputPrivacy.valueOf(privacy)
                }
            } catch (e: IllegalArgumentException) {
                privacyHelper.logInvalidPrivacyLevelError(e)
                mappingContext.textAndInputPrivacy
            }

        return mappingContext.copy(
            imagePrivacy = imagePrivacy,
            textAndInputPrivacy = textAndInputPrivacy
        )
    }
}
