/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("TooManyFunctions") // Compose decomposition and native-view traversal are split by concern.

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.utils.isValidTapTarget
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedEmbeddedContentMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedHiddenViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.LegacyCapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.PendingViewMapping
import com.datadog.android.sessionreplay.internal.composition.mapper.PixelCaptureEligibility
import com.datadog.android.sessionreplay.internal.putTouchOverrideArea
import com.datadog.android.sessionreplay.internal.recorder.DefaultOptionSelectorDetector
import com.datadog.android.sessionreplay.internal.recorder.HeatmapIdentifierResolver
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.recorder.composition.CompositionNativeSubtree
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.IdentityHashMap
import kotlin.math.roundToInt

internal sealed interface WindowCopyResult {
    data class Present(
        val window: CapturedNativeWindow,
        val touchOverrideAreas: Map<Rect, TouchPrivacy>,
        val requiresTouchPrivacyCapture: Boolean = false
    ) : WindowCopyResult
    object Filtered : WindowCopyResult
    object Aborted : WindowCopyResult
}

/**
 * Copies one native window in a single UI-thread turn. Only this phase reads live Views; native
 * text styling and final clipping/layer construction operate on the copy on the construction executor.
 * Compose hosts contribute owned subtrees without traversing their native implementation children.
 * The iterative walk avoids call-stack limits without yielding between inconsistent UI states.
 */
