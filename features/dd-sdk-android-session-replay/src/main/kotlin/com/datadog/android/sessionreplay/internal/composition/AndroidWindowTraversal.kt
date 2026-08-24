/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.utils.isValidTapTarget
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedHiddenViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.PixelCaptureEligibility
import com.datadog.android.sessionreplay.internal.recorder.HeatmapIdentifierResolver
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.recorder.composition.CompositionNativeSubtree
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import kotlin.math.max

internal sealed interface WindowWalkResult {
    data class Present(
        val rootLayer: CapturedLayer,
        val layers: List<CapturedLayer>,
        val wireframes: List<CapturedWireframe>,
        val pendingPixelCaptures: List<PendingPixelCapture>
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
    private val rootImagePrivacy: ImagePrivacy = ImagePrivacy.MASK_ALL,
    private val rootTextAndInputPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND,
    private val viewsPerCheckpoint: Int = VIEWS_PER_CHECKPOINT,
    private val isComposeHost: (View) -> Boolean = ::isComposeHostByClassName,
    private val heatmapResolver: HeatmapIdentifierResolver? = null
) {

    /**
     * [viewUrl] scopes this window's heatmap identifiers to a screen (see
     * [HeatmapIdentifierResolver]) - null (no active RUM view, or heatmaps disabled) skips
     * identifier resolution entirely for this window. Only native View content gets a
     * [CapturedWireframe.permanentId][com.datadog.android.internal.sessionreplay.composition.CapturedWireframe.permanentId] -
     * a Compose host's own interior (including any embedded native "interop" View handed back via
     * [handleNativeViewHandoff]) is out of scope for now, same as the legacy pipeline's own gap.
     */
    @UiThread
    fun traverseWindow(
        windowRoot: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        viewUrl: String? = null
    ): WindowWalkResult {
        if (!context.shouldContinue()) return WindowWalkResult.Aborted
        val state = TraversalState(screenDensity = windowRoot.resources.displayMetrics.density)
        val rootPrivacy = EffectivePrivacy(rootImagePrivacy, rootTextAndInputPrivacy)
        val heatmapContext = viewUrl?.let { heatmapResolver?.beginTraversal(it) }
        return when (
            val result = visitView(
                view = windowRoot,
                ownIdentity = windowIdentity,
                ownKind = CapturedLayerKind.WINDOW_ROOT,
                windowIdentity = windowIdentity,
                identityFactory = identityFactory,
                ancestorBounds = emptyList(),
                inheritedPrivacy = rootPrivacy,
                context = context,
                state = state,
                nodePath = emptyList(),
                typeIndex = 0,
                heatmapContext = heatmapContext
            )
        ) {
            is LayerWalkResult.Present -> {
                heatmapContext?.publish()
                WindowWalkResult.Present(result.layer, state.layers, state.wireframes, state.pendingPixelCaptures)
            }
            LayerWalkResult.Filtered -> {
                heatmapContext?.publish()
                WindowWalkResult.Filtered
            }
            // The whole capture is discarded on abort - publishing a possibly-incomplete snapshot
            // of identifiers here would be worse than leaving the previous, complete one in place.
            LayerWalkResult.Aborted -> WindowWalkResult.Aborted
        }
    }

    @Suppress("ReturnCount", "LongParameterList", "LongMethod")
    @UiThread
    private fun visitView(
        view: View,
        ownIdentity: CapturedIdentity,
        ownKind: CapturedLayerKind,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        ancestorBounds: List<CapturedBounds>,
        inheritedPrivacy: EffectivePrivacy,
        context: CaptureGenerationContext,
        state: TraversalState,
        nodePath: List<String>,
        typeIndex: Int,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
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
        val ownPrivacy = resolveEffectivePrivacy(view, inheritedPrivacy, internalLogger)

        val heatmapIdentity = resolveHeatmapIdentity(view, nodePath, typeIndex, heatmapContext)
        val viewPath = heatmapIdentity?.viewPath ?: nodePath

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
            childAncestorBounds,
            ownPrivacy
        )
        if (attempt is ComposeAttempt.Aborted) return LayerWalkResult.Aborted

        val isPixelFallbackTerminal = if (attempt is ComposeAttempt.Decomposed) {
            spliceComposeResult(attempt.result, childAncestorBounds, children, state)
            false
        } else {
            mapNativeView(
                view,
                isHidden,
                identityFactory,
                ownIdentity,
                ancestorBounds,
                ownPrivacy,
                children,
                state,
                heatmapIdentity?.identifier
            )
        }

        val mustAbort = recurseIntoNativeChildren(
            view,
            isHidden,
            attempt,
            isPixelFallbackTerminal,
            windowIdentity,
            identityFactory,
            childAncestorBounds,
            ownPrivacy,
            context,
            state,
            children,
            viewPath,
            heatmapContext
        )
        if (mustAbort) return LayerWalkResult.Aborted

        val layer = CapturedLayer(identity = ownIdentity, kind = ownKind, bounds = bounds, children = children)
        state.layers.add(layer)
        return LayerWalkResult.Present(layer)
    }

