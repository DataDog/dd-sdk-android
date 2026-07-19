/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.processor.WireframeBounds
import com.datadog.android.sessionreplay.internal.processor.WireframeUtils
import com.datadog.android.sessionreplay.internal.processor.copy
import com.datadog.android.sessionreplay.internal.recorder.mapper.HiddenViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCaptureFallbackMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.QueueStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.HostViewDecomposeRequest
import com.datadog.android.sessionreplay.recorder.HostViewDecomposer
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.NoOpInteropViewCallback
import com.datadog.android.sessionreplay.recorder.PixelCaptureCallback
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * Builds the composition tree for Session Replay's experimental pixel-copy recording pipeline,
 * gated by `pixelCaptureEnabled` (see [SessionReplayRecorder]).
 *
 * This is a **wholly separate traversal** from the default pipeline ([TreeViewTraversal] /
 * [SnapshotProducer] and its full mapper chain), which this class never touches and which
 * behaves exactly as it does today when the flag is off. When the flag is on, this builder
 * replaces that traversal entirely for the views it walks, using only three leaf mappers —
 * deliberately not the default pipeline's full built-in + extension mapper chain:
 * - Every [TextView] is mapped via [textViewMapper], so text stays crisp/selectable rather than
 *   becoming a pixel capture.
 * - Every [WebView] is mapped via [webViewMapper], the same dedicated mapper the default pipeline
 *   uses — a pixel capture would silently come back blank, since WebView's content composites
 *   through a path [android.view.View.draw] never touches (see [PixelCapture]'s own
 *   `containsHardwareSurface` doc). Always treated as a leaf regardless of [ViewGroup.getChildCount]
 *   for the same reason as [isComposeHostView] below — WebView is itself a [ViewGroup], but its
 *   real content isn't rendered through any child the traversal could recurse into.
 * - A leaf whose exact runtime class is [isSimpleContainerView] — [View] itself, or one of a
 *   short allowlist of stock layout classes ([FrameLayout]/[LinearLayout]/[RelativeLayout]) —
 *   tries [viewWireframeMapper] before a pixel capture: none of these override [View.onDraw] to
 *   paint anything beyond their own background, so *when that background resolves to a shape*
 *   (a plain color, or another [android.graphics.drawable.Drawable] reducible to one), it already
 *   IS the view's entire visual content — capturing a bitmap for it would only reproduce the same
 *   flat color at real [View.draw] cost instead of for free. Only used when non-empty, though: an
 *   image or vector background can't be reduced to a color (see [mapLeafView]'s doc), and
 *   accepting an empty result there at face value would silently blank out real content a pixel
 *   capture could have shown correctly — so this only ever *skips* a pixel capture, never
 *   *replaces* one with nothing. Deliberately an exact class check
 *   (`view.javaClass == FrameLayout::class.java`, not `is FrameLayout`): a subclass of any of
 *   these could override `onDraw` to paint real custom content of its own — silently swapping
 *   that content for just its background would be a correctness regression, not an optimization,
 *   so only the exact stock classes qualify.
 * - Every other leaf view — including Jetpack Compose content (`ComposeView`/`AndroidComposeView`
 *   are forced onto this leaf path by [isComposeHostView] regardless of their child count — they
 *   always carry an internal `AndroidViewsHandler` child even when nothing is drawn through it,
 *   so child count alone can't be used to tell whether one is a real container) and Material's
 *   `TextInputLayout` (forced onto this leaf path by [isMaterialTextInputLayout] for a different
 *   reason: its own box outline/floating label are real content, but neither is representable by
 *   recursing into its single `EditText` child — the box background drawable actually gets
 *   attached to that *child*, not the layout, and the floating label is canvas-drawn directly in
 *   `TextInputLayout.draw()` with its expanded/collapsed state decided by a package-private
 *   method this module has no compile-time visibility into) and `NavigationBarItemView`/
 *   `BottomNavigationItemView` (forced onto this leaf path by [isNavigationBarItemView]: a
 *   `BadgeDrawable` — e.g. from `BottomNavigationView.getOrCreateBadge()` — attaches to the icon's
 *   wrapping container via [View.getOverlay], which only ever paints as the last step of that
 *   exact view's own `draw()` — something no container in this pipeline ever gets, see
 *   [isNavigationBarItemView]'s doc for the full reasoning) — is captured via
 *   [pixelCaptureFallbackMapper]. This pipeline never handles Compose interop views, hence
 *   [NoOpInteropViewCallback].
 * - Every container (a [ViewGroup] with children) becomes its own [MobileSegment.CompositionLayer]
 *   so container-level rendering effects — currently just [View.getAlpha] — can be applied once
 *   to the whole group on the backend, instead of being lost the way it is in the default
 *   pipeline today (a container's alpha there only ever affects its own background wireframe,
 *   see [com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper] — it
 *   never reaches descendants).
 *
 * Per-view privacy overrides (`View.setSessionReplayImagePrivacy`/`setSessionReplayTextAndInputPrivacy`/
 * `setSessionReplayHidden`/`setSessionReplayTouchPrivacy`) are resolved fresh for every view via
 * [resolveViewPrivacyOverrides]/[isViewHiddenForSessionReplay]/[resolveTouchPrivacyOverride] in
 * [childReferences] (and once per root window in [build]) — this pipeline used to build one
 * [MappingContext] from the app-wide config and reuse it unchanged for the whole tree, silently
 * ignoring every per-view override for as long as pixel capture was enabled; see those functions'
 * docs for why this is shared with [SnapshotProducer]/[TreeViewTraversal] rather than a second,
 * separately-maintained copy.
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
    private val webViewMapper: WebViewWireframeMapper,
    private val viewWireframeMapper: WireframeMapper<View>,
    private val pixelCaptureFallbackMapper: PixelCaptureFallbackMapper,
    private val hiddenViewMapper: HiddenViewMapper,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val imageWireframeHelper: ImageWireframeHelper,
    private val pixelCaptureCallback: PixelCaptureCallback? = null,
    private val hostViewDecomposer: HostViewDecomposer? = null,
    private val viewUtilsInternal: ViewUtilsInternal = ViewUtilsInternal(),
    private val wireframeUtils: WireframeUtils = WireframeUtils()
) {

    private val layers = mutableListOf<MobileSegment.CompositionLayer>()
    private val wireframes = mutableListOf<MobileSegment.Wireframe>()

    /**
     * Builds the composition tree and wireframe list spanning every currently-shown window in
     * [rootViews] — mirrors [SnapshotProducer.produce]'s per-window signature, building its own
     * [MappingContext] the same way (this pipeline never handles Compose interop views, hence
     * [NoOpInteropViewCallback]). Views failing [ViewUtilsInternal.isNotVisible] are dropped
     * before building. [Output.compositionTree] is null only if no window produced a layer (e.g.
     * all filtered out, or ids couldn't be resolved) — every other node degrades gracefully
     * (falls back to a flat pixel capture of that subtree, see [childReferences]) instead of
     * failing the build. One window's layer becomes the tree's root directly, unchanged from
     * before; more than one are wrapped under a synthetic full-screen root (see
     * [buildSyntheticRootLayer]) so the tree always has exactly one root regardless of window
     * count.
     */
    @UiThread
    fun build(
        rootViews: List<View>,
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
            pixelCaptureCallback = pixelCaptureCallback
        )
        val asyncJobStatusCallback = QueueStatusCallback(recordedDataQueueRefs)

        val visibleRootViews = rootViews.filterNot { viewUtilsInternal.isNotVisible(it) }
        val windowLayers = visibleRootViews.mapNotNull {
            // Resolved per root window for the same reason childReferences resolves it per
            // child — a root is vanishingly unlikely to carry its own override in practice, but
            // this keeps every view in the tree, root or not, subject to the same rule.
            resolveTouchPrivacyOverride(it, mappingContext, internalLogger)
            val localMappingContext = resolveViewPrivacyOverrides(it, mappingContext, internalLogger)
            buildLayer(it, localMappingContext, asyncJobStatusCallback, internalLogger, ancestorBounds = emptyList())
        }

        val rootLayer = when (windowLayers.size) {
            0 -> null
            1 -> windowLayers.single()
            else -> {
                // These are no longer "the root" of their own tree, so they must join the flat
                // layers list alongside every nested (non-root) layer already added there.
                layers.addAll(windowLayers)
                buildSyntheticRootLayer(windowLayers, systemInformation.screenBounds)
            }
        }
        val compositionTree = rootLayer?.let {
            MobileSegment.CompositionTree(root = it, layers = layers.toList().ifEmpty { null })
        }
        return Output(compositionTree, wireframes.toList())
    }

    /**
     * Wraps [windowLayers] (multiple currently-shown windows) under one synthetic layer spanning
     * the full screen, so [MobileSegment.CompositionTree] always has exactly one root regardless
     * of window count. [screenBounds] is already density-normalized — the same units every other
     * layer/wireframe in this tree uses — so it's used as-is, with no backing view of its own;
     * [SYNTHETIC_ROOT_LAYER_ID] is a fixed sentinel, never resolved via [viewIdentifierResolver].
     */
    private fun buildSyntheticRootLayer(
        windowLayers: List<MobileSegment.CompositionLayer>,
        screenBounds: GlobalBounds
    ): MobileSegment.CompositionLayer = MobileSegment.CompositionLayer(
        id = SYNTHETIC_ROOT_LAYER_ID,
        x = screenBounds.x,
        y = screenBounds.y,
        width = screenBounds.width,
        height = screenBounds.height,
        children = windowLayers.map { MobileSegment.CompositionLayerChild(id = it.id, type = LAYER_CHILD_TYPE) }
    )

    /**
     * Always builds a layer for [view] — callers decide whether a view warrants one.
     * [ancestorBounds] is every container's bounds from the root window down to (not including)
     * [view] itself — see [childReferences]'s doc for why this is threaded through instead of
     * relying on [WireframeUtils]'s `Node`/`parents`-based clipping, which this pipeline has no
     * equivalent of.
     */
    private fun buildLayer(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger,
        ancestorBounds: List<WireframeBounds>
    ): MobileSegment.CompositionLayer? {
        val id = viewIdentifierResolver.resolveChildUniqueIdentifier(view, COMPOSITION_LAYER_KEY_NAME)
            ?: return null
        val density = mappingContext.systemInformation.screenDensity
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)

        // A container only ever contributed its children here — its own `view.background` was
        // silently dropped, unlike a leaf (which goes through mapLeafView's viewWireframeMapper/
        // pixelCaptureFallbackMapper). Reusing viewWireframeMapper here closes that gap: it
        // already resolves a color (and, since BaseWireframeMapper's corner-radius fix, a corner
        // radius) from view.background, exactly as it would if this view had no children and hit
        // isSimpleContainerView in mapLeafView instead. Prepended so it renders as a backdrop
        // behind the real children, not on top of them.
        val backgroundWireframes = viewWireframeMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
            .clippedAgainst(ancestorBounds)
        wireframes.addAll(backgroundWireframes)
        val backgroundChildren = backgroundWireframes.map {
            MobileSegment.CompositionLayerChild(id = it.id(), type = WIREFRAME_CHILD_TYPE)
        }

        // [view] becomes an ancestor constraint for its own children from here on — appended,
        // never replacing what came before, so a grandchild is still clipped against every
        // container above it, not just its immediate parent.
        val childAncestorBounds = ancestorBounds + bounds.toWireframeBounds()

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
            children = backgroundChildren +
                buildChildren(view, mappingContext, asyncJobStatusCallback, internalLogger, childAncestorBounds)
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
        internalLogger: InternalLogger,
        ancestorBounds: List<WireframeBounds>
    ): List<MobileSegment.CompositionLayerChild> {
        if (view !is ViewGroup || view.childCount == 0) return emptyList()

        val children = mutableListOf<MobileSegment.CompositionLayerChild>()
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) ?: continue
            if (viewUtilsInternal.isNotVisible(child) || viewUtilsInternal.isSystemNoise(child)) continue
            children.addAll(
                childReferences(child, mappingContext, asyncJobStatusCallback, internalLogger, ancestorBounds)
            )
        }
        return children
    }

    /**
     * Resolves how [view] should be referenced from its parent's children list. [mappingContext]
     * is resolved for [view]'s own privacy tag overrides first — see [resolveViewPrivacyOverrides] —
     * before any of the checks below, and that resolved context (not the one passed in) is what
     * every branch actually uses. [resolveTouchPrivacyOverride] runs unconditionally, before any
     * of those checks — it registers a screen-space area in a separate, shared accumulator rather
     * than affecting how [view] itself gets mapped, so it applies the same way regardless of
     * which branch below [view] ends up taking:
     * - [isViewHiddenForSessionReplay] — replaced with a single "Hidden"-labeled placeholder via
     *   [hiddenViewMapper], its children never visited at all, mirroring [TreeViewTraversal]'s
     *   own handling of the same tag in the default pipeline.
     * - A container (has its own children) — build its own layer and reference it by id.
     * - A Compose host — decompose it via [hostViewDecomposer] into its own layer, if a
     *   decomposer is wired in and can decompose it (see [buildComposeLayer]).
     * - Otherwise — or if a layer/decomposition could not be built (missing/duplicate id,
     *   no decomposer, decomposition failed) — map it as a leaf (one or more wireframes; a leaf
     *   mapper can produce more than one, e.g. a background plus content) and reference each by
     *   id. A container that falls back this way is captured as a single flat pixel snapshot of
     *   the whole subtree instead of a group — a degraded but still-correct result, never a
     *   dropped one.
     *
     * Every wireframe this produces is clipped against [ancestorBounds] — every container's
     * bounds from the root window down to [view]'s own parent — via [clippedAgainst]. This
     * pipeline builds a `CompositionLayer`/`CompositionLayerChild` tree, not the default
     * pipeline's `Node`/`parents`, so it has no equivalent of [WireframeUtils.resolveWireframeClip]
     * running as a post-processing pass over the whole tree; without this, a wireframe that
     * scrolls outside its container's bounds keeps its full, unclipped on-screen geometry and
     * renders straight over whatever's drawn above that container (e.g. a fixed toolbar) instead
     * of being visually cropped by it.
     */
    private fun childReferences(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger,
        ancestorBounds: List<WireframeBounds>
    ): List<MobileSegment.CompositionLayerChild> {
        resolveTouchPrivacyOverride(view, mappingContext, internalLogger)
        val localMappingContext = resolveViewPrivacyOverrides(view, mappingContext, internalLogger)

        if (isViewHiddenForSessionReplay(view)) {
            val hiddenWireframes = hiddenViewMapper.map(
                view,
                localMappingContext,
                asyncJobStatusCallback,
                internalLogger
            ).clippedAgainst(ancestorBounds)
            wireframes.addAll(hiddenWireframes)
            return hiddenWireframes.map {
                MobileSegment.CompositionLayerChild(id = it.id(), type = WIREFRAME_CHILD_TYPE)
            }
        }

        if (view is ViewGroup &&
            view.childCount > 0 &&
            !isComposeHostView(view) &&
            view !is WebView &&
            !isMaterialTextInputLayout(view) &&
            !isNavigationBarItemView(view)
        ) {
            val layer = buildLayer(view, localMappingContext, asyncJobStatusCallback, internalLogger, ancestorBounds)
            if (layer != null) {
                layers.add(layer)
                return listOf(MobileSegment.CompositionLayerChild(id = layer.id, type = LAYER_CHILD_TYPE))
            }
        }

        if (isComposeHostView(view)) {
            val composeLayer =
                buildComposeLayer(view, localMappingContext, asyncJobStatusCallback, internalLogger, ancestorBounds)
            if (composeLayer != null) {
                layers.add(composeLayer)
                return listOf(MobileSegment.CompositionLayerChild(id = composeLayer.id, type = LAYER_CHILD_TYPE))
            }
        }

        val leafWireframes = mapLeafView(view, localMappingContext, asyncJobStatusCallback, internalLogger)
            .clippedAgainst(ancestorBounds)
        wireframes.addAll(leafWireframes)
        return leafWireframes.map {
            MobileSegment.CompositionLayerChild(id = it.id(), type = WIREFRAME_CHILD_TYPE)
        }
    }

    /**
     * Decomposes a Compose host [view] into individual composable-level regions via
     * [hostViewDecomposer], instead of falling through to [mapLeafView]'s whole-view pixel
     * capture. Returns null (falling through to that whole-view capture, unchanged from before
     * this extension point existed) when no decomposer is wired in, [HostViewDecomposer.canDecompose]
     * declines, an id couldn't be resolved, or [HostViewDecomposer.decompose] itself fails.
     *
     * [ancestorBounds] is only threaded through to [nativeViewHandoff] here — any native
     * View embedded in [view]'s composable tree still gets clipped correctly against every
     * ancestor including [view] itself. The decomposed composables' own wireframes/nested layers
     * (`result.wireframes`/`result.nestedLayers`) come from [decomposer]'s own internal structure
     * and are **not** clipped against [ancestorBounds] by this pipeline — doing so would mean
     * threading ancestor bounds through [HostViewDecomposeRequest] and having the decomposer
     * itself apply them, a change to that decomposer, not this class.
     */
    private fun buildComposeLayer(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger,
        ancestorBounds: List<WireframeBounds>
    ): MobileSegment.CompositionLayer? {
        val decomposer = hostViewDecomposer ?: return null
        if (!decomposer.canDecompose(view)) return null

        val id = viewIdentifierResolver.resolveChildUniqueIdentifier(view, COMPOSITION_LAYER_KEY_NAME)
            ?: return null
        val density = mappingContext.systemInformation.screenDensity
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)
        val childAncestorBounds = ancestorBounds + bounds.toWireframeBounds()

        // Null when the view reports no visible area at all (e.g. fully scrolled off-screen) —
        // the decomposer treats that the same as "no clip info available" and skips clipping
        // rather than misreading it as "nothing is visible, clip everything".
        val visibleRect = Rect().takeIf { view.getGlobalVisibleRect(it) }

        val request = HostViewDecomposeRequest(
            mappingContext = mappingContext,
            asyncJobStatusCallback = asyncJobStatusCallback,
            internalLogger = internalLogger,
            pixelCaptureCallback = pixelCaptureCallback,
            nativeViewHandoff = { nativeView ->
                childReferences(nativeView, mappingContext, asyncJobStatusCallback, internalLogger, childAncestorBounds)
            },
            hostVisibleRectPx = visibleRect
        )
        val result = decomposer.decompose(view, request) ?: return null

        layers.addAll(result.nestedLayers)
        wireframes.addAll(result.wireframes)

        return MobileSegment.CompositionLayer(
            id = id,
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            children = result.rootChildren,
            modifiers = opacityModifiers(view.alpha)
        )
    }

    /**
     * `AndroidComposeView`/`ComposeView` always carry at least one internal child
     * (`AndroidViewsHandler`, used to host Compose/View interop) even when nothing is drawn
     * through it — so [ViewGroup.getChildCount] is never a reliable "is this a real container"
     * signal for them the way it is for ordinary layout ViewGroups. Treating them as a container
     * would recurse into that internal child (empty and filtered out as not-visible) instead of
     * capturing the Compose-rendered content the host view itself draws — the pipeline has no
     * compile-time dependency on Compose (this module doesn't depend on it), hence the by-name
     * check instead of an `is AndroidComposeView` check.
     */
    private fun isComposeHostView(view: View): Boolean {
        val className = view.javaClass.name
        return className == "androidx.compose.ui.platform.AndroidComposeView" ||
            className == "androidx.compose.ui.platform.ComposeView"
    }

    /**
     * See the class doc's note on `TextInputLayout` for why this is forced onto the leaf/
     * pixel-capture path rather than being treated as an ordinary container. A by-name check
     * rather than `is TextInputLayout`, matching [isComposeHostView]'s approach, since this
     * module has no compile-time dependency on the Material Components library — an app-level
     * subclass of `TextInputLayout` won't match this exact-name check, same limitation as
     * [isComposeHostView] has for Compose subclasses.
     */
    private fun isMaterialTextInputLayout(view: View): Boolean {
        return view.javaClass.name == "com.google.android.material.textfield.TextInputLayout"
    }

    /**
     * `NavigationBarItemView` (the shared item view backing both `BottomNavigationView` and
     * `NavigationRailView`, and `BottomNavigationItemView` on older Material versions before the
     * two were unified) is forced onto the leaf/pixel-capture path for the same class of reason
     * as [isMaterialTextInputLayout]: real content that recursing into its children can't
     * reproduce. Here that's [com.google.android.material.badge.BadgeDrawable] — attached via
     * `BadgeUtils.attachBadgeDrawable` to the icon's wrapping `FrameLayout` (so the badge can
     * overflow that container's own bounds), which means it's added to that container's
     * [View.getOverlay], not as a real child view. An overlay-drawn [android.graphics.drawable.Drawable]
     * only ever gets painted as the last step of that exact view's own `draw()` call — and a
     * *container* in this pipeline never gets one (see [buildLayer]'s doc: only a background-color
     * wireframe, plus recursing into children, each captured independently) — so the badge would
     * otherwise never be captured by anything: not the icon container (never drawn as a whole),
     * and not the icon `ImageView` itself (the badge isn't attached there). Capturing the whole
     * item view as one leaf via [pixelCaptureFallbackMapper] naturally includes it: `ViewGroup`'s
     * child-dispatch draw already calls each child's own `draw()` (overlay included), so the
     * badge comes along for free in the same pass as the icon/label/active-indicator pill. A
     * by-name check, matching [isComposeHostView]/[isMaterialTextInputLayout]'s approach, since
     * this module has no compile-time dependency on Material Components.
     */
    private fun isNavigationBarItemView(view: View): Boolean {
        val className = view.javaClass.name
        return className == "com.google.android.material.navigation.NavigationBarItemView" ||
            className == "com.google.android.material.bottomnavigation.BottomNavigationItemView"
    }

    /**
     * [TextView]s go through [textViewMapper] so text stays crisp; [WebView]s go through
     * [webViewMapper] since a pixel capture can't see their content (see the class doc);
     * [isSimpleContainerView] leaves try [viewWireframeMapper] first — but only *use* that result
     * if it actually resolved a shape. [viewWireframeMapper] returns an empty list for a
     * background it can't reduce to a color (notably [android.graphics.drawable.BitmapDrawable]
     * and [android.graphics.drawable.VectorDrawable] — see
     * [com.datadog.android.sessionreplay.internal.recorder.mapper.AndroidMDrawableToColorMapper])
     * or for no background at all — accepting that empty result at face value would silently
     * blank out a real image where a pixel capture could have shown it correctly, trading a
     * genuine correctness regression for an efficiency win that only applies to *some* leaves in
     * this allowlist. Falling through to [pixelCaptureFallbackMapper] on empty costs one
     * discarded, side-effect-free call to [viewWireframeMapper] (it never touches
     * [asyncJobStatusCallback]) for the leaves where it doesn't pan out, in exchange for never
     * being worse than pixel-capturing everything the way this pipeline did before. Everything
     * else falls straight to [pixelCaptureFallbackMapper].
     */
    private fun mapLeafView(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        if (view is TextView) {
            return textViewMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }
        if (view is WebView) {
            return webViewMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }
        if (isSimpleContainerView(view)) {
            val shapeWireframes = viewWireframeMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
            if (shapeWireframes.isNotEmpty()) return shapeWireframes
        }
        return pixelCaptureFallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
    }

    /**
     * True for [View] itself and a short allowlist of stock layout classes that never override
     * [View.onDraw] to paint anything beyond their own background — see the class doc for why
     * this must be an exact-class check, not `is`, and why a false positive here would be a
     * correctness regression rather than just a missed optimization.
     */
    private fun isSimpleContainerView(view: View): Boolean {
        return SIMPLE_CONTAINER_CLASSES.contains(view.javaClass)
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

    /** [GlobalBounds] (x/y/width/height) in [WireframeBounds]'s left/top/right/bottom shape. */
    private fun GlobalBounds.toWireframeBounds(): WireframeBounds = WireframeBounds(
        left = x,
        top = y,
        right = x + width,
        bottom = y + height,
        width = width,
        height = height
    )

    /**
     * Clips every wireframe in this list against [ancestorBounds] — see [childReferences]'s doc
     * for why this pipeline has to do this itself instead of relying on
     * [WireframeUtils.resolveWireframeClip]'s usual `Node`/`parents`-based post-processing pass.
     */
    private fun List<MobileSegment.Wireframe>.clippedAgainst(
        ancestorBounds: List<WireframeBounds>
    ): List<MobileSegment.Wireframe> {
        if (ancestorBounds.isEmpty()) return this
        return map { it.copy(clip = wireframeUtils.resolveClipFromAncestorBounds(it, ancestorBounds)) }
    }

    internal data class Output(
        val compositionTree: MobileSegment.CompositionTree?,
        val wireframes: List<MobileSegment.Wireframe>
    )

    companion object {
        internal const val COMPOSITION_LAYER_KEY_NAME = "composition_layer"
        private val WIREFRAME_CHILD_TYPE = MobileSegment.Type.WIREFRAME
        private val LAYER_CHILD_TYPE = MobileSegment.Type.LAYER

        /**
         * Sentinel id for the synthetic multi-window root layer — has no backing view, so it
         * can't go through [ViewIdentifierResolver]. Every id actually produced in this tree
         * comes from either `resolveChildUniqueIdentifier` (`SecureRandom.nextInt().toLong()`)
         * or `resolveViewId` (`System.identityHashCode(view).toLong()`) — both bounded to the
         * full *signed 32-bit* range. This value sits just outside that range (unreachable from
         * either producer, positive or negative) while staying well within JS's safe-integer
         * range (`2^53-1`) — unlike e.g. `Long.MAX_VALUE` — since ids round-trip through the
         * JSON/JS-based player downstream.
         */
        internal const val SYNTHETIC_ROOT_LAYER_ID = Int.MAX_VALUE.toLong() + 1L

        /** See [isSimpleContainerView] — kept short and explicit rather than reflection-based. */
        private val SIMPLE_CONTAINER_CLASSES: Set<Class<*>> = setOf(
            View::class.java,
            FrameLayout::class.java,
            LinearLayout::class.java,
            RelativeLayout::class.java
        )
    }
}