internal class AndroidWindowTraversal(
    private val mapperRegistry: CapturedViewMapperRegistry,
    private val internalLogger: InternalLogger,
    private val hiddenViewMapper: CapturedViewMapper<View> = CapturedHiddenViewMapper(),
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault(),
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal(),
    private val composeHostDecomposer: CompositionHostDecomposer? = null,
    private val drawingOrderResolver: ViewGroupDrawingOrderResolver = ViewGroupDrawingOrderResolver(internalLogger),
    private val rootImagePrivacy: ImagePrivacy = ImagePrivacy.MASK_ALL,
    private val rootTextAndInputPrivacy: TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
    private val isComposeHost: (View) -> Boolean = ::isComposeHostByClassName,
    private val pixelCapture: ViewPixelCapture = ViewPixelCapture(internalLogger),
    private val foregroundCapture: ViewForegroundCapture = ViewForegroundCapture(internalLogger),
    private val heatmapResolver: HeatmapIdentifierResolver? = null,
    private val embeddedContentMapper: CapturedEmbeddedContentMapper? = null,
    private val optionSelectorDetector: OptionSelectorDetector = DefaultOptionSelectorDetector()
) {
    private val occlusionDetector = ViewOcclusionDetector(drawableToColorMapper, viewUtilsInternal, internalLogger)

    @UiThread
    fun traverseWindow(
        windowRoot: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        viewUrl: String? = null
    ): WindowCopyResult = copyWindow(
        windowRoot,
        CopyState(
            windowRoot.resources.displayMetrics.density,
            windowIdentity,
            identityFactory,
            EffectivePrivacy(rootImagePrivacy, rootTextAndInputPrivacy),
            heatmapContext = viewUrl?.let { heatmapResolver?.beginTraversal(it) }
        ),
        context
    )

    @UiThread
    @Suppress("ReturnCount") // Expiry discards all work.
    private fun copyWindow(windowRoot: View, state: CopyState, context: CaptureGenerationContext): WindowCopyResult {
        @Suppress("UnsafeThirdPartyFunctionCall") // Empty UI-owned deque.
        val stack = ArrayDeque<WorkItem>()
        @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded UI-owned deque.
        stack.addLast(
            WorkItem(
                windowRoot,
                -1,
                clipToBounds = state.rootKind == CapturedLayerKind.WINDOW_ROOT,
                inheritedPrivacy = state.rootPrivacy,
                hasOptionSelectorParent = state.rootHasOptionSelectorParent
            )
        )
        while (stack.size > 0) {
            if (!context.shouldContinue()) return WindowCopyResult.Aborted
            @Suppress("UnsafeThirdPartyFunctionCall") // Non-empty check above; synchronous ownership.
            val item = stack.removeLast()
            if (!tryVisit(item, state)) continue
            // Separate surfaces remain uncertain even inside a subtree omitted by sibling culling.
            state.isDrawingOrderReliable = state.isDrawingOrderReliable && item.view.sharesNativePaintOrder()
            collectTouchOverrideArea(item.view, state.touchOverrideAreas, internalLogger, context::shouldContinue)
            collectComposeTouchOverrides(item.view, state.touchOverrideAreas, context)
            if (item.touchOnly) {
                captureTouchOnlyCustomMapper(item, state, context, mapperRegistry, internalLogger)
                (item.view as? ViewGroup)?.let {
                    pushTouchChildren(
                        it,
                        stack,
                        resolveEffectivePrivacy(it, item.inheritedPrivacy, internalLogger),
                        item.hasOptionSelectorParent || isOptionSelector(it)
                    )
                }
            } else {
                copyView(item, stack, state, context)
            }
        }
        if (!context.shouldContinue()) return WindowCopyResult.Aborted
        state.heatmapContext?.publish()
        return if (state.views.isEmpty()) {
            WindowCopyResult.Filtered
        } else {
            // Lists are owned by this copy and are never mutated after this handoff.
            WindowCopyResult.Present(
                CapturedNativeWindow(
                    state.density,
                    state.views,
                    state.isDrawingOrderReliable,
                    state.pendingPixelCaptures,
                    state.pendingMappings
                ),
                state.touchOverrideAreas,
                state.requiresTouchPrivacyCapture
            )
        }
    }

    @UiThread
    private fun collectComposeTouchOverrides(
        view: View,
        overrides: MutableMap<Rect, TouchPrivacy>,
        context: CaptureGenerationContext
    ) {
        if (composeHostDecomposer == null || !isComposeHost(view)) return
        val areas = readComposeTouchPrivacy(view, context, composeHostDecomposer, internalLogger)
        if (areas == null || ViewTransformResolver.hasTransformedAncestor(view)) {
            val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, 1f)
            overrides.putTouchOverrideArea(
                Rect(
                    bounds.x.toInt(),
                    bounds.y.toInt(),
                    (bounds.x + bounds.width).toInt(),
                    (bounds.y + bounds.height).toInt()
                ),
                TouchPrivacy.HIDE
            )
        } else {
            areas.forEach { (rect, privacy) ->
                clipTouchOverrideArea(view, rect, privacy, context::shouldContinue)?.let {
                    overrides.putTouchOverrideArea(it, privacy)
                }
            }
        }
    }

    @UiThread
    private fun tryVisit(item: WorkItem, state: CopyState): Boolean {
        val view = item.view
        if (shouldFilterFromTraversal(view) ||
            view.parent !== item.expectedParent
        ) {
            return false
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // UI-owned IdentityHashMap accepts this non-null key/value.
        return state.visited.put(view, true) == null
    }

    @UiThread
    private fun copyView(
        item: WorkItem,
        stack: ArrayDeque<WorkItem>,
        state: CopyState,
        context: CaptureGenerationContext
    ) {
        val view = item.view
        val kind = resolveLayerKind(item, state)
        val identity = resolveIdentity(item, kind, state)
        // Read geometry once. Retain the original pixels for coverage and derive the same replay bounds.
        val hasTransform = ViewTransformResolver.hasSpatialTransform(view, item.inheritedScale)
        val transform = if (hasTransform) ViewTransformResolver.resolve(view) else null
        val viewScale = transform?.uniformScale ?: 1f
        val requiresRaster = requiresRasterCapture(hasTransform, transform, kind)
        val pixels = if (hasTransform) transform?.pixelBounds else resolvePixelBounds(view, state.locationOnScreen)
        val bounds = resolveCapturedBounds(view, pixels, hasTransform, state.density)
        val ownPrivacy = resolveEffectivePrivacy(view, item.inheritedPrivacy, internalLogger)
        val heatmapIdentity = resolveHeatmapIdentity(view, item, state.heatmapContext)
        val mappingContext = mappingContext(item, identity, bounds, ownPrivacy, viewScale, state, context)
        val hidden = view.getTag(R.id.datadog_hidden) == true
        val composition = if (canTryComposition(view, hasTransform)) {
            safelyDecomposeHost(view, kind, hidden, mappingContext, state, context)
        } else {
            null
        }
        if (!context.shouldContinue()) return
        val viewData = captureViewData(item, mappingContext, hidden, requiresRaster, transform, composition != null)
        val children = if (!hidden && composition == null && !viewData.isTerminal) view as? ViewGroup else null
        val clippingViewGroup = if (composition != null) view as? ViewGroup else children
        val pixelPaddingClip = clippingViewGroup?.let { pixels?.paddingClip(it, transform) }
        val paddingClip = resolvePaddingClip(clippingViewGroup, pixelPaddingClip, state.density)
        // The transformed rasterizer bakes explicit clips in local coordinates into the bitmap.
        val pixelClip = resolvePixelClip(view, pixels, transform, requiresRaster)
        val clip = resolveExplicitClip(view, pixels, pixelClip, state.density, requiresRaster)
        state.views += CapturedNativeView(
            identity,
            item.parentIndex,
            bounds,
            view.alpha.toDouble(),
            viewData,
            item.clipToBounds,
            clip,
            paddingClip,
            pixels,
            pixelClip,
            pixelPaddingClip,
            view.hasReliableGeometry(kind, item.isGeometryReliable, hasTransform),
            kind,
            composition,
            foreground = captureForeground(view, mappingContext, viewData, hidden),
            viewScale = viewScale,
            permanentId = heatmapIdentity?.identifier?.rawValue,
            modifiers = captureNativeModifiers(view, bounds, state.density, internalLogger)
        )
        pushNativeChildren(
            view,
            children,
            hidden,
            ownPrivacy,
            stack,
            state,
            heatmapIdentity?.viewPath ?: item.nodePath,
            item.hasOptionSelectorParent || isOptionSelector(view)
        )
    }

    @UiThread
    @Suppress("LongParameterList", "UnsafeThirdPartyFunctionCall") // Sinks append to owned, unbounded lists.
    private fun mappingContext(
        item: WorkItem,
        identity: CapturedIdentity,
        bounds: CapturedBounds,
        privacy: EffectivePrivacy,
        scale: Float,
        state: CopyState,
        generation: CaptureGenerationContext
    ) = CapturedMappingContext(
        state.identityFactory, identity, state.density, bounds,
        imagePrivacy = privacy.imagePrivacy,
        textAndInputPrivacy = privacy.textAndInputPrivacy,
        pendingPixelCaptureSink = PendingPixelCaptureSink { state.pendingPixelCaptures.add(it) },
        viewScale = scale,
        shouldContinue = generation::shouldContinue,
        pendingMappingSink = { state.pendingMappings.add(it) },
        hasOptionSelectorParent = item.hasOptionSelectorParent,
        isOptionSelector = { isOptionSelector(it) },
        customTouchPrivacySink = state::collectCustomTouchPrivacy
    )

    @UiThread
    private fun canTryComposition(view: View, hasTransform: Boolean): Boolean =
        !hasTransform && embeddedContentMapper?.hasSlotId(view) != true &&
            mapperRegistry.resolve(view) !is LegacyCapturedViewMapper<*>

    /**
     * A slot-tagged view is never dropped for invisibility - it still needs an explicit hidden
     * [com.datadog.android.internal.sessionreplay.composition.CapturedWireframe.EmbeddedContent] placeholder (via [resolveMapper]/[CapturedEmbeddedContentMapper]),
     * not silent omission, mirroring legacy `TreeViewTraversal`'s own `embeddedMapper == null &&
     * isNotVisible` gate. System noise/secondary-display views are still dropped unconditionally.
     */
    @UiThread
    private fun shouldFilterFromTraversal(view: View): Boolean {
        val isSlotTagged = embeddedContentMapper?.hasSlotId(view) == true
        return (!isSlotTagged && viewUtilsInternal.isNotVisible(view)) ||
            viewUtilsInternal.isSystemNoise(view) ||
            viewUtilsInternal.isOnSecondaryDisplay(view)
    }

    /**
     * [isHidden] (privacy masking) always wins over slot tagging, matching legacy
     * `TreeViewTraversal`'s own priority - a privacy-hidden view is never exposed via an embedded
     * content placeholder either. Slot tagging otherwise takes priority over the type-based
     * [mapperRegistry]: it's orthogonal to a view's concrete class (any [View] subtype can carry the
     * tag), which [CapturedViewMapperRegistry]'s type-based dispatch can't express.
     */
    @UiThread
    private fun resolveMapper(view: View, isHidden: Boolean): CapturedViewMapper<View> = when {
        isHidden -> hiddenViewMapper
        embeddedContentMapper?.hasSlotId(view) == true -> embeddedContentMapper
        else -> mapperRegistry.resolve(view)
    }

    @UiThread
    private fun resolveHeatmapIdentity(
        view: View,
        item: WorkItem,
        heatmapContext: HeatmapIdentifierResolver.TraversalContext?
    ): HeatmapIdentifierResolver.HeatmapIdentity? = heatmapContext?.let {
        if ((view is ViewGroup && view.childCount > 0) || view.isValidTapTarget()) {
            it.resolveIdentity(view, item.nodePath, item.typeIndex)
        } else {
            null
        }
    }

    @UiThread
    private fun captureForeground(
        view: View,
        context: CapturedMappingContext,
        viewData: CapturedViewData,
        hidden: Boolean
    ): CapturedForeground? =
        if (hidden || viewData.isTerminal) null else foregroundCapture.capture(view, context)

    @UiThread
    private fun resolveCapturedBounds(
        view: View,
        pixels: GlobalBounds?,
        transformed: Boolean,
        density: Float
    ): CapturedBounds = when {
        pixels == null -> viewBoundsResolver.resolveViewGlobalBounds(view, density).toCaptured()
        transformed -> pixels.toTransformedReplayBounds(density)
        else -> pixels.toNormalizedBounds(density)
    }

    @UiThread
    @Suppress("LongParameterList")
    private fun captureViewData(
        item: WorkItem,
        context: CapturedMappingContext,
        hidden: Boolean,
        transformed: Boolean,
        transform: ViewTransform?,
        decomposed: Boolean
    ): CapturedViewData = when {
        decomposed -> CapturedViewData.Mapped(CapturedViewMapperResult.None)
        hidden -> hiddenViewMapper.capture(item.view, context)
        embeddedContentMapper?.hasSlotId(
            item.view
        ) == true -> resolveMapper(item.view, false).capture(item.view, context)
        transformed -> CapturedViewData.Mapped(
            pixelCapture.captureTransformed(item.view, context, transform, item.clipToBounds)
        )
        else -> mapperRegistry.resolve(item.view).capture(item.view, context)
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // copyView appended this view before pushing its children.
    private fun pushNativeChildren(
        view: View,
        children: ViewGroup?,
        hidden: Boolean,
        inheritedPrivacy: EffectivePrivacy,
        stack: ArrayDeque<WorkItem>,
        state: CopyState,
        nodePath: List<String>,
        hasOptionSelectorParent: Boolean
    ) {
        val copied = state.views.last()
        if (children != null) {
            pushChildren(children, copied, inheritedPrivacy, stack, state, nodePath, hasOptionSelectorParent)
        } else if (view is ViewGroup && view !is WebView) {
            if (hidden || copied.viewData.isTerminal) {
                pushTouchChildren(view, stack, inheritedPrivacy, hasOptionSelectorParent)
            }
        }
    }

    @UiThread
    private fun resolveLayerKind(item: WorkItem, state: CopyState): CapturedLayerKind = when {
        item.parentIndex < 0 -> state.rootKind
        isComposeHost(item.view) -> CapturedLayerKind.COMPOSE_HOST
        else -> CapturedLayerKind.NATIVE_VIEW
    }

    @UiThread
    private fun resolveIdentity(item: WorkItem, kind: CapturedLayerKind, state: CopyState): CapturedIdentity {
        if (item.parentIndex < 0) return state.rootIdentity
        val localId = viewIdentifierResolver.resolveViewId(item.view).toString()
        return if (kind == CapturedLayerKind.COMPOSE_HOST) {
            state.identityFactory.composeHost(state.windowIdentity, localId)
        } else {
            state.identityFactory.view(state.windowIdentity, localId)
        }
    }

    @UiThread
    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    private fun safelyDecomposeHost(
        view: View,
        kind: CapturedLayerKind,
        hidden: Boolean,
        mappingContext: CapturedMappingContext,
        state: CopyState,
        context: CaptureGenerationContext
    ): CompositionHostDecomposeResult? = try {
        decomposeHost(view, kind, hidden, mappingContext, state, context)
    } catch (e: Exception) {
        logDecompositionFailure(internalLogger, e)
        null
    } catch (e: LinkageError) {
        logDecompositionFailure(internalLogger, e)
        null
    }

    @UiThread
    @Suppress("LongParameterList", "ReturnCount", "UnsafeThirdPartyFunctionCall")
    private fun decomposeHost(
        view: View,
        kind: CapturedLayerKind,
        hidden: Boolean,
        mappingContext: CapturedMappingContext,
        state: CopyState,
        context: CaptureGenerationContext
    ): CompositionHostDecomposeResult? {
        val decomposer = composeHostDecomposer
            ?.takeIf {
                !hidden && (
                    kind == CapturedLayerKind.COMPOSE_HOST ||
                        (kind == CapturedLayerKind.WINDOW_ROOT && isComposeHost(view))
                    ) && it.canDecompose(view)
            }
            ?: return null
        val hostIdentity = if (kind == CapturedLayerKind.WINDOW_ROOT) {
            state.identityFactory.composeHost(state.windowIdentity, "root-host")
        } else {
            mappingContext.ownerIdentity
        }
        // A failed decomposition must not leave native handoff jobs or touches in the real capture.
        val handoffState = CopyState(
            state.density,
            state.windowIdentity,
            state.identityFactory,
            state.rootPrivacy,
            visited = IdentityHashMap(state.visited)
        )
        val request = decomposeRequest(hostIdentity, mappingContext, handoffState, context)
        val result = decomposer.decompose(view, request) ?: return null
        state.pendingPixelCaptures += handoffState.pendingPixelCaptures
        state.pendingMappings += handoffState.pendingMappings
        state.requiresTouchPrivacyCapture =
            state.requiresTouchPrivacyCapture || handoffState.requiresTouchPrivacyCapture
        handoffState.touchOverrideAreas.forEach { (rect, privacy) ->
            state.touchOverrideAreas.putTouchOverrideArea(rect, privacy)
        }
        state.visited.putAll(handoffState.visited)
        state.isDrawingOrderReliable = state.isDrawingOrderReliable && handoffState.isDrawingOrderReliable
        return if (kind == CapturedLayerKind.WINDOW_ROOT) {
            CompositionHostDecomposeResult(
                listOf(CapturedChild.Layer(hostIdentity)),
                result.nodes + CapturedLayer(
                    hostIdentity,
                    CapturedLayerKind.COMPOSE_HOST,
                    mappingContext.bounds ?: CapturedBounds(0, 0, 0, 0),
                    result.rootChildren
                ),
                result.wireframes,
                result.nativeSubtrees
            )
        } else {
            result
        }
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // Capture-owned sinks only append to unbounded lists.
    private fun decomposeRequest(
        hostIdentity: CapturedIdentity,
        mappingContext: CapturedMappingContext,
        handoffState: CopyState,
        context: CaptureGenerationContext
    ) = CompositionHostDecomposeRequest(
        identityFactory = handoffState.identityFactory,
        hostIdentity = hostIdentity,
        screenDensity = handoffState.density,
        hostBounds = mappingContext.bounds,
        nativeViewHandoff = { interopView, childIdentity ->
            captureNativeSubtree(
                interopView,
                childIdentity,
                handoffState,
                EffectivePrivacy(mappingContext.imagePrivacy, mappingContext.textAndInputPrivacy),
                context,
                mappingContext.hasOptionSelectorParent
            )
        },
        shouldContinue = context::shouldContinue,
        pixelCapturePlaceholderLabelFor = { bounds ->
            PixelCaptureEligibility.placeholderLabelFor(
                mappingContext.imagePrivacy,
                GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)
            )
        },
        pendingPixelCaptureSink = PendingPixelCaptureSink { handoffState.pendingPixelCaptures.add(it) },
        imagePrivacy = mappingContext.imagePrivacy,
        textAndInputPrivacy = mappingContext.textAndInputPrivacy,
        nativeViewHandoffWithPrivacy = { interopView, childIdentity, imagePrivacy, textPrivacy ->
            captureNativeSubtree(
                interopView,
                childIdentity,
                handoffState,
                EffectivePrivacy(imagePrivacy, textPrivacy),
                context,
                mappingContext.hasOptionSelectorParent
            )
        },
        pixelCapturePlaceholderLabelForPrivacy = { imagePrivacy, bounds ->
            PixelCaptureEligibility.placeholderLabelFor(
                imagePrivacy,
                GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)
            )
        },
        hasOptionSelectorParent = mappingContext.hasOptionSelectorParent
    )

    /** Copies native interop now; construction waits for mapper jobs on the worker. */
    @UiThread
    // Filtered/expired subtrees contribute nothing; the identity map copies synchronous, capture-owned state.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    private fun captureNativeSubtree(
        view: View,
        identity: CapturedIdentity,
        outerState: CopyState,
        inheritedPrivacy: EffectivePrivacy,
        context: CaptureGenerationContext,
        hasOptionSelectorParent: Boolean = false
    ): CompositionNativeSubtree? {
        val state = CopyState(
            outerState.density,
            outerState.windowIdentity,
            outerState.identityFactory,
            inheritedPrivacy,
            rootIdentity = identity,
            rootKind = CapturedLayerKind.NATIVE_VIEW,
            // Seed ancestor visits to prevent re-entry, but keep tentative handoffs isolated so
            // a null decomposition can still traverse the same native children in its fallback.
            visited = IdentityHashMap(outerState.visited),
            rootHasOptionSelectorParent = hasOptionSelectorParent
        )
        val copy = copyWindow(view, state, context) as? WindowCopyResult.Present ?: return null
        copy.touchOverrideAreas.forEach { (bounds, privacy) ->
            outerState.touchOverrideAreas.putTouchOverrideArea(bounds, privacy)
        }
        outerState.pendingPixelCaptures += copy.window.pendingPixelCaptures
        outerState.pendingMappings += copy.window.pendingMappings
        outerState.requiresTouchPrivacyCapture =
            outerState.requiresTouchPrivacyCapture || copy.requiresTouchPrivacyCapture
        outerState.visited.putAll(state.visited)
        outerState.isDrawingOrderReliable = outerState.isDrawingOrderReliable && copy.window.isDrawingOrderReliable
        val root = copy.window.views.firstOrNull() ?: return null
        return CompositionNativeSubtree(
            CapturedLayer(root.identity, root.kind, root.bounds, emptyList()),
            emptyList(),
            emptyList(),
            resolve = {
                CapturedNativeSnapshotBuilder().buildWindow(copy.window, context)?.let {
                    CompositionNativeSubtree(it.rootLayer, it.layers, it.wireframes)
                }
            }
        )
    }

    @UiThread
    private fun resolvePixelBounds(view: View, location: IntArray): GlobalBounds? {
        if (viewBoundsResolver !== DefaultViewBoundsResolver) return null
        @Suppress("UnsafeThirdPartyFunctionCall") // Capture-owned coordinate array always has two entries.
        view.getLocationOnScreen(location)
        return GlobalBounds(location[0].toLong(), location[1].toLong(), view.width.toLong(), view.height.toLong())
    }

    @UiThread
    private fun resolvePaddingClip(
        children: ViewGroup?,
        pixels: GlobalBounds?,
        density: Float
    ): CapturedBounds? {
        if (pixels != null) return pixels.toNormalizedBounds(density)
        return if (children != null && children.clipToPadding && children.hasNonZeroPadding()) {
            viewBoundsResolver.resolveViewPaddedBounds(children, density).toCaptured()
        } else {
            null
        }
    }

    @UiThread
    private fun resolveExplicitClip(
        view: View,
        pixels: GlobalBounds?,
        clip: GlobalBounds?,
        density: Float,
        transformed: Boolean
    ): CapturedBounds? = when {
        transformed -> null
        pixels != null -> clip?.toNormalizedClip(density)
        else -> view.clipBoundsOnScreen(density)
    }

    @UiThread
    private fun resolvePixelClip(
        view: View,
        pixels: GlobalBounds?,
        transform: ViewTransform?,
        rasterized: Boolean
    ): GlobalBounds? = when {
        rasterized -> null
        transform != null -> view.clipBounds?.let {
            transform.mapBounds(
                GlobalBounds(it.left.toLong(), it.top.toLong(), it.width().toLong(), it.height().toLong())
            )
        }
        else -> pixels?.explicitClip(view)
    }

    @UiThread
    private fun pushChildren(
        viewGroup: ViewGroup,
        parent: CapturedNativeView,
        inheritedPrivacy: EffectivePrivacy,
        stack: ArrayDeque<WorkItem>,
        state: CopyState,
        nodePath: List<String>,
        hasOptionSelectorParent: Boolean
    ) {
        val drawingOrder = drawingOrderResolver.resolve(viewGroup)
        val childTypeIndices = IdentityHashMap<View, Int>()
        state.heatmapContext?.computeChildTypeIndices(viewGroup)?.let { indices ->
            for (i in indices.indices) viewGroup.getChildAt(i)?.let { childTypeIndices[it] = indices[i] }
        }
        state.isDrawingOrderReliable = state.isDrawingOrderReliable && drawingOrder.isReliable
        val clipChildren = viewGroup.clipChildren
        val occluded = if (clipChildren) occlusionDetector.occludedChildIndices(drawingOrder) else emptySet()
        for (i in drawingOrder.children.indices.reversed()) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded UI-owned deque.
            stack.addLast(
                WorkItem(
                    drawingOrder.children[i],
                    state.views.lastIndex,
                    clipChildren,
                    i in occluded,
                    parent.isGeometryReliable,
                    inheritedPrivacy = inheritedPrivacy,
                    inheritedScale = parent.viewScale,
                    nodePath = nodePath,
                    typeIndex = childTypeIndices[drawingOrder.children[i]] ?: 0,
                    hasOptionSelectorParent = hasOptionSelectorParent
                )
            )
        }
    }

    // Occlusion affects drawing, not touch delivery: keep collecting tags throughout covered subtrees.
    @UiThread
    private fun pushTouchChildren(
        viewGroup: ViewGroup,
        stack: ArrayDeque<WorkItem>,
        inheritedPrivacy: EffectivePrivacy,
        hasOptionSelectorParent: Boolean
    ) {
        if (viewGroup is WebView) return
        val children = drawingOrderResolver.resolve(viewGroup).children
        for (i in children.indices.reversed()) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded UI-owned deque.
            stack.addLast(
                WorkItem(
                    children[i],
                    -1,
                    false,
                    touchOnly = true,
                    inheritedPrivacy = inheritedPrivacy,
                    hasOptionSelectorParent = hasOptionSelectorParent
                )
            )
        }
    }

    @UiThread
    private fun isOptionSelector(view: View): Boolean =
        view is ViewGroup && optionSelectorDetector.isOptionSelectorSafely(view, internalLogger)
}