    /**
     * Maps [view] via the appropriate native mapper (hidden or resolved) when Compose decomposition
     * wasn't attempted or didn't apply, recording its wireframes and reporting whether the result
     * is pixel-fallback terminal. Isolated purely to keep [visitView]'s own branching within
     * [LongMethod]'s budget.
     */
    @Suppress("LongParameterList")
    private fun mapNativeView(
        view: View,
        isHidden: Boolean,
        identityFactory: CapturedIdentityFactory,
        ownIdentity: CapturedIdentity,
        ancestorBounds: List<CapturedBounds>,
        ownPrivacy: EffectivePrivacy,
        children: MutableList<CapturedChild>,
        state: TraversalState,
        heatmapIdentifier: HeatmapIdentifier?
    ): Boolean {
        val mappingContext = CapturedMappingContext(
            identityFactory,
            ownIdentity,
            state.screenDensity,
            imagePrivacy = ownPrivacy.imagePrivacy,
            textAndInputPrivacy = ownPrivacy.textAndInputPrivacy,
            pendingPixelCaptureSink = PendingPixelCaptureSink { state.pendingPixelCaptures.add(it) }
        )
        val mapped = (if (isHidden) hiddenViewMapper else mapperRegistry.resolve(view)).map(view, mappingContext)
        addWireframes(mapped, ancestorBounds, children, state, heatmapIdentifier)
        return mapped.isPixelFallbackTerminal()
    }

    /**
     * Resolves [view]'s stable heatmap identity, mirroring the legacy pipeline's own
     * `SnapshotProducer.resolveHeatmapIdentity` - computed whenever [view] is itself a valid tap
     * target, or it's a [ViewGroup] with children whose own paths need this view's path segment as
     * a prefix regardless of whether it turns out to be a valid tap target itself. The latter check
     * is deliberately conservative (it doesn't know yet whether children will actually be walked -
     * e.g. a pixel-fallback-terminal ViewGroup never recurses into them) rather than tightly
     * coupled to that later decision: computing an unused path segment is harmless, but skipping a
     * needed one would silently break every descendant's identifier.
     */
    @UiThread
    private fun resolveHeatmapIdentity(
        view: View,
        nodePath: List<String>,
        typeIndex: Int,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
    ): HeatmapIdentifierResolver.HeatmapIdentity? {
        return heatmapContext?.let { ctx ->
            val pathMayBeNeededForChildren = view is ViewGroup && view.childCount > 0
            if (pathMayBeNeededForChildren || view.isValidTapTarget()) {
                ctx.resolveIdentity(view, nodePath, typeIndex)
            } else {
                null
            }
        }
    }

