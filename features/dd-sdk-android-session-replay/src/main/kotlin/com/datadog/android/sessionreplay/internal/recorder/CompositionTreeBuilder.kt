/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCopyFallbackMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueStatusCallback
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.NoOpInteropViewCallback
import com.datadog.android.sessionreplay.recorder.PixelCropCallback
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * Builds the composition tree for Session Replay's experimental pixel-copy recording pipeline,
 * gated by `pixelCopyCaptureEnabled` (see [SessionReplayRecorder]).
 *
 * This is a **wholly separate traversal** from the default pipeline ([TreeViewTraversal] /
 * [SnapshotProducer] and its full mapper chain), which this class never touches and which
 * behaves exactly as it does today when the flag is off. When the flag is on, this builder
 * replaces that traversal entirely for the views it walks:
 * - Every [TextView] is mapped via [textViewMapper], so text stays crisp/selectable rather than
 *   becoming a pixel capture.
 * - Every other leaf view is captured via [pixelCopyFallbackMapper].
 * - Every container (a [ViewGroup] with children) becomes its own [MobileSegment.CompositionLayer]
 *   so container-level rendering effects — currently just [View.getAlpha] — can be applied once
 *   to the whole group on the backend, instead of being lost the way it is in the default
 *   pipeline today (a container's alpha there only ever affects its own background wireframe,
 *   see [com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper] — it
 *   never reaches descendants).
 *
 * Mirrors iOS's `CompositionTreeBuilder` (see
 * https://github.com/DataDog/dd-sdk-ios/pull/3014): recursively walks the view hierarchy
 * directly, producing both the [MobileSegment.CompositionTree] and the flat
 * [MobileSegment.Wireframe] list in the same pass. As on iOS, children are referenced from their
 * parent layer by id (via [MobileSegment.CompositionLayerChild], tagged `wireframe` or `layer`)
 * rather than embedded inline, and completed non-root layers are collected into a flat
 * [MobileSegment.CompositionTree.layers] list alongside the separately-tracked root.
 */