/** Explicit view clips apply to the view and its whole subtree, regardless of clipChildren. */
@UiThread
private fun View.clipBoundsOnScreen(screenDensity: Float): CapturedBounds? {
    val clip = clipBounds ?: return null
    val location = IntArray(2)
    @Suppress("UnsafeThirdPartyFunctionCall") // The coordinate array always has two entries.
    getLocationOnScreen(location)
    val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
    val left = ((location[0].toLong() + clip.left) * inverseDensity).toLong()
    val top = ((location[1].toLong() + clip.top) * inverseDensity).toLong()
    val right = ((location[0].toLong() + clip.right) * inverseDensity).toLong()
    val bottom = ((location[1].toLong() + clip.bottom) * inverseDensity).toLong()
    return CapturedBounds(left, top, right - left, bottom - top)
}

// Android applies clipToPadding only when at least one padding edge is non-zero.
private fun ViewGroup.hasNonZeroPadding(): Boolean =
    paddingLeft != 0 || paddingTop != 0 || paddingRight != 0 || paddingBottom != 0

// The window-root outline is a common clip for every view in this window. Other outline clips,
// transforms and animations are uncertain coverage and disable culling for their descendants.
private fun View.hasStableGeometry(): Boolean =
    rotation == 0f && rotationX == 0f && rotationY == 0f && scaleX == 1f && scaleY == 1f &&
        translationX % 1f == 0f && translationY % 1f == 0f && animation == null

