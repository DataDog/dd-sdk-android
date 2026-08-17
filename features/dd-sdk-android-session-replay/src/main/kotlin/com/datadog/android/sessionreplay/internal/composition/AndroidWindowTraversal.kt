/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedHiddenViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import kotlin.math.max

internal sealed interface WindowWalkResult {
    data class Present(
        val rootLayer: CapturedLayer,
        val layers: List<CapturedLayer>,
        val wireframes: List<CapturedWireframe>
    ) : WindowWalkResult

    /** The window (e.g. not shown, or on a secondary display) contributes nothing - skip it. */
    object Filtered : WindowWalkResult

    /** The deadline expired mid-walk - the whole capture must be discarded, not just this window. */
    object Aborted : WindowWalkResult
}

/**
 * Walks one window's native View hierarchy into a [CapturedLayer] tree, combining legacy
 * `TreeViewTraversal` (per-view decisions) and `SnapshotProducer` (recursion) into a single pass,
 * with clip computed inline via a threaded ancestor-bounds stack instead of a separate flatten step.
 *
 * Every visited [View] becomes exactly one [CapturedLayer] (kind [CapturedLayerKind.NATIVE_VIEW],
 * or [CapturedLayerKind.WINDOW_ROOT] for the window's own root). Its identity is always created via
 * [CapturedIdentityFactory.view] against the *window's* identity - never the view's real structural
 * parent - per the flat-namespacing rule [CapturedIdentityFactory] enforces; real nesting is carried
 * purely by [CapturedLayer.children].
 */
