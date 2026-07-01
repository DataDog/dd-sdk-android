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
import com.datadog.android.internal.utils.isValidTapTarget
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.callback.DefaultInteropViewCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.recorder.PixelCropCallback
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import java.util.LinkedList

internal class SnapshotProducer(
    private val imageWireframeHelper: ImageWireframeHelper,
    private val treeViewTraversal: TreeViewTraversal,
    private val optionSelectorDetector: OptionSelectorDetector,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val internalLogger: InternalLogger,
    private val heatmapResolver: HeatmapIdentifierResolver? = null,
    private val pixelCropCallback: PixelCropCallback? = null
) {

    @UiThread
    fun produce(
        rootView: View,
        systemInformation: SystemInformation,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        recordedDataQueueRefs: RecordedDataQueueRefs,
        activeRumViewUrl: String? = null
    ): Node? {
        val heatmapContext = if (activeRumViewUrl != null) heatmapResolver?.beginTraversal(activeRumViewUrl) else null

        val rootNode = convertViewToNode(
            view = rootView,
            mappingContext = MappingContext(
                systemInformation = systemInformation,
                imageWireframeHelper = imageWireframeHelper,
                textAndInputPrivacy = textAndInputPrivacy,
                imagePrivacy = imagePrivacy,
                touchPrivacyManager = touchPrivacyManager,
                interopViewCallback = DefaultInteropViewCallback(
                    treeViewTraversal,
                    recordedDataQueueRefs
                ),
                pixelCropCallback = pixelCropCallback
            ),
            parents = LinkedList(),
            recordedDataQueueRefs = recordedDataQueueRefs,
            nodePath = emptyList(),
            typeIndex = 0,
            heatmapContext = heatmapContext
        )

        heatmapContext?.publish()

        return rootNode
    }

    @Suppress("ComplexMethod", "ReturnCount", "LongParameterList")
    @UiThread
    private fun convertViewToNode(
        view: View,
        mappingContext: MappingContext,
        parents: LinkedList<MobileSegment.Wireframe>,
        recordedDataQueueRefs: RecordedDataQueueRefs,
        nodePath: List<String>,
        typeIndex: Int,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
    ): Node? {
        return withinSRBenchmarkSpan(view::class.java.simpleName, view is ViewGroup) {
            val localMappingContext = resolvePrivacyOverrides(view, mappingContext)
            val traversedTreeView = treeViewTraversal.traverse(view, localMappingContext, recordedDataQueueRefs)
            val nextTraversalStrategy = traversedTreeView.nextActionStrategy
            val resolvedWireframes = traversedTreeView.mappedWireframes
            if (nextTraversalStrategy == TraversalStrategy.STOP_AND_DROP_NODE) {
                return null
            }

            val identity = resolveHeatmapIdentity(view, nodePath, typeIndex, nextTraversalStrategy, heatmapContext)
            val viewPath = identity?.viewPath ?: nodePath
            val heatmapIdentifier = identity?.identifier

            if (nextTraversalStrategy == TraversalStrategy.STOP_AND_RETURN_NODE) {
                return Node(
                    wireframes = resolvedWireframes,
                    parents = parents,
                    heatmapIdentifier = heatmapIdentifier
                )
            }

            val childNodes = LinkedList<Node>()
            if (view is ViewGroup &&
                view.childCount > 0 &&
                nextTraversalStrategy == TraversalStrategy.TRAVERSE_ALL_CHILDREN
            ) {
                val childMappingContext = resolveChildMappingContext(view, localMappingContext)
                val parentsCopy = LinkedList(parents).apply { addAll(resolvedWireframes) }
                val childTypeIndices = heatmapContext?.computeChildTypeIndices(view)
                for (i in 0 until view.childCount) {
                    val viewChild = view.getChildAt(i) ?: continue

                    @Suppress("UnsafeThirdPartyFunctionCall") // i is always in-bounds: array size == view.childCount
                    val childTypeIndex = childTypeIndices?.get(i) ?: 0
                    convertViewToNode(
                        view = viewChild,
                        mappingContext = childMappingContext,
                        parents = parentsCopy,
                        recordedDataQueueRefs = recordedDataQueueRefs,
                        nodePath = viewPath,
                        typeIndex = childTypeIndex,
                        heatmapContext = heatmapContext
                    )?.let {
                        childNodes.add(it)
                    }
                }
            }
            Node(
                children = childNodes,
                wireframes = resolvedWireframes,
                parents = parents,
                heatmapIdentifier = heatmapIdentifier
            )
        }
    }

    @UiThread
    private fun resolveHeatmapIdentity(
        view: View,
        nodePath: List<String>,
        typeIndex: Int,
        nextTraversalStrategy: TraversalStrategy,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
    ): HeatmapIdentifierResolver.HeatmapIdentity? {
        return heatmapContext?.let { ctx ->
            val pathNeededForChildren = view is ViewGroup &&
                view.childCount > 0 &&
                nextTraversalStrategy == TraversalStrategy.TRAVERSE_ALL_CHILDREN
            if (pathNeededForChildren || view.isValidTapTarget()) {
                ctx.resolveIdentity(view, nodePath, typeIndex)
            } else {
                null
            }
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
                logInvalidPrivacyLevelError(e)
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
                logInvalidPrivacyLevelError(e)
                mappingContext.textAndInputPrivacy
            }

        return mappingContext.copy(
            imagePrivacy = imagePrivacy,
            textAndInputPrivacy = textAndInputPrivacy
        )
    }

    private fun logInvalidPrivacyLevelError(e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            { INVALID_PRIVACY_LEVEL_ERROR },
            e
        )
    }

    internal companion object {
        internal const val INVALID_PRIVACY_LEVEL_ERROR = "Invalid privacy level"
    }
}