private fun GlobalBounds.toNormalizedBounds(screenDensity: Float): CapturedBounds {
    val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
    return CapturedBounds(
        (x * inverseDensity).toLong(),
        (y * inverseDensity).toLong(),
        (width * inverseDensity).toLong(),
        (height * inverseDensity).toLong()
    )
}

private fun GlobalBounds.toNormalizedClip(screenDensity: Float): CapturedBounds {
    val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
    val left = (x * inverseDensity).toLong()
    val top = (y * inverseDensity).toLong()
    val right = ((x + width) * inverseDensity).toLong()
    val bottom = ((y + height) * inverseDensity).toLong()
    return CapturedBounds(left, top, right - left, bottom - top)
}

private fun GlobalBounds.paddedBounds(view: View): GlobalBounds = GlobalBounds(
    x + view.paddingLeft,
    y + view.paddingTop,
    width - view.paddingLeft - view.paddingRight,
    height - view.paddingTop - view.paddingBottom
)

private fun GlobalBounds.explicitClip(view: View): GlobalBounds? = view.clipBounds?.let {
    GlobalBounds(x + it.left, y + it.top, it.right.toLong() - it.left, it.bottom.toLong() - it.top)
}

private fun View.sharesNativePaintOrder(): Boolean = this !is SurfaceView && this !is TextureView