internal class AndroidWindowTraversal(
    private val mapperRegistry: CapturedViewMapperRegistry,
    private val hiddenViewMapper: CapturedViewMapper<View> = CapturedHiddenViewMapper(),
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal(),
    private val composeHostCallback: CapturedInteropViewCallback? = null,
    private val viewsPerCheckpoint: Int = VIEWS_PER_CHECKPOINT
) {

    fun traverseWindow(
        windowRoot: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext
    ): WindowWalkResult {
        if (!context.shouldContinue()) return WindowWalkResult.Aborted
        val state = TraversalState(screenDensity = windowRoot.resources.displayMetrics.density)
        return when (
            val result = visitView(
                view = windowRoot,
                ownIdentity = windowIdentity,
                ownKind = CapturedLayerKind.WINDOW_ROOT,
                windowIdentity = windowIdentity,
                identityFactory = identityFactory,
                context = context,
                state = state
            )
        ) {
            is LayerWalkResult.Present ->
                WindowWalkResult.Present(result.layer, state.layers, state.wireframes)
            LayerWalkResult.Filtered -> WindowWalkResult.Filtered
            LayerWalkResult.Aborted -> WindowWalkResult.Aborted
        }
    }

    @Suppress("ReturnCount")
    private fun visitView(
        view: View,
        ownIdentity: CapturedIdentity,
        ownKind: CapturedLayerKind,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        state: TraversalState
    ): LayerWalkResult {
        state.viewsVisited++
        if (state.viewsVisited % viewsPerCheckpoint == 0 && !context.shouldContinue()) {
            return LayerWalkResult.Aborted
        }
        if (viewUtilsInternal.isNotVisible(view) ||
            viewUtilsInternal.isSystemNoise(view) ||
            viewUtilsInternal.isOnSecondaryDisplay(view)
        ) {
            return LayerWalkResult.Filtered
        }

        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, state.screenDensity).toCaptured()
        val mappingContext = CapturedMappingContext(identityFactory, ownIdentity, state.screenDensity)
        val isHidden = view.getTag(R.id.datadog_hidden) == true

        val children = mutableListOf<CapturedChild>()
        val interopResult = composeHostCallback?.takeIf { isComposeHost(view) }?.map(view, mappingContext)
        // A compose host's own decomposition can cost far more than one view's worth of work, yet
        // still only counts as +1 against viewsPerCheckpoint. Re-poll the deadline immediately after
        // it returns instead of waiting for the next checkpoint tick, so a handful of expensive hosts
        // can't hide behind a coarse, uniform per-node count.
        if (interopResult != null && !context.shouldContinue()) {
            return LayerWalkResult.Aborted
        }
        val mapped = interopResult
            ?: (if (isHidden) hiddenViewMapper else mapperRegistry.resolve(view)).map(view, mappingContext)
        addWireframes(mapped, state.ancestorBounds, children, state)

        if (!isHidden && interopResult == null && view is ViewGroup) {
            val aborted = visitChildren(view, windowIdentity, identityFactory, context, state, bounds, children)
            if (aborted) return LayerWalkResult.Aborted
        }

        val layer = CapturedLayer(identity = ownIdentity, kind = ownKind, bounds = bounds, children = children)
        state.layers.add(layer)
        return LayerWalkResult.Present(layer)
    }

    /**
     * A Compose host's interior is Compose's own node tree, not further Android child Views - its
     * content is fully described by whatever `composeHostCallback` returned in [visitView], which
     * is why that case never reaches here.
     */
    @Suppress("LongParameterList")
    private fun visitChildren(
        viewGroup: ViewGroup,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        state: TraversalState,
        ownBounds: CapturedBounds,
        children: MutableList<CapturedChild>
    ): Boolean {
        state.ancestorBounds.add(ownBounds)
        try {
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i) ?: continue
                val childIdentity = identityFactory.view(
                    windowIdentity,
                    viewIdentifierResolver.resolveViewId(child).toString()
                )
                when (
                    val childResult = visitView(
                        view = child,
                        ownIdentity = childIdentity,
                        ownKind = CapturedLayerKind.NATIVE_VIEW,
                        windowIdentity = windowIdentity,
                        identityFactory = identityFactory,
                        context = context,
                        state = state
                    )
                ) {
                    is LayerWalkResult.Present -> children.add(CapturedChild.Layer(childResult.layer.identity))
                    LayerWalkResult.Filtered -> Unit
                    LayerWalkResult.Aborted -> return true
                }
            }
        } finally {
            state.ancestorBounds.removeAt(state.ancestorBounds.lastIndex)
        }
        return false
    }

    private fun addWireframes(
        result: CapturedViewMapperResult,
        ancestorBounds: List<CapturedBounds>,
        children: MutableList<CapturedChild>,
        state: TraversalState
    ) {
        if (result !is CapturedViewMapperResult.Wireframes) return
        for (wireframe in result.wireframes) {
            val clipped = wireframe.withClip(computeClip(wireframe.bounds, ancestorBounds))
            state.wireframes.add(clipped)
            children.add(CapturedChild.Wireframe(clipped.identity))
        }
    }

    private fun computeClip(bounds: CapturedBounds, ancestorBounds: List<CapturedBounds>): CapturedClip? {
        var clipTop = 0L
        var clipBottom = 0L
        var clipLeft = 0L
        var clipRight = 0L
        val bottom = bounds.y + bounds.height
        val right = bounds.x + bounds.width
        for (ancestor in ancestorBounds) {
            clipTop = max(ancestor.y - bounds.y, clipTop)
            clipBottom = max(bottom - (ancestor.y + ancestor.height), clipBottom)
            clipLeft = max(ancestor.x - bounds.x, clipLeft)
            clipRight = max(right - (ancestor.x + ancestor.width), clipRight)
        }
        val hasNoClip = listOf(clipTop, clipBottom, clipLeft, clipRight).all { it <= 0 }
        if (hasNoClip) return null
        return CapturedClip(
            top = clipTop.takeIf { it > 0 },
            bottom = clipBottom.takeIf { it > 0 },
            left = clipLeft.takeIf { it > 0 },
            right = clipRight.takeIf { it > 0 }
        )
    }

    /**
     * Detected by class name only, deliberately with no compile-time `androidx.compose` dependency
     * from this module. `ComposeView` is the public entry point apps add to a native layout; the
     * internal `AndroidComposeView` it creates as its single child is Compose-owned and never itself
     * an addressable child in the surrounding native hierarchy.
     */
    private fun isComposeHost(view: View): Boolean = view.javaClass.name == COMPOSE_VIEW_CLASS_NAME

    private fun CapturedWireframe.withClip(clip: CapturedClip?): CapturedWireframe = when (this) {
        is CapturedWireframe.Shape -> copy(clip = clip)
        is CapturedWireframe.Text -> copy(clip = clip)
        is CapturedWireframe.WebView -> copy(clip = clip)
        is CapturedWireframe.Pixel -> copy(clip = clip)
        is CapturedWireframe.PrivacyPlaceholder -> copy(clip = clip)
    }

    private sealed interface LayerWalkResult {
        data class Present(val layer: CapturedLayer) : LayerWalkResult
        object Filtered : LayerWalkResult
        object Aborted : LayerWalkResult
    }

    private class TraversalState(val screenDensity: Float) {
        var viewsVisited = 0
        val layers = mutableListOf<CapturedLayer>()
        val wireframes = mutableListOf<CapturedWireframe>()
        val ancestorBounds = mutableListOf<CapturedBounds>()
    }

    private companion object {
        const val VIEWS_PER_CHECKPOINT = 200
        const val COMPOSE_VIEW_CLASS_NAME = "androidx.compose.ui.platform.ComposeView"
    }
}
