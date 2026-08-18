/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewGroup
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedHiddenViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.recorder.composition.CompositionNativeSubtree
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
 * [CapturedLayerKind.WINDOW_ROOT] for the window's own root, or [CapturedLayerKind.COMPOSE_HOST]
 * for a `ComposeView`). Its identity is always created via [CapturedIdentityFactory.view] (or
 * [CapturedIdentityFactory.composeHost]) against the *window's* identity - never the view's real
 * structural parent - per the flat-namespacing rule [CapturedIdentityFactory] enforces; real
 * nesting is carried purely by [CapturedLayer.children]. A Compose host's own interior is handed to
 * [composeHostDecomposer] instead of being recursed into as native children - see
 * [com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer].
 */
internal class AndroidWindowTraversal(
    private val mapperRegistry: CapturedViewMapperRegistry,
    private val hiddenViewMapper: CapturedViewMapper<View> = CapturedHiddenViewMapper(),
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal(),
    private val composeHostDecomposer: CompositionHostDecomposer? = null,
    private val viewsPerCheckpoint: Int = VIEWS_PER_CHECKPOINT,
    private val isComposeHost: (View) -> Boolean = ::isComposeHostByClassName
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
                ancestorBounds = emptyList(),
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

    @Suppress("ReturnCount", "LongParameterList")
    private fun visitView(
        view: View,
        ownIdentity: CapturedIdentity,
        ownKind: CapturedLayerKind,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        ancestorBounds: List<CapturedBounds>,
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

        val globalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, state.screenDensity)
        val bounds = CapturedBounds(globalBounds.x, globalBounds.y, globalBounds.width, globalBounds.height)
        val childAncestorBounds = ancestorBounds + bounds
        val isHidden = view.getTag(R.id.datadog_hidden) == true

        val children = mutableListOf<CapturedChild>()
        val attempt = attemptComposeDecomposition(
            view,
            ownKind,
            isHidden,
            ownIdentity,
            windowIdentity,
            identityFactory,
            context,
            state,
            childAncestorBounds
        )
        if (attempt is ComposeAttempt.Aborted) return LayerWalkResult.Aborted

        if (attempt is ComposeAttempt.Decomposed) {
            spliceComposeResult(attempt.result, childAncestorBounds, children, state)
        } else {
            val mappingContext = CapturedMappingContext(identityFactory, ownIdentity, state.screenDensity)
            val mapped = (if (isHidden) hiddenViewMapper else mapperRegistry.resolve(view)).map(view, mappingContext)
            addWireframes(mapped, ancestorBounds, children, state)
        }

        // A Compose host's interior is Compose's own node tree, not further Android child Views -
        // its content is fully described by whatever composeHostDecomposer returned above. Covers
        // both "not a compose host" and "decomposition wasn't attempted/failed".
        if (!isHidden && attempt !is ComposeAttempt.Decomposed && view is ViewGroup) {
            val mustAbort = recurseIntoNativeChildren(
                view,
                windowIdentity,
                identityFactory,
                childAncestorBounds,
                context,
                state,
                children
            )
            if (mustAbort) return LayerWalkResult.Aborted
        }

        val layer = CapturedLayer(identity = ownIdentity, kind = ownKind, bounds = bounds, children = children)
        state.layers.add(layer)
        return LayerWalkResult.Present(layer)
    }

    /**
     * Visits every native child of [view], appending each to [children]. Returns true if the whole
     * capture must be aborted because a child's visit ran past the deadline.
     */
    @Suppress("LongParameterList")
    private fun recurseIntoNativeChildren(
        view: ViewGroup,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        childAncestorBounds: List<CapturedBounds>,
        context: CaptureGenerationContext,
        state: TraversalState,
        children: MutableList<CapturedChild>
    ): Boolean {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) ?: continue
            when (
                val childResult =
                    visitChild(child, windowIdentity, identityFactory, childAncestorBounds, context, state)
            ) {
                is LayerWalkResult.Present -> children.add(CapturedChild.Layer(childResult.layer.identity))
                LayerWalkResult.Filtered -> Unit
                LayerWalkResult.Aborted -> return true
            }
        }
        return false
    }

    /** Mints a child's identity/kind (Compose host or plain native view) and visits it. */
    private fun visitChild(
        child: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        ancestorBounds: List<CapturedBounds>,
        context: CaptureGenerationContext,
        state: TraversalState
    ): LayerWalkResult {
        val localId = viewIdentifierResolver.resolveViewId(child).toString()
        val isChildComposeHost = isComposeHost(child)
        val childIdentity = if (isChildComposeHost) {
            identityFactory.composeHost(windowIdentity, localId)
        } else {
            identityFactory.view(windowIdentity, localId)
        }
        return visitView(
            view = child,
            ownIdentity = childIdentity,
            ownKind = if (isChildComposeHost) CapturedLayerKind.COMPOSE_HOST else CapturedLayerKind.NATIVE_VIEW,
            windowIdentity = windowIdentity,
            identityFactory = identityFactory,
            ancestorBounds = ancestorBounds,
            context = context,
            state = state
        )
    }

    /**
     * Attempts Compose decomposition for [view] when it's an eligible, non-hidden compose host.
     * Isolated from [visitView] to keep that function's own branching manageable - this is the
     * only place [ComposeAttempt.Aborted] can arise, since a decomposer's own main-thread work
     * isn't otherwise checkpointed by this traversal, so the deadline is re-polled immediately
     * after it returns rather than waiting for the next `viewsPerCheckpoint` tick.
     */
    @Suppress("LongParameterList", "ReturnCount")
    private fun attemptComposeDecomposition(
        view: View,
        ownKind: CapturedLayerKind,
        isHidden: Boolean,
        ownIdentity: CapturedIdentity,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        state: TraversalState,
        hostChildAncestorBounds: List<CapturedBounds>
    ): ComposeAttempt {
        val decomposer = composeHostDecomposer
            ?.takeIf { !isHidden && ownKind == CapturedLayerKind.COMPOSE_HOST && it.canDecompose(view) }
            ?: return ComposeAttempt.NotAttempted
        val request = CompositionHostDecomposeRequest(
            identityFactory = identityFactory,
            hostIdentity = ownIdentity,
            screenDensity = state.screenDensity,
            nativeViewHandoff = { interopView, childIdentity ->
                handleNativeViewHandoff(
                    interopView,
                    childIdentity,
                    windowIdentity,
                    identityFactory,
                    hostChildAncestorBounds,
                    context,
                    state
                )
            }
        )
        val result = decomposer.decompose(view, request)
        if (!context.shouldContinue()) return ComposeAttempt.Aborted
        return result?.let(ComposeAttempt::Decomposed) ?: ComposeAttempt.NotAttempted
    }

    /**
     * The Compose -> View seam: runs the ordinary native traversal over one embedded interop
     * [view], into a fresh, isolated [TraversalState] so its layers/wireframes can be returned as
     * one self-contained [CompositionNativeSubtree] rather than mutating the ambient [outerState]
     * directly - the caller (the Compose decomposer) decides how/whether to splice it in. Resets
     * the per-checkpoint view counter for this nested walk; interop subtrees are bounded, so this
     * only means slightly coarser deadline-checkpoint cadence, not a correctness issue.
     */
    private fun handleNativeViewHandoff(
        view: View,
        ownIdentity: CapturedIdentity,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        ancestorBounds: List<CapturedBounds>,
        context: CaptureGenerationContext,
        outerState: TraversalState
    ): CompositionNativeSubtree? {
        val localState = TraversalState(screenDensity = outerState.screenDensity)
        return when (
            val result = visitView(
                view = view,
                ownIdentity = ownIdentity,
                ownKind = CapturedLayerKind.NATIVE_VIEW,
                windowIdentity = windowIdentity,
                identityFactory = identityFactory,
                ancestorBounds = ancestorBounds,
                context = context,
                state = localState
            )
        ) {
            is LayerWalkResult.Present -> CompositionNativeSubtree(
                result.layer,
                localState.layers,
                localState.wireframes
            )
            LayerWalkResult.Filtered, LayerWalkResult.Aborted -> null
        }
    }

    /**
     * Splices a Compose host's decomposition into the ambient traversal state: every wireframe is
     * (re-)clipped against [hostChildAncestorBounds] - the host's own bounds are an ancestor for
     * everything inside it, whether produced directly by Compose or handed back from an embedded
     * native subtree (already correctly clipped against the same bounds, so this is a harmless,
     * idempotent recompute for those, and the only clipping pass for genuinely Compose-native ones,
     * since Compose has no ancestor-bounds-stack clip mechanism of its own - only its own explicit
     * modifiers, carried as [CapturedModifier]s on the layers themselves).
     */
    private fun spliceComposeResult(
        result: CompositionHostDecomposeResult,
        hostChildAncestorBounds: List<CapturedBounds>,
        children: MutableList<CapturedChild>,
        state: TraversalState
    ) {
        state.layers.addAll(result.nodes)
        for (wireframe in result.wireframes) {
            state.wireframes.add(wireframe.withClip(computeClip(wireframe.bounds, hostChildAncestorBounds)))
        }
        children.addAll(result.rootChildren)
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
        // Each value is accumulated via max(x, 0), so all four are always non-negative - "all <= 0"
        // reduces to a single comparison against their max, instead of a 4-term condition.
        if (max(max(clipTop, clipBottom), max(clipLeft, clipRight)) <= 0) return null
        return CapturedClip(
            top = clipTop.takeIf { it > 0 },
            bottom = clipBottom.takeIf { it > 0 },
            left = clipLeft.takeIf { it > 0 },
            right = clipRight.takeIf { it > 0 }
        )
    }

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

    private sealed interface ComposeAttempt {
        data class Decomposed(val result: CompositionHostDecomposeResult) : ComposeAttempt

        /** Not a compose host, or the decomposer declined/failed to produce anything. */
        object NotAttempted : ComposeAttempt

        /** The deadline expired during decomposition - the whole capture must be discarded. */
        object Aborted : ComposeAttempt
    }

    private class TraversalState(val screenDensity: Float) {
        var viewsVisited = 0
        val layers = mutableListOf<CapturedLayer>()
        val wireframes = mutableListOf<CapturedWireframe>()
    }

    private companion object {
        const val VIEWS_PER_CHECKPOINT = 200
    }
}

/**
 * Detected by class name only, deliberately with no compile-time `androidx.compose` dependency
 * from this module. `ComposeView` is the public entry point apps add to a native layout; the
 * internal `AndroidComposeView` it creates as its single child is Compose-owned and never itself
 * an addressable child in the surrounding native hierarchy.
 */
private fun isComposeHostByClassName(view: View): Boolean =
    view.javaClass.name == "androidx.compose.ui.platform.ComposeView"