private fun View.hasReliableGeometry(
    kind: CapturedLayerKind,
    parentIsReliable: Boolean,
    transformed: Boolean
): Boolean = !transformed && parentIsReliable &&
    (kind == CapturedLayerKind.WINDOW_ROOT || !clipToOutline) && hasStableGeometry()

private fun GlobalBounds.paddingClip(view: ViewGroup, transform: ViewTransform?): GlobalBounds? {
    if (!view.clipToPadding || !view.hasNonZeroPadding()) return null
    return transform?.mapBounds(
        GlobalBounds(
            view.paddingLeft.toLong(),
            view.paddingTop.toLong(),
            (view.width - view.paddingLeft - view.paddingRight).toLong(),
            (view.height - view.paddingTop - view.paddingBottom).toLong()
        )
    ) ?: paddedBounds(view)
}

/** Detects Compose hosts without a compile-time dependency on Compose. */
internal fun isComposeHostByClassName(view: View): Boolean {
    var type: Class<*>? = view.javaClass
    while (type != null) {
        val name = type.name
        if (name == "androidx.compose.ui.platform.ComposeView" ||
            name == "androidx.compose.ui.platform.AndroidComposeView" ||
            name == "androidx.compose.ui.platform.AbstractComposeView"
        ) {
            return true
        }
        type = type.superclass
    }
    return false
}