    /**
     * A Compose host's interior is Compose's own node tree, not further Android child Views - its
     * content is fully described by whatever composeHostDecomposer returned in [visitView]. Covers
     * both "not a compose host" and "decomposition wasn't attempted/failed". A pixel/placeholder
     * wireframe is the same kind of terminal description on the native side: [View.draw] already
     * bakes every child into the bitmap it produced (or the placeholder is standing in for that
     * same subtree), so walking the real children again would only double-describe them - in
     * either case there's nothing to recurse into, so this returns early rather than requiring
     * [visitView] itself to guard the call. Otherwise visits every native child of [view],
     * appending each to [children]. Returns true if the whole capture must be aborted because a
     * child's visit ran past the deadline.
     */
    @Suppress("LongParameterList", "ReturnCount")
    @UiThread
    private fun recurseIntoNativeChildren(
        view: View,
        isHidden: Boolean,
        attempt: ComposeAttempt,
        isPixelFallbackTerminal: Boolean,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        childAncestorBounds: List<CapturedBounds>,
        inheritedPrivacy: EffectivePrivacy,
        context: CaptureGenerationContext,
        state: TraversalState,
        children: MutableList<CapturedChild>,
        nodePath: List<String>,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
    ): Boolean {
        val isNativeContainerNeedingChildren = !isHidden && attempt !is ComposeAttempt.Decomposed &&
            !isPixelFallbackTerminal
        if (!isNativeContainerNeedingChildren || view !is ViewGroup) return false
        // Computed once per parent regardless of whether heatmapContext is present: same pattern
        // as the identity resolution itself, cheap when there's nothing to compute for.
        val childTypeIndices = heatmapContext?.computeChildTypeIndices(view)
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) ?: continue