internal class CompositionTreeBuilder(
    private val viewIdentifierResolver: ViewIdentifierResolver,
    private val viewBoundsResolver: ViewBoundsResolver,
    private val textViewMapper: TextViewMapper<TextView>,
    private val pixelCopyFallbackMapper: PixelCopyFallbackMapper,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val imageWireframeHelper: ImageWireframeHelper,
    private val pixelCropCallback: PixelCropCallback? = null,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal()
) {

    private val layers = mutableListOf<MobileSegment.CompositionLayer>()
    private val wireframes = mutableListOf<MobileSegment.Wireframe>()

    /**
     * Builds the composition tree and wireframe list rooted at [rootView] — mirrors
     * [SnapshotProducer.produce]'s signature, building its own [MappingContext] the same way
     * (this pipeline never handles Compose interop views, hence [NoOpInteropViewCallback]).
     * [Output.compositionTree] is null only if a layer could not be built for [rootView] itself
     * (its id could not be resolved) — every other node degrades gracefully (falls back to a
     * flat pixel capture of that subtree, see [childReferences]) instead of failing the build.
     */
    @UiThread
    fun build(
        rootView: View,
        systemInformation: SystemInformation,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        recordedDataQueueRefs: RecordedDataQueueRefs,
        internalLogger: InternalLogger
    ): Output {
        layers.clear()
        wireframes.clear()

        val mappingContext = MappingContext(
            systemInformation = systemInformation,
            imageWireframeHelper = imageWireframeHelper,
            textAndInputPrivacy = textAndInputPrivacy,
            imagePrivacy = imagePrivacy,
            touchPrivacyManager = touchPrivacyManager,
            interopViewCallback = NoOpInteropViewCallback(),
            pixelCropCallback = pixelCropCallback
        )
        val asyncJobStatusCallback = QueueStatusCallback(recordedDataQueueRefs)

        val rootLayer = buildLayer(rootView, mappingContext, asyncJobStatusCallback, internalLogger)
        val compositionTree = rootLayer?.let {
            MobileSegment.CompositionTree(root = it, layers = layers.toList().ifEmpty { null })
        }
        return Output(compositionTree, wireframes.toList())
    }

    /** Always builds a layer for [view] — callers decide whether a view warrants one. */
    private fun buildLayer(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): MobileSegment.CompositionLayer? {
        val id = viewIdentifierResolver.resolveChildUniqueIdentifier(view, COMPOSITION_LAYER_KEY_NAME)
            ?: return null
        val density = mappingContext.systemInformation.screenDensity
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)

        // CompositionLayer is the working representation while alpha is still a plain float —
        // it is translated into a CompositionLayerOpacityModifier below, the same way iOS's
        // modifiers() only emits one when opacity < 1.
        val layer = CompositionLayer(
            id = id,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            alpha = view.alpha,
            children = buildChildren(view, mappingContext, asyncJobStatusCallback, internalLogger)
        )

        return MobileSegment.CompositionLayer(
            id = layer.id,
            x = layer.x,
            y = layer.y,
            width = layer.width,
            height = layer.height,
            children = layer.children,
            modifiers = opacityModifiers(layer.alpha)
        )
    }

    /** [view]'s children, in rendering order — empty for anything that isn't a non-empty [ViewGroup]. */
    private fun buildChildren(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.CompositionLayerChild> {
        if (view !is ViewGroup || view.childCount == 0) return emptyList()

        val children = mutableListOf<MobileSegment.CompositionLayerChild>()
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) ?: continue
            if (viewUtilsInternal.isNotVisible(child) || viewUtilsInternal.isSystemNoise(child)) continue
            children.addAll(childReferences(child, mappingContext, asyncJobStatusCallback, internalLogger))
        }
        return children
    }

    /**
     * Resolves how [view] should be referenced from its parent's children list:
     * - A container (has its own children) — build its own layer and reference it by id.
     * - Otherwise — or if a layer could not be built for a container (missing/duplicate id) —
     *   map it as a leaf (one or more wireframes; a leaf mapper can produce more than one, e.g.
     *   a background plus content) and reference each by id. A container that falls back this
     *   way is captured as a single flat pixel snapshot of the whole subtree instead of a
     *   group — a degraded but still-correct result, never a dropped one.
     */
    private fun childReferences(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.CompositionLayerChild> {
        if (view is ViewGroup && view.childCount > 0) {
            val layer = buildLayer(view, mappingContext, asyncJobStatusCallback, internalLogger)
            if (layer != null) {
                layers.add(layer)
                return listOf(MobileSegment.CompositionLayerChild(id = layer.id, type = LAYER_CHILD_TYPE))
            }
        }

        val leafWireframes = mapLeafView(view, mappingContext, asyncJobStatusCallback, internalLogger)
        wireframes.addAll(leafWireframes)
        return leafWireframes.map {
            MobileSegment.CompositionLayerChild(id = it.id(), type = WIREFRAME_CHILD_TYPE)
        }
    }

    /** [TextView]s go through [textViewMapper] so text stays crisp; everything else is pixel-captured. */
    private fun mapLeafView(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        return if (view is TextView) {
            textViewMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        } else {
            pixelCopyFallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }
    }

    private fun opacityModifiers(alpha: Float): List<MobileSegment.CompositionLayerModifier>? {
        if (alpha >= 1f) return null
        return listOf(MobileSegment.CompositionLayerModifier.CompositionLayerOpacityModifier(value = alpha))
    }

    @Suppress("FunctionMinLength")
    private fun MobileSegment.Wireframe.id(): Long = when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> id
        is MobileSegment.Wireframe.TextWireframe -> id
        is MobileSegment.Wireframe.ImageWireframe -> id
        is MobileSegment.Wireframe.PlaceholderWireframe -> id
        is MobileSegment.Wireframe.WebviewWireframe -> id
    }

    internal data class Output(
        val compositionTree: MobileSegment.CompositionTree?,
        val wireframes: List<MobileSegment.Wireframe>
    )

    companion object {
        internal const val COMPOSITION_LAYER_KEY_NAME = "composition_layer"
        private val WIREFRAME_CHILD_TYPE = MobileSegment.Type.WIREFRAME
        private val LAYER_CHILD_TYPE = MobileSegment.Type.LAYER
    }
}