@UiThread
private fun captureNativeModifiers(
    view: View,
    bounds: CapturedBounds,
    density: Float,
    internalLogger: InternalLogger
): List<CapturedModifier> = listOfNotNull(
    resolveNativeClipModifier(nativeOutlineRadiusPx(view, internalLogger), bounds, density),
    resolveNativeShadowModifier(view, density)
)

/**
 * The corner radius of [view]'s outline in raw pixels, or null if [view] doesn't actually clip to a
 * rect/rounded-rect outline - either [View.getClipToOutline] is false (the outline, if any, is
 * shadow-only), the outline is an oval/path shape with no rect to read, or reading it isn't
 * supported below API 29, where [Outline.getRect]/[Outline.getRadius] became public - there is no
 * supported way to read an outline's shape back on earlier API levels. Deliberately not
 * unit-testable: `unitTests.isReturnDefaultValues = true` makes every real `Outline`/
 * [android.view.ViewOutlineProvider] method silently return its default instead of actually
 * storing/reporting a shape, so a plain JVM unit test can never observe real outline state through
 * the genuine Android classes either way - kept as a single small, directly inlined call instead of
 * introducing a seam whose only purpose would be working around that in tests.
 * [resolveNativeClipModifier], which this feeds, is the actual decision logic and is fully
 * unit-tested on its own, with a plain [Float]? input. [android.view.ViewOutlineProvider.getOutline]
 * is arbitrary app code and can throw; a failure here degrades to null, same as any other
 * unsupported outline shape, rather than aborting the capture.
 */
@Suppress("ReturnCount", "TooGenericExceptionCaught")
private fun nativeOutlineRadiusPx(view: View, internalLogger: InternalLogger): Float? {
    if (!view.clipToOutline || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val provider = view.outlineProvider ?: return null

    val outline = Outline()
    try {
        // Arbitrary third-party ViewOutlineProvider code; any throw is caught below.
        @Suppress("UnsafeThirdPartyFunctionCall")
        provider.getOutline(view, outline)
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            { "Failed to read ViewOutlineProvider outline for composition clip" },
            e
        )
        return null
    }
    val isRect = outline.getRect(Rect())
    if (!isRect) return null
    return outline.radius
}