            @Suppress("UnsafeThirdPartyFunctionCall") // i is always in-bounds: array size == view.childCount
            val childTypeIndex = childTypeIndices?.get(i) ?: 0
            when (
                val childResult = visitChild(
                    child,
                    windowIdentity,
                    identityFactory,
                    childAncestorBounds,
                    inheritedPrivacy,
                    context,
                    state,
                    nodePath,
                    childTypeIndex,
                    heatmapContext
                )
            ) {
                is LayerWalkResult.Present -> children.add(CapturedChild.Layer(childResult.layer.identity))
                LayerWalkResult.Filtered -> Unit
                LayerWalkResult.Aborted -> return true
            }
        }
        return false
    }

    /** Mints a child's identity/kind (Compose host or plain native view) and visits it. */
    @Suppress("LongParameterList")
    @UiThread
    private fun visitChild(
        child: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        ancestorBounds: List<CapturedBounds>,
        inheritedPrivacy: EffectivePrivacy,
        context: CaptureGenerationContext,
        state: TraversalState,
        nodePath: List<String>,
        typeIndex: Int,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
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
            inheritedPrivacy = inheritedPrivacy,
            context = context,
            state = state,
            nodePath = nodePath,
            typeIndex = typeIndex,
            heatmapContext = heatmapContext
        )
    }

    /**
     * Attempts Compose decomposition for [view] when it's an eligible, non-hidden compose host.
     * Isolated from [visitView] to keep that function's own branching manageable. The decomposer
     * itself checkpoints [CompositionHostDecomposeRequest.shouldContinue] against its own cadence
     * (it lives in a separate module with no other visibility into the deadline), but the deadline
     * is re-polled here too immediately after it returns rather than waiting for the next
     * `viewsPerCheckpoint` tick, so this remains the single place [ComposeAttempt.Aborted] can
     * arise regardless of whether the decomposer's own checkpoint ever fired.
     */
    @Suppress("LongParameterList", "ReturnCount")
    @UiThread
    private fun attemptComposeDecomposition(
        view: View,
        ownKind: CapturedLayerKind,
        isHidden: Boolean,
        ownIdentity: CapturedIdentity,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        state: TraversalState,
        hostChildAncestorBounds: List<CapturedBounds>,
        ownPrivacy: EffectivePrivacy
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
                    ownPrivacy,
                    context,
                    state
                )
            },
            shouldContinue = context::shouldContinue,
            pixelCapturePlaceholderLabelFor = { bounds ->
                PixelCaptureEligibility.placeholderLabelFor(
                    ownPrivacy.imagePrivacy,
                    GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)
                )
            },
            pendingPixelCaptureSink = PendingPixelCaptureSink { state.pendingPixelCaptures.add(it) }
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
     * only means slightly coarser deadline-checkpoint cadence, not a correctness issue. Any pending
     * pixel captures collected inside the interop subtree are merged directly into [outerState] -
     * they're a traversal-internal side channel, not part of [CompositionNativeSubtree]'s shape.
     */
    @Suppress("LongParameterList")
    @UiThread
    private fun handleNativeViewHandoff(
        view: View,
        ownIdentity: CapturedIdentity,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        ancestorBounds: List<CapturedBounds>,
        inheritedPrivacy: EffectivePrivacy,
        context: CaptureGenerationContext,
        outerState: TraversalState
    ): CompositionNativeSubtree? {
        val localState = TraversalState(screenDensity = outerState.screenDensity)
        val result = try {
            visitView(
                view = view,
                ownIdentity = ownIdentity,
                ownKind = CapturedLayerKind.NATIVE_VIEW,
                windowIdentity = windowIdentity,
                identityFactory = identityFactory,
                ancestorBounds = ancestorBounds,
                inheritedPrivacy = inheritedPrivacy,
                context = context,
                state = localState,
                // Out of scope for now: a native View embedded inside a Compose host has no
                // legacy analog to mirror a path/heatmap story from - see this class's own doc.
                nodePath = emptyList(),
                typeIndex = 0,
                heatmapContext = null
            )
        } finally {
            outerState.pendingPixelCaptures.addAll(localState.pendingPixelCaptures)
        }
        return when (result) {
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
        state: TraversalState,
        heatmapIdentifier: HeatmapIdentifier?
    ) {
        if (result !is CapturedViewMapperResult.Wireframes) return
        for (wireframe in result.wireframes) {
            val clipped = wireframe.withClip(computeClip(wireframe.bounds, ancestorBounds))
            // Every wireframe this one View produced shares the same permanentId, same as legacy's
            // NodeFlattener applying one Node's heatmapIdentifier to all of that Node's wireframes -
            // e.g. a TextView's background-shape and text wireframes both identify the same element.
            val identified = heatmapIdentifier?.let { clipped.withPermanentId(it.rawValue) } ?: clipped
            state.wireframes.add(identified)
            children.add(CapturedChild.Wireframe(identified.identity))
        }
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
        val pendingPixelCaptures = mutableListOf<PendingPixelCapture>()
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

/**
 * A [CapturedWireframe.Pixel] or [CapturedWireframe.PrivacyPlaceholder] from the native
 * pixel-fallback mapper already stands in for this view's entire subtree - the bitmap it
 * rasterized bakes every child in, and the placeholder is standing in for that same bitmap.
 * Walking the real children afterward would describe them a second time, redundantly. Stateless -
 * kept out of [AndroidWindowTraversal] itself to stay within [TooManyFunctions]'s budget.
 */
private fun CapturedViewMapperResult.isPixelFallbackTerminal(): Boolean =
    this is CapturedViewMapperResult.Wireframes &&
        wireframes.any { it is CapturedWireframe.Pixel || it is CapturedWireframe.PrivacyPlaceholder }

/** Stateless - kept out of [AndroidWindowTraversal] itself to stay within [TooManyFunctions]'s budget. */
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

/** Stateless - kept out of [AndroidWindowTraversal] itself to stay within [TooManyFunctions]'s budget. */
private fun CapturedWireframe.withClip(clip: CapturedClip?): CapturedWireframe = when (this) {
    is CapturedWireframe.Shape -> copy(clip = clip)
    is CapturedWireframe.Text -> copy(clip = clip)
    is CapturedWireframe.WebView -> copy(clip = clip)
    is CapturedWireframe.Pixel -> copy(clip = clip)
    is CapturedWireframe.PrivacyPlaceholder -> copy(clip = clip)
}

/** Stateless - kept out of [AndroidWindowTraversal] itself to stay within [TooManyFunctions]'s budget. */
private fun CapturedWireframe.withPermanentId(permanentId: String): CapturedWireframe = when (this) {
    is CapturedWireframe.Shape -> copy(permanentId = permanentId)
    is CapturedWireframe.Text -> copy(permanentId = permanentId)
    is CapturedWireframe.WebView -> copy(permanentId = permanentId)
    is CapturedWireframe.Pixel -> copy(permanentId = permanentId)
    is CapturedWireframe.PrivacyPlaceholder -> copy(permanentId = permanentId)
}