/**
 * A shadow-only outline, or an outline with no meaningful rounding, is skipped - a plain rectangle
 * has no visual effect the existing per-wireframe ancestor-bounds crop ([com.datadog.android.internal.sessionreplay.composition.CapturedClip]) doesn't
 * already provide, matching Compose's own `GranularComposeDecomposer.resolveClipModifier`. Stateless
 * - kept out of [AndroidWindowTraversal] itself to stay within [TooManyFunctions]'s budget.
 */
@Suppress("ReturnCount") // Each guard bails out at the point it's no longer worth a Clip modifier.
internal fun resolveNativeClipModifier(
    radiusPx: Float?,
    bounds: CapturedBounds,
    density: Float
): CapturedModifier.Clip? {
    if (radiusPx == null) return null
    val radius = (radiusPx / density).toDouble()
    if (radius <= 0.0) return null
    val clampedRadius = radius.coerceAtMost(minOf(bounds.width, bounds.height) / 2.0)
    return CapturedModifier.Clip(path = roundedRectPath(bounds.width, bounds.height, clampedRadius))
}

/**
 * A closed SVG path for a [width]x[height] rectangle with all four corners rounded by [radius].
 * Coordinates are local to the layer's own rectangle, per [CapturedModifier.Clip]'s wire contract.
 * [radius] is expected to already be clamped to at most half of the shorter side by the caller.
 * Mirrors `GranularComposeDecomposer`'s own `roundedRectPath` - duplicated rather than shared since
 * the two live in separate Gradle modules with no existing shared home for this kind of small,
 * platform-specific geometry helper.
 */
private fun roundedRectPath(width: Long, height: Long, radius: Double): String {
    val w = width.toDouble()
    val h = height.toDouble()
    return "M $radius,0 " +
        "L ${w - radius},0 A $radius,$radius 0 0 1 $w,$radius " +
        "L $w,${h - radius} A $radius,$radius 0 0 1 ${w - radius},$h " +
        "L $radius,$h A $radius,$radius 0 0 1 0,${h - radius} " +
        "L 0,$radius A $radius,$radius 0 0 1 $radius,0 Z"
}

/**
 * Android's elevation shadow is a geometric light-source simulation (see hwui/Skia's ambient+spot
 * shadow renderer), not a simple offset/blur formula - there is no principled way to derive
 * [CapturedModifier.Shadow]'s offset/radius from elevation directly. This instead looks up
 * Google's own published Material Design elevation table (the same one Material Components
 * Web/MUI use to replicate Android shadows in CSS), using only its dominant "key" (umbra) layer -
 * the table's penumbra/ambient layers and its spread parameter have no equivalent in
 * [CapturedModifier.Shadow]'s single-shadow model, so this is a visual approximation, not an exact
 * reconstruction. Skipped entirely for zero/negative Z - a flat view casts no shadow. Mirrors
 * `GranularComposeDecomposer`'s own `resolveShadowModifier` - duplicated rather than shared for the
 * same reason as [roundedRectPath].
 */
private fun resolveNativeShadowModifier(view: View, density: Float): CapturedModifier.Shadow? {
    val zPx = view.elevation + view.translationZ
    if (zPx <= 0f) return null
    val zDp = zPx / density
    val level = zDp.roundToInt().coerceIn(1, MAX_ELEVATION_DP)
    val (offsetYDp, blurDp) = MATERIAL_KEY_SHADOW_DP[level]
    return CapturedModifier.Shadow(
        color = shadowColorHex(nativeSpotShadowColor(view)),
        offsetX = 0.0,
        offsetY = offsetYDp,
        radius = blurDp
    )
}

/** [View.getOutlineSpotShadowColor] requires API 28 - defaults to black below that, matching the platform's own default. */
private fun nativeSpotShadowColor(view: View): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) view.outlineSpotShadowColor else Color.BLACK

private fun shadowColorHex(colorInt: Int): String {
    // Color.alpha is pure bit-shift arithmetic (`color ushr 24`); it never throws.
    @Suppress("UnsafeThirdPartyFunctionCall")
    val existingAlpha = Color.alpha(colorInt)
    val alpha = (existingAlpha * SHADOW_KEY_OPACITY).roundToInt()
    return DefaultColorStringFormatter.formatColorAndAlphaAsHexString(colorInt, alpha)
}

private const val SHADOW_KEY_OPACITY = 0.2
private const val MAX_ELEVATION_DP = 24

/**
 * (offsetY, blurRadius) in dp per elevation level, 0-24 - the "key"/umbra layer only, from Google's
 * own Material Design elevation table (values from
 * https://github.com/material-components/material-components-web/blob/master/packages/mdc-elevation/_variables.scss,
 * also used by Material Components Web/MUI to replicate these shadows in CSS). offsetX is always 0
 * at every level; the table's spread parameter has no equivalent in [CapturedModifier.Shadow] and
 * is dropped. Index 0 is unused - [resolveNativeShadowModifier] never looks up a non-positive Z.
 */
@Suppress("MagicNumber") // Every value here is a literal reference-table entry, not an arbitrary constant.
private val MATERIAL_KEY_SHADOW_DP = listOf(
    0.0 to 0.0,
    2.0 to 1.0,
    3.0 to 1.0,
    3.0 to 3.0,
    2.0 to 4.0,
    3.0 to 5.0,
    3.0 to 5.0,
    4.0 to 5.0,
    5.0 to 5.0,
    5.0 to 6.0,
    6.0 to 6.0,
    6.0 to 7.0,
    7.0 to 8.0,
    7.0 to 8.0,
    7.0 to 9.0,
    8.0 to 9.0,
    8.0 to 10.0,
    8.0 to 11.0,
    9.0 to 11.0,
    9.0 to 12.0,
    10.0 to 13.0,
    10.0 to 13.0,
    10.0 to 14.0,
    11.0 to 14.0,
    11.0 to 15.0
)

private data class WorkItem(
    val view: View,
    val parentIndex: Int,
    val clipToBounds: Boolean,
    val touchOnly: Boolean = false,
    val isGeometryReliable: Boolean = true,
    val expectedParent: ViewParent? = view.parent,
    val inheritedPrivacy: EffectivePrivacy = EffectivePrivacy(ImagePrivacy.MASK_ALL, TextAndInputPrivacy.MASK_ALL),
    val inheritedScale: Float = 1f,
    val nodePath: List<String> = emptyList(),
    val typeIndex: Int = 0,
    val hasOptionSelectorParent: Boolean = false
)

/** An unavailable custom detector must conservatively mask selectable inputs. */
@UiThread
@Suppress("TooGenericExceptionCaught") // Optional extensions must not crash application drawing.
internal fun OptionSelectorDetector.isOptionSelectorSafely(view: ViewGroup, internalLogger: InternalLogger): Boolean =
    try {
        isOptionSelector(view)
    } catch (e: Exception) {
        logSelectorFailure(internalLogger, e)
        true
    } catch (e: LinkageError) {
        logSelectorFailure(internalLogger, e)
        true
    }

private fun logSelectorFailure(internalLogger: InternalLogger, error: Throwable) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.TELEMETRY,
        { "Unable to detect an option selector" },
        error,
        onlyOnce = true
    )
}

private class CopyState(
    val density: Float,
    val windowIdentity: CapturedIdentity,
    val identityFactory: CapturedIdentityFactory,
    val rootPrivacy: EffectivePrivacy,
    val rootIdentity: CapturedIdentity = windowIdentity,
    val rootKind: CapturedLayerKind = CapturedLayerKind.WINDOW_ROOT,
    val visited: IdentityHashMap<View, Boolean> = IdentityHashMap(),
    val heatmapContext: HeatmapIdentifierResolver.TraversalContext? = null,
    val rootHasOptionSelectorParent: Boolean = false
) {
    var isDrawingOrderReliable = true
    var requiresTouchPrivacyCapture = false
    val locationOnScreen = IntArray(2)
    val views = mutableListOf<CapturedNativeView>()
    val touchOverrideAreas = mutableMapOf<Rect, TouchPrivacy>()
    val pendingPixelCaptures = mutableListOf<PendingPixelCapture>()
    val pendingMappings = mutableListOf<PendingViewMapping>()

    fun collectCustomTouchPrivacy(areas: Map<Rect, TouchPrivacy>?) {
        requiresTouchPrivacyCapture = true
        val overrides = areas ?: mapOf(
            Rect(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE) to TouchPrivacy.HIDE
        )
        overrides.forEach { (rect, privacy) -> touchOverrideAreas.putTouchOverrideArea(rect, privacy) }
    }
}

@UiThread
@Suppress("TooGenericExceptionCaught")
private fun readComposeTouchPrivacy(
    view: View,
    context: CaptureGenerationContext,
    composeHostDecomposer: CompositionHostDecomposer?,
    internalLogger: InternalLogger
): Map<Rect, TouchPrivacy>? = try {
    composeHostDecomposer?.readTouchPrivacy(view, context::shouldContinue)
} catch (e: Exception) {
    logDecompositionFailure(internalLogger, e)
    null
} catch (e: LinkageError) {
    logDecompositionFailure(internalLogger, e)
    null
}

private fun logDecompositionFailure(internalLogger: InternalLogger, error: Throwable) = internalLogger.log(
    InternalLogger.Level.ERROR,
    InternalLogger.Target.TELEMETRY,
    { "Unable to decompose a composition host" },
    error,
    onlyOnce = true
)

private fun requiresRasterCapture(
    transformed: Boolean,
    transform: ViewTransform?,
    kind: CapturedLayerKind
): Boolean = transformed && (transform?.uniformScale == null || kind == CapturedLayerKind.COMPOSE_HOST)

/** Custom touch rules may exist inside hidden or visually occluded native subtrees too. */
@UiThread
private fun captureTouchOnlyCustomMapper(
    item: WorkItem,
    state: CopyState,
    context: CaptureGenerationContext,
    registry: CapturedViewMapperRegistry,
    internalLogger: InternalLogger
) {
    val mapper = registry.resolve(item.view)
    if (mapper !is LegacyCapturedViewMapper<*> || !mapper.collectTouchPrivacy) return
    @Suppress("UNCHECKED_CAST") // The registry checked that this mapper supports the view's class.
    val customMapper = mapper as LegacyCapturedViewMapper<View>
    val privacy = resolveEffectivePrivacy(item.view, item.inheritedPrivacy, internalLogger)
    customMapper.captureTouchPrivacy(
        item.view,
        CapturedMappingContext(
            state.identityFactory,
            state.windowIdentity,
            state.density,
            imagePrivacy = privacy.imagePrivacy,
            textAndInputPrivacy = privacy.textAndInputPrivacy,
            hasOptionSelectorParent = item.hasOptionSelectorParent,
            shouldContinue = context::shouldContinue,
            customTouchPrivacySink = state::collectCustomTouchPrivacy
        )
    )
}
