/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package com.datadog.android.sessionreplay.compose.internal.tree

import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.InteroperableComposeUiNode
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.utils.BackgroundResolver
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.HostViewDecomposeRequest
import com.datadog.android.sessionreplay.recorder.HostViewDecomposeResult
import com.datadog.android.sessionreplay.recorder.HostViewDecomposer
import com.datadog.android.sessionreplay.recorder.PixelCaptureEligibility
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import kotlin.math.max

/**
 * Walks a Compose host's real [LayoutNode] tree via public APIs (see the class-level notes on
 * each accessor below for exactly which ones need
 * `@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")` vs `@OptIn(InternalComposeUiApi::class)`
 * vs nothing at all). A node that is itself a text node peels off into a real
 * [MobileSegment.Wireframe.TextWireframe] (Phase 2). A node with its own drawing effect
 * (background/clip/custom draw — see [hasDrawingEffect]) first tries to resolve as a flat
 * [MobileSegment.Wireframe.ShapeWireframe] — a plain background color (optionally rounded) is
 * read via reflection (see [resolveSimpleShapeWireframe]), mirroring the native View pipeline's
 * `ViewWireframeMapper`/`PixelCaptureFallbackMapper` split — and only falls back to pixel-capturing
 * the whole subtree as one atomic unit when that's not resolvable (a real custom draw modifier, or
 * an unsupported `Brush`), which keeps visuals correct at the cost of that subtree's own text not
 * being masking-aware yet (see the "known gap" note on [hasDrawingEffect]). Pure structural
 * containers (no drawing effect, no children left) fall back to one atomic pixel capture per leaf
 * (Phase 1's original, simplest-possible fallback).
 */
internal class DefaultComposeCompositionWalker : HostViewDecomposer {

    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val reflectionUtils = ReflectionUtils()

    // Matches DEFAULT_FONT_FAMILY ("roboto, sans-serif") — used to re-measure text width when a
    // node's real font is being substituted for it, see widthPaddedForFontFallback.
    private val fallbackFontPaint = TextPaint().apply { typeface = Typeface.SANS_SERIF }

    // `innerBoundsOf` is only used by `BackgroundResolver.resolveBackgroundInfo`, which this class
    // doesn't call — only `resolveBackgroundColor`/`resolveBackgroundShape`, neither of which
    // invoke it — so this is a harmless placeholder, never actually invoked.
    private val backgroundResolver = BackgroundResolver(
        reflectionUtils = reflectionUtils,
        innerBoundsOf = { GlobalBounds(x = 0, y = 0, width = 0, height = 0) }
    )

    override fun canDecompose(view: View): Boolean = resolveOwner(view) is Owner

    override fun decompose(view: View, request: HostViewDecomposeRequest): HostViewDecomposeResult? {
        val owner = resolveOwner(view)
        if (owner !is Owner) return null
        val root = try {
            owner.root
        } catch (e: Exception) {
            request.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "HostViewDecomposer: failed to resolve root LayoutNode" },
                e
            )
            return null
        }

        if (isLikelyMidTransition(root, request)) return null

        val rootChildren = mutableListOf<MobileSegment.CompositionLayerChild>()
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
        val nestedLayers = mutableListOf<MobileSegment.CompositionLayer>()
        var captureCount = 0

        val semanticsById = mutableMapOf<Int, SemanticsNode>()
        (owner as? AndroidComposeView)?.semanticsOwner?.unmergedRootSemanticsNode?.let {
            collectSemanticsNodes(it, semanticsById)
        }

        // ancestorOpaqueColorHex: the nearest fully-opaque ancestor shape wireframe's color, or
        // null if there isn't one (or it's translucent). A node with a `.clip(shape)` modifier
        // clips *everything drawn inside it* to that shape on the real screen — which we don't
        // and (with today's rectangle-only WireframeClip) largely can't reproduce generally. But
        // when a descendant shape is fully opaque and the exact same color as that ancestor, it's
        // visually redundant regardless of its own (possibly mismatched) corner radius — skipping
        // it avoids exactly that mismatch (e.g. a button's inner content/state-layer container
        // carrying a squarer radius than the outer pill it's invisibly clipped within) without
        // needing real clip propagation. See resolveShapeCornerRadius's doc for the underlying gap.
        fun walk(node: LayoutNode, effectiveAlpha: Float, ancestorOpaqueColorHex: String?) {
            if (!node.isPlaced || !node.isAttached) return

            val interopView = (node as? InteroperableComposeUiNode)?.getInteropView()
            if (interopView != null) {
                rootChildren.addAll(request.nativeViewHandoff(interopView))
                return
            }

            val nodeAlpha = effectiveAlpha * nodeOwnAlpha(node)

            val ownSemantics = semanticsById[node.semanticsId]?.config ?: SemanticsConfiguration()
            val textContent = resolveTextContent(ownSemantics)
            if (textContent != null) {
                val resolvedLayoutResult = resolveTextLayoutResult(ownSemantics)
                val fontFamily = resolvedLayoutResult?.layoutInput?.style?.fontFamily
                val isCustomFont = fontFamily != null && fontFamily !is GenericFontFamily

                // A custom font (as opposed to a GenericFontFamily) has no equivalent the player
                // can render — see resolveFontFamily/widthPaddedForFontFallback. Pixel-capturing
                // the real rendered glyphs is strictly more faithful than substituting a
                // different font, so prefer it here — falling back to the (already
                // width-padded) text wireframe when capture is ineligible (privacy — see
                // PixelCaptureEligibility) or this host's per-cycle capture budget is spent,
                // rather than ever dropping the text.
                val wireframe = if (isCustomFont && captureCount < MAX_CAPTURES_PER_HOST) {
                    registerLeafCapture(node, view, request)?.also { captureCount++ }
                        ?: buildTextWireframe(node, view, textContent, resolvedLayoutResult, request)
                } else {
                    buildTextWireframe(node, view, textContent, resolvedLayoutResult, request)
                }

                if (wireframe == null) return
                emitWireframe(wireframe, nodeAlpha, rootChildren, wireframes, nestedLayers)
                return
            }

            val children = node.zSortedChildren
            val drawingEffect = hasDrawingEffect(node)

            if (drawingEffect) {
                val semanticsNode = semanticsById[node.semanticsId]
                val shapeWireframe = resolveSimpleShapeWireframe(node, semanticsNode, view, request)
                if (shapeWireframe != null) {
                    val colorHex = shapeWireframe.shapeStyle?.backgroundColor
                    val isOpaque = colorHex != null && colorHex.endsWith(OPAQUE_HEX_SUFFIX, ignoreCase = true)
                    val isRedundant = isOpaque && colorHex == ancestorOpaqueColorHex
                    if (!isRedundant) {
                        emitWireframe(shapeWireframe, nodeAlpha, rootChildren, wireframes, nestedLayers)
                    }
                    val childAncestorColorHex = if (isOpaque) colorHex else ancestorOpaqueColorHex
                    for (i in 0 until children.size) {
                        walk(children[i], nodeAlpha, childAncestorColorHex)
                    }
                    return
                }
            }

            val isAtomicLeaf = children.isEmpty() || (drawingEffect && !isTooLargeToCollapse(node, request))
            if (isAtomicLeaf) {
                if (captureCount >= MAX_CAPTURES_PER_HOST) return
                val wireframe = registerLeafCapture(node, view, request) ?: return
                captureCount++
                emitWireframe(wireframe, nodeAlpha, rootChildren, wireframes, nestedLayers)
            } else {
                for (i in 0 until children.size) {
                    walk(children[i], nodeAlpha, ancestorOpaqueColorHex)
                }
            }
        }

        walk(root, 1f, null)

        if (rootChildren.isEmpty()) return null

        return HostViewDecomposeResult(
            rootChildren = rootChildren,
            nestedLayers = nestedLayers,
            wireframes = wireframes
        )
    }

    /**
     * Adds [wireframe] to the output, wrapping it in a small [MobileSegment.CompositionLayer]
     * carrying a [MobileSegment.CompositionLayerModifier.CompositionLayerOpacityModifier] when
     * [effectiveAlpha] (this node's own alpha combined with every ancestor's, see [nodeOwnAlpha])
     * is less than fully opaque — otherwise referenced directly, avoiding a pointless wrapper layer
     * for the overwhelmingly common fully-opaque case.
     *
     * **Known gap**: this only covers `Modifier.graphicsLayer(alpha = <constant>)` (a fixed value,
     * readable via [InspectableValue] — see [nodeOwnAlpha]). It does *not* cover an *animated*
     * alpha set via the lambda overload (`Modifier.graphicsLayer { alpha = ... }`), which is what
     * Compose transition animations actually use (e.g. Navigation Compose's cross-fade between
     * destinations, which legitimately keeps the outgoing and incoming destinations' `LayoutNode`s
     * composed *simultaneously* for the animation's duration — confirmed on-device). The modifier
     * backing that lambda overload has no public, readable "current value" — confirmed via `javap`:
     * its access flags omit `ACC_PUBLIC` entirely (file-private in Compose's own source, stronger
     * than the `internal` visibility `@Suppress` can bypass elsewhere in this class), and the
     * underlying [androidx.compose.ui.node.OwnedLayer] it writes into is a write-only sink with no
     * getter. During such a transition, both destinations' wireframes still render fully opaque and
     * overlapping instead of cross-fading like the real screen — a known, accepted limitation.
     */
    private fun emitWireframe(
        wireframe: MobileSegment.Wireframe,
        effectiveAlpha: Float,
        rootChildren: MutableList<MobileSegment.CompositionLayerChild>,
        wireframes: MutableList<MobileSegment.Wireframe>,
        nestedLayers: MutableList<MobileSegment.CompositionLayer>
    ) {
        wireframes.add(wireframe)
        if (effectiveAlpha >= 1f) {
            rootChildren.add(
                MobileSegment.CompositionLayerChild(id = wireframe.id, type = MobileSegment.Type.WIREFRAME)
            )
            return
        }
        val bounds = wireframe.bounds()
        val layerId = composeAlphaLayerId(wireframe.id)
        nestedLayers.add(
            MobileSegment.CompositionLayer(
                id = layerId,
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                children = listOf(
                    MobileSegment.CompositionLayerChild(id = wireframe.id, type = MobileSegment.Type.WIREFRAME)
                ),
                modifiers = listOf(
                    MobileSegment.CompositionLayerModifier.CompositionLayerOpacityModifier(value = effectiveAlpha)
                )
            )
        )
        rootChildren.add(MobileSegment.CompositionLayerChild(id = layerId, type = MobileSegment.Type.LAYER))
    }

    /**
     * This node's own alpha, read from a `graphicsLayer` modifier's `alpha` [InspectableValue]
     * element (the same mechanism [hasDrawingEffect] uses for `clip` — confirmed via `javap`
     * against this repo's pinned Compose UI classes: `GraphicsLayerElement.inspectableProperties()`
     * exposes `alpha` alongside `shape`/`clip`). `1f` (fully opaque) if no `graphicsLayer` modifier
     * is present or it doesn't set alpha. Only catches the fixed-value overload — see the "known
     * gap" note on [emitWireframe] for the animated-alpha case this can't observe.
     */
    private fun nodeOwnAlpha(node: LayoutNode): Float {
        var alpha = 1f
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is InspectableValue && modifier.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK) {
                val value = modifier.inspectableElements.firstOrNull { it.name == ALPHA_PROPERTY_NAME }?.value
                if (value is Float) alpha *= value
            }
        }
        return alpha
    }

    /**
     * [node]'s own `Modifier.paint(painter, ...)` element's [Painter.intrinsicSize] in px, or
     * `null` if this node has no such modifier (or the painter's intrinsic size is unspecified) —
     * read via the same public, stable [InspectableValue] mechanism [nodeOwnAlpha]/[hasDrawingEffect]
     * already use for `alpha`/`clip` (confirmed: every `ModifierNodeElement`, including the private
     * `PainterElement` backing `.paint()`, is an `InspectableValue`, and `PainterElement` explicitly
     * exposes `painter` under the `"paint"` name — no reflection, no internal member access).
     *
     * Only a fallback for [registerLeafCapture] when [LayoutCoordinates.boundsInRoot] degenerates
     * to a zero size — see that function's doc for why `node.coordinates` (LayoutNode's
     * `innerCoordinator`) can't see this modifier's size effect.
     */
    private fun resolvePainterIntrinsicSizePx(node: LayoutNode): Size? {
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is InspectableValue && modifier.nameFallback == PAINT_NAME_FALLBACK) {
                val painter = modifier.inspectableElements
                    .firstOrNull { it.name == PAINTER_PROPERTY_NAME }?.value as? Painter
                // Size.Unspecified is NaN-backed, so a plain > 0f comparison (done by the caller)
                // already excludes it without needing a separate isUnspecified check/import here.
                if (painter != null) return painter.intrinsicSize
            }
        }
        return null
    }

    /**
     * `AndroidComposeView` implements `Owner` directly — but the traversal usually first
     * encounters the *outer* `ComposeView` wrapper (which itself hosts one `AndroidComposeView`
     * child), since `isComposeHostView` in `CompositionTreeBuilder` matches on either class name
     * and the outer wrapper is what real View hierarchies expose first. Handles both shapes:
     * [view] itself being the `Owner`, or its first child being one.
     */
    private fun resolveOwner(view: View): Owner? {
        if (view is Owner) return view
        if (view is ViewGroup && view.childCount > 0) {
            val child = view.getChildAt(0)
            if (child is Owner) return child
        }
        return null
    }

    /**
     * Registers one atomic [com.datadog.android.sessionreplay.internal.recorder.PendingPixelCapture]
     * for [node]'s own bounds — [isolationView] is always [hostView] (the real, single `View`
     * every composable in this tree renders through), only [isolationClipRect] differs per node,
     * which is exactly what [com.datadog.android.sessionreplay.internal.recorder.PixelCapture]
     * already supports without any changes.
     */
    private fun registerLeafCapture(
        node: LayoutNode,
        hostView: View,
        request: HostViewDecomposeRequest
    ): MobileSegment.Wireframe.ImageWireframe? {
        val pixelCaptureCallback = request.pixelCaptureCallback ?: return null

        // node.coordinates resolves to LayoutNode.innerCoordinator — the node's own content
        // coordinate space *before* its modifier chain applies — not the outer coordinator that's
        // actually drawn/hit-tested and what Layout Inspector shows. For a plain clip()+background()
        // this makes no visible difference (those modifiers don't change measured size, so inner
        // and outer coincide) — but a node with a size-affecting modifier further in the chain
        // (e.g. Icon's internal .paint(painter), sized from the painter's intrinsic size) measures
        // that effect *outside* the inner coordinate space, so this can read 0 height for a node
        // that's genuinely, say, 32x32 on screen (confirmed on-device via Layout Inspector). The
        // outer coordinator would be the correct read, but it's a true `internal`-only Compose UI
        // member (unlike coordinates, which overrides a public interface) — Kotlin's
        // internal-visibility name-mangling embeds a compose-ui-module suffix (e.g. `$ui_release`)
        // in the compiled method name, not guaranteed stable across the Compose UI build variants
        // a host app might bundle — confirmed on-device via a NoSuchMethodError crash. Do not
        // switch this back to node.outerCoordinator.
        //
        // POSITION (left/top) from node.coordinates is still reliable here: translating even a
        // degenerate zero-size box up the coordinator chain lands at the correct root-space origin,
        // since .paint()/.clip()/.background() all place themselves at a zero intra-node offset —
        // only the SIZE half of boundsInRoot() is broken. So when the primary read is degenerate,
        // this falls back to the node's own position (still correct) combined with a SIZE derived
        // from the Painter's public, stable intrinsicSize — read via the same safe InspectableValue
        // mechanism already used above for alpha/clip/shape, not reflection or any internal member.
        val rawBoundsPx = try {
            node.coordinates.boundsInRoot()
        } catch (e: Exception) {
            return null
        }
        val boundsPx = if (rawBoundsPx.width > 0f && rawBoundsPx.height > 0f) {
            rawBoundsPx
        } else {
            val painterSize = resolvePainterIntrinsicSizePx(node)
            if (painterSize != null && painterSize.width > 0f && painterSize.height > 0f) {
                androidx.compose.ui.geometry.Rect(offset = rawBoundsPx.topLeft, size = painterSize)
            } else {
                return null
            }
        }

        val locationOnScreen = IntArray(2)
        hostView.getLocationOnScreen(locationOnScreen)

        val density = request.mappingContext.systemInformation.screenDensity
        val inverseDensity = if (density == 0f) 1f else 1f / density

        val globalBounds = GlobalBounds(
            x = ((locationOnScreen[0] + boundsPx.left) * inverseDensity).toLong(),
            y = ((locationOnScreen[1] + boundsPx.top) * inverseDensity).toLong(),
            width = (boundsPx.width * inverseDensity).toLong(),
            height = (boundsPx.height * inverseDensity).toLong()
        )

        if (!PixelCaptureEligibility.isEligible(
                textAndInputPrivacy = request.mappingContext.textAndInputPrivacy,
                imagePrivacy = request.mappingContext.imagePrivacy,
                boundsDp = globalBounds
            )
        ) {
            return null
        }

        val isolationClipRect = Rect(
            boundsPx.left.toInt(),
            boundsPx.top.toInt(),
            boundsPx.right.toInt(),
            boundsPx.bottom.toInt()
        )

        val nodeId = composeNodeId(node.semanticsId)

        val imageWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = nodeId,
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            clip = resolveClip(screenBoundsPx(locationOnScreen, boundsPx), request, density),
            isEmpty = true
        )

        val wireframes = mutableListOf<MobileSegment.Wireframe>(imageWireframe)

        pixelCaptureCallback.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = globalBounds,
            isolationView = hostView,
            isolationClipRect = isolationClipRect,
            wireframe = imageWireframe,
            wireframeSlot = WireframeSlot { wireframes[0] = it },
            asyncJobStatusCallback = request.asyncJobStatusCallback
        )

        return imageWireframe
    }

    /**
     * Walks the real (unmerged) [SemanticsNode] tree once per [decompose] call and indexes it by
     * [SemanticsNode.getId], which is the same id space as [LayoutNode.getSemanticsId] — letting
     * the main [LayoutNode] walk look up each node's own semantics with a cheap map read instead
     * of constructing a fresh [androidx.compose.ui.semantics.SemanticsOwner] per node (that
     * constructor's signature isn't stable across Compose UI versions — confirmed breaking
     * between 1.5.4 and 1.9.0-alpha03 — whereas [AndroidComposeView.getSemanticsOwner] and every
     * accessor used here have stayed public and stable across both).
     */
    private fun collectSemanticsNodes(node: SemanticsNode, into: MutableMap<Int, SemanticsNode>) {
        into[node.id] = node
        for (child in node.children) {
            collectSemanticsNodes(child, into)
        }
    }

    /**
     * A node with a real drawing effect of its own (background, `drawBehind`/`drawWithContent`,
     * a custom `Modifier.Node` implementing [DrawModifierNode], or a shape `clip`) can't be split
     * into its children without losing that effect. [resolveSimpleShapeWireframe] tries first to
     * represent it as a flat, maskable-by-position [MobileSegment.Wireframe.ShapeWireframe]; when
     * that's not possible (a real custom draw effect, or an unresolvable `Brush`), pixel-capturing
     * the node's own bounds captures its whole subtree (children draw *inside* a parent's bounds)
     * as one atomic unit instead, background and all descendants (including any text) baked into
     * the same image. A node that IS itself a text node still always peels off into its own
     * maskable [MobileSegment.Wireframe.TextWireframe] first (see the text check above this one in
     * [decompose]'s `walk`) — this only affects *containers* with their own drawing effect.
     *
     * **Known gap**: when a container's drawing effect can't be resolved as a simple shape (falls
     * through to pixel capture), any text inside it isn't masking-aware — a real privacy gap when
     * [TextAndInputPrivacy] masking is enabled. The plan is to composite a masking rectangle over
     * the masked text's own region within the capture (mirroring how the iOS SDK handles this same
     * problem) in a later phase.
     *
     * Custom draw modifiers (`drawBehind{}`/`drawWithContent{}`/third-party) attach a
     * [DrawModifierNode]-implementing `Modifier.Node` — one interface check catches all of them
     * uniformly, *when* [LayoutNode.getModifierInfo] surfaces that Node itself rather than a
     * wrapping Element (confirmed on-device this is what happens for the custom-draw case here,
     * since it's what makes background-with-drawBehind detection work in practice — but confirmed
     * *not* what happens for `Modifier.background()` specifically, which surfaces its wrapping
     * `BackgroundElement` instead, unable to satisfy this check — see [resolveSimpleShapeWireframe]
     * for how that case is handled separately). `Modifier.clip(shape)` doesn't draw, it's a
     * `graphicsLayer` modifier with `clip=true` — confirmed via the Phase 0 on-device spike — so it
     * needs its own check via [InspectableValue].
     */
    /**
     * Attempts to represent [node]'s own drawing effect as a flat [MobileSegment.Wireframe.ShapeWireframe]
     * (background color + optional rounded corner radius) instead of pixel-capturing it — mirroring
     * the native View pipeline's `ViewWireframeMapper` (simple background → shape) vs
     * `PixelCaptureFallbackMapper` (everything else) split. Returns null (falls back to atomic pixel
     * capture) when [node] has any drawing effect beyond a plain `Modifier.background()` — a real
     * `DrawModifierNode`-implementing custom draw modifier (`drawBehind{}`/`drawWithContent{}`/
     * third-party) can't be safely flattened — or when the background's color isn't resolvable at
     * all (an unsupported `Brush` type).
     *
     * Reads `BackgroundElement`'s `color`/`shape`/`brush`/`alpha` fields via the existing, already
     * shipped reflection utilities in this module (`ReflectionUtils`/`BackgroundResolver`, originally
     * built for the old mapper-based pipeline) rather than any new reflection code. Confirmed
     * on-device that [LayoutNode.getModifierInfo] surfaces the *Element* (`BackgroundElement`) for
     * `Modifier.background()`, not the attached `BackgroundNode` — consistent with what
     * `BackgroundResolver` already expects. A direct Kotlin type check against the concrete
     * `BackgroundNode`/`BackgroundElement` classes (a `checkcast`/`instanceof` bytecode instruction)
     * throws `IllegalAccessError` at runtime — both classes are genuinely non-public at the JVM
     * level, confirmed by an actual crash, not just documentation — which is exactly why this goes
     * through `Field.get()` reflection with `setAccessible(true)` instead: that bypasses JVM access
     * checks entirely, unlike a direct cast.
     */
    private fun resolveSimpleShapeWireframe(
        node: LayoutNode,
        semanticsNode: SemanticsNode?,
        hostView: View,
        request: HostViewDecomposeRequest
    ): MobileSegment.Wireframe.ShapeWireframe? {
        if (semanticsNode == null) return null
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is DrawModifierNode) return null
            // getModifierInfo() surfaces the wrapping *Element* (PainterElement), not the
            // PainterNode it creates — the same gap documented above for BackgroundElement — so
            // the is DrawModifierNode check above never catches Modifier.paint(painter). Without
            // this, a node with .paint() (e.g. Icon/Image) chained alongside .clip()/.background()
            // was silently flattened into a plain background-color shape, discarding the actual
            // painted content (a vector icon glyph) entirely — confirmed on-device: the icon's
            // grey circle background rendered, but the glyph inside never did. Explicitly reject
            // shape-flattening whenever a paint() modifier is present so this instead falls
            // through to registerLeafCapture, which pixel-captures the whole node (background +
            // glyph) as one real screenshot.
            if (modifier is InspectableValue && modifier.nameFallback == PAINT_NAME_FALLBACK) return null
        }

        val colorLong = backgroundResolver.resolveBackgroundColor(semanticsNode) ?: return null

        val boundsPx = try {
            node.coordinates.boundsInRoot()
        } catch (e: Exception) {
            return null
        }
        if (boundsPx.width <= 0f || boundsPx.height <= 0f) return null

        val locationOnScreen = IntArray(2)
        hostView.getLocationOnScreen(locationOnScreen)

        val density = request.mappingContext.systemInformation.screenDensity
        val inverseDensity = if (density == 0f) 1f else 1f / density
        val globalBounds = GlobalBounds(
            x = ((locationOnScreen[0] + boundsPx.left) * inverseDensity).toLong(),
            y = ((locationOnScreen[1] + boundsPx.top) * inverseDensity).toLong(),
            width = (boundsPx.width * inverseDensity).toLong(),
            height = (boundsPx.height * inverseDensity).toLong()
        )

        val composeColor = Color(colorLong.toULong())
        val argb = composeColor.toArgb()
        val alpha255 = (composeColor.alpha * OPAQUE_ALPHA_VALUE).toInt()

        val cornerRadius = resolveShapeCornerRadius(node, semanticsNode, globalBounds)

        return MobileSegment.Wireframe.ShapeWireframe(
            id = composeNodeId(node.semanticsId),
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            clip = resolveClip(screenBoundsPx(locationOnScreen, boundsPx), request, density),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = colorStringFormatter.formatColorAndAlphaAsHexString(argb, alpha255),
                cornerRadius = cornerRadius?.toDouble()
            )
        )
    }

    /**
     * [backgroundResolver.resolveBackgroundShape] only reads the `shape` *parameter* passed
     * directly to `Modifier.background(color, shape = ...)` — which defaults to `RectangleShape`
     * when omitted. The equally (if not more) common `Modifier.clip(RoundedCornerShape(...))
     * .background(color)` idiom rounds the corners via a *separate* `graphicsLayer` modifier
     * ([hasDrawingEffect]'s `isClipped` check already detects *that* one is present, but only as
     * a boolean, discarding the actual shape) — so a node using that idiom was silently resolving
     * to a 0dp corner radius here despite genuinely being rounded on-screen. Prefers a
     * `graphicsLayer` clip shape when present (it's the shape actually applied to the final
     * drawn bounds), falling back to the background element's own shape parameter otherwise.
     *
     * Deliberately reads `shape` the same way [nodeOwnAlpha] reads `alpha` and [hasDrawingEffect]
     * reads `clip` — via the public [InspectableValue.inspectableElements] API, not
     * [reflectionUtils]. This pipeline is meant to minimize reflection wherever a public
     * alternative exists; `GraphicsLayerElement.inspectableProperties()` already exposes `shape`
     * alongside `alpha`/`clip` (confirmed via `javap`, same as the other two), so there was no
     * need to reach for [ReflectionUtils.getClipShape] — that stays legacy-mapper-only.
     */
    private fun resolveShapeCornerRadius(
        node: LayoutNode,
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds
    ): Float? {
        val clipShape = node.getModifierInfo().asSequence()
            .mapNotNull { info ->
                val modifier = info.modifier
                if (modifier is InspectableValue && modifier.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK) {
                    modifier.inspectableElements.firstOrNull { it.name == SHAPE_PROPERTY_NAME }?.value as? Shape
                } else {
                    null
                }
            }
            .firstOrNull()
        val backgroundShape = backgroundResolver.resolveBackgroundShape(semanticsNode)
        val shape = clipShape ?: backgroundShape
        return shape?.let { backgroundResolver.resolveCornerRadius(it, globalBounds, node.density) }
    }

    private fun hasDrawingEffect(node: LayoutNode): Boolean {
        for (info in node.getModifierInfo()) {
            val modifier = info.modifier
            if (modifier is DrawModifierNode) {
                return true
            }
            if (modifier is InspectableValue && modifier.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK) {
                val isClipped = modifier.inspectableElements
                    .firstOrNull { it.name == CLIP_PROPERTY_NAME }
                    ?.value == true
                if (isClipped) return true
            }
        }
        return false
    }

    /**
     * Whether [node]'s own bounds are too large a fraction of the screen to usefully collapse into
     * one atomic pixel capture (see [hasDrawingEffect]). Without this, the very first ancestor with
     * a drawing effect wins and swallows everything beneath it — and almost every real Compose
     * screen wraps its *entire* content in a themed root background (`Surface`/`Scaffold`), so
     * without a size cap that root background alone collapses the whole screen back into one image,
     * exactly the whole-view capture this pipeline exists to get away from (confirmed on-device).
     * A small, local background (a button/card/chip) is nowhere near this large; only a background
     * that covers most of the screen — i.e. is effectively a page/root background, not a specific
     * element's own — gets treated as a pass-through instead, letting recursion continue past it to
     * whatever's actually drawn on top. Mirrors [com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCaptureFallbackMapper]'s
     * existing `MAX_CAPTURABLE_AREA_IN_SCREENS` pattern of scaling the threshold to the device's own
     * screen size rather than a fixed pixel count.
     */
    private fun isTooLargeToCollapse(node: LayoutNode, request: HostViewDecomposeRequest): Boolean {
        val boundsPx = try {
            node.coordinates.boundsInRoot()
        } catch (e: Exception) {
            return false
        }
        val nodeAreaPx = boundsPx.width.toDouble() * boundsPx.height.toDouble()
        return nodeAreaPx > screenAreaPx(request) * MAX_DRAWING_EFFECT_AREA_FRACTION
    }

    private fun screenAreaPx(request: HostViewDecomposeRequest): Double {
        val density = request.mappingContext.systemInformation.screenDensity
        val screenBounds = request.mappingContext.systemInformation.screenBounds
        return (screenBounds.width * density).toDouble() * (screenBounds.height * density).toDouble()
    }

    /**
     * An approximate, best-effort signal that [root] is mid-transition — e.g. Navigation Compose's
     * cross-fade between destinations, which legitimately keeps the outgoing and incoming
     * destination's `LayoutNode`s composed *simultaneously* for the animation's duration (confirmed
     * on-device). There's no public, reliable way to read the actual animated alpha driving such a
     * transition (see the "known gap" note on [emitWireframe]), so this falls back to a geometric
     * heuristic instead: two or more sibling nodes that each cover a large fraction of the screen
     * *and* substantially overlap each other's bounds is exactly the shape a full-screen cross-fade
     * takes, and not a pattern normal, static UI produces (ordinary siblings that are each
     * individually large — e.g. a two-pane layout — don't also *overlap* one another).
     *
     * When detected, [decompose] returns null for this cycle, falling back to the pre-existing
     * whole-view pixel capture — a coarser result for that one cycle, but a *correct* one (a real
     * screenshot of whatever is actually blended on screen), rather than this pipeline's
     * decomposed wireframes rendering both destinations' content at full opacity, stacked.
     * Approximate by nature: a false positive (unrelated large overlapping siblings that aren't a
     * transition) costs one cycle of coarser-than-usual capture; a false negative (a transition
     * shaped differently than this heuristic expects) just leaves today's known overlap artifact
     * for that cycle. Runs once per [decompose] call, over the whole tree, before the main walk.
     */
    private fun isLikelyMidTransition(root: LayoutNode, request: HostViewDecomposeRequest): Boolean {
        val screenArea = screenAreaPx(request)

        fun boundsOf(node: LayoutNode): androidx.compose.ui.geometry.Rect? {
            if (!node.isPlaced || !node.isAttached) return null
            return try {
                node.coordinates.boundsInRoot()
            } catch (e: Exception) {
                null
            }
        }

        fun isLarge(bounds: androidx.compose.ui.geometry.Rect): Boolean {
            val area = bounds.width.toDouble() * bounds.height.toDouble()
            return area > screenArea * MIN_TRANSITION_SIBLING_AREA_FRACTION
        }

        fun overlapSignificantly(
            a: androidx.compose.ui.geometry.Rect,
            b: androidx.compose.ui.geometry.Rect
        ): Boolean {
            val overlapWidth = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val overlapHeight = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (overlapWidth <= 0f || overlapHeight <= 0f) return false
            val overlapArea = overlapWidth.toDouble() * overlapHeight.toDouble()
            val minArea = minOf(
                a.width.toDouble() * a.height.toDouble(),
                b.width.toDouble() * b.height.toDouble()
            )
            if (minArea <= 0.0) return false
            return overlapArea > minArea * MIN_TRANSITION_OVERLAP_FRACTION
        }

        fun hasOverlappingLargeSiblings(node: LayoutNode): Boolean {
            if (!node.isPlaced || !node.isAttached) return false

            val largeChildBounds = mutableListOf<androidx.compose.ui.geometry.Rect>()
            val children = node.zSortedChildren
            for (i in 0 until children.size) {
                val bounds = boundsOf(children[i]) ?: continue
                if (isLarge(bounds)) largeChildBounds.add(bounds)
            }
            for (i in largeChildBounds.indices) {
                for (j in i + 1 until largeChildBounds.size) {
                    if (overlapSignificantly(largeChildBounds[i], largeChildBounds[j])) return true
                }
            }

            for (i in 0 until children.size) {
                if (hasOverlappingLargeSiblings(children[i])) return true
            }
            return false
        }

        return hasOverlappingLargeSiblings(root)
    }

    private fun resolveTextContent(config: SemanticsConfiguration): String? {
        if (config.contains(SemanticsProperties.EditableText)) {
            return config[SemanticsProperties.EditableText].text
        }
        if (config.contains(SemanticsProperties.Text)) {
            return config[SemanticsProperties.Text].joinToString(separator = "") { it.text }
        }
        return null
    }

    private fun resolveTextLayoutResult(config: SemanticsConfiguration): TextLayoutResult? {
        if (!config.contains(SemanticsActions.GetTextLayoutResult)) return null
        val action = config[SemanticsActions.GetTextLayoutResult].action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        val handled = try {
            action.invoke(results)
        } catch (e: Exception) {
            false
        }
        if (!handled) return null

        return results.firstOrNull()
    }

    private fun buildTextWireframe(
        node: LayoutNode,
        hostView: View,
        textContent: String,
        resolvedLayoutResult: TextLayoutResult?,
        request: HostViewDecomposeRequest
    ): MobileSegment.Wireframe.TextWireframe? {
        val boundsPx = try {
            node.coordinates.boundsInRoot()
        } catch (e: Exception) {
            return null
        }
        if (boundsPx.width <= 0f || boundsPx.height <= 0f) return null

        val locationOnScreen = IntArray(2)
        hostView.getLocationOnScreen(locationOnScreen)

        val density = request.mappingContext.systemInformation.screenDensity
        val inverseDensity = if (density == 0f) 1f else 1f / density

        val capturedText = resolveCapturedText(textContent, request.mappingContext.textAndInputPrivacy)
        val resolvedStyle = resolvedLayoutResult?.layoutInput?.style
        val fontSizePx = if (resolvedStyle != null && !resolvedStyle.fontSize.isUnspecified) {
            with(node.density) { resolvedStyle.fontSize.toPx() }
        } else {
            with(node.density) { DEFAULT_FONT_SIZE_SP.toPx() }
        }
        val textStyle = resolveTextStyle(fontSizePx, density, resolvedStyle)
        val widthPx = widthPaddedForFontFallback(
            fontFamily = resolvedStyle?.fontFamily,
            text = capturedText,
            fontSizePx = fontSizePx,
            originalWidthPx = boundsPx.width
        )

        val globalBounds = GlobalBounds(
            x = ((locationOnScreen[0] + boundsPx.left) * inverseDensity).toLong(),
            y = ((locationOnScreen[1] + boundsPx.top) * inverseDensity).toLong(),
            width = (widthPx * inverseDensity).toLong(),
            height = (boundsPx.height * inverseDensity).toLong()
        )
        val screenBoundsPx = Rect(
            (locationOnScreen[0] + boundsPx.left).toInt(),
            (locationOnScreen[1] + boundsPx.top).toInt(),
            (locationOnScreen[0] + boundsPx.left + widthPx).toInt(),
            (locationOnScreen[1] + boundsPx.bottom).toInt()
        )

        return MobileSegment.Wireframe.TextWireframe(
            id = composeNodeId(node.semanticsId),
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            clip = resolveClip(screenBoundsPx, request, density),
            text = capturedText,
            textStyle = textStyle
        )
    }

    /**
     * [boundsPx] (Compose root-relative) translated into screen-pixel coordinates via
     * [locationOnScreen] — the same space [HostViewDecomposeRequest.hostVisibleRectPx] is in,
     * so the two can be compared directly in [resolveClip].
     */
    private fun screenBoundsPx(locationOnScreen: IntArray, boundsPx: androidx.compose.ui.geometry.Rect): Rect {
        return Rect(
            (locationOnScreen[0] + boundsPx.left).toInt(),
            (locationOnScreen[1] + boundsPx.top).toInt(),
            (locationOnScreen[0] + boundsPx.right).toInt(),
            (locationOnScreen[1] + boundsPx.bottom).toInt()
        )
    }

    /**
     * How much of [nodeScreenBoundsPx] falls outside [HostViewDecomposeRequest.hostVisibleRectPx]
     * — i.e. how much of it is currently clipped by a scrolling ancestor *outside* the Compose
     * host itself (a native `NestedScrollView`/`ScrollView`, say), which nothing in this class's
     * own [LayoutNode] walk can otherwise see (see the doc on [HostViewDecomposeRequest.hostVisibleRectPx]).
     * Mirrors [com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCaptureFallbackMapper.resolveWireframeClip]'s
     * max-overflow-per-edge math exactly, just against the host's visible rect instead of the
     * individual view's own. Returns null when there's no clip rect to compare against (host not
     * attached/resolvable) or the node is already fully within it.
     */
    private fun resolveClip(
        nodeScreenBoundsPx: Rect,
        request: HostViewDecomposeRequest,
        screenDensity: Float
    ): MobileSegment.WireframeClip? {
        val visibleRect = request.hostVisibleRectPx ?: return null
        val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity

        val clipTop = max(0, visibleRect.top - nodeScreenBoundsPx.top)
        val clipBottom = max(0, nodeScreenBoundsPx.bottom - visibleRect.bottom)
        val clipLeft = max(0, visibleRect.left - nodeScreenBoundsPx.left)
        val clipRight = max(0, nodeScreenBoundsPx.right - visibleRect.right)

        if (clipTop == 0 && clipBottom == 0 && clipLeft == 0 && clipRight == 0) return null

        return MobileSegment.WireframeClip(
            top = (clipTop * inverseDensity).toLong(),
            bottom = (clipBottom * inverseDensity).toLong(),
            left = (clipLeft * inverseDensity).toLong(),
            right = (clipRight * inverseDensity).toLong()
        )
    }

    private fun resolveCapturedText(originalText: String, textAndInputPrivacy: TextAndInputPrivacy): String {
        return when (textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> originalText
            TextAndInputPrivacy.MASK_ALL -> StringObfuscator.getStringObfuscator().obfuscate(originalText)
            TextAndInputPrivacy.MASK_ALL_INPUTS -> originalText
        }
    }

    private fun resolveTextStyle(
        fontSizePx: Float,
        screenDensity: Float,
        style: TextStyle?
    ): MobileSegment.TextStyle {
        val colorArgb = if (style != null && style.color.isSpecified) {
            style.color.toArgb()
        } else {
            DEFAULT_TEXT_COLOR_ARGB
        }
        return MobileSegment.TextStyle(
            family = resolveFontFamily(style?.fontFamily),
            size = (fontSizePx / screenDensity).toLong(),
            color = colorStringFormatter.formatColorAndAlphaAsHexString(colorArgb, OPAQUE_ALPHA_VALUE)
        )
    }

    /**
     * When [fontFamily] isn't a [GenericFontFamily], [resolveFontFamily] substitutes
     * [DEFAULT_FONT_FAMILY] for it — and a box Compose measured and placed for the *real* font's
     * metrics can be too narrow for the *substitute* font's metrics at the same declared point
     * size, wrapping text that never wrapped on-device (see [resolveFontFamily]'s doc). Since the
     * substitute is a font this class controls (not an arbitrary unknown), its actual on-device
     * width for [text] at [fontSizePx] can be measured directly via [fallbackFontPaint] — using
     * the max of that and [originalWidthPx] widens an about-to-be-too-tight box without ever
     * narrowing one that's already correct (e.g. for text whose real font already matches or is
     * narrower than the substitute).
     */
    private fun widthPaddedForFontFallback(
        fontFamily: FontFamily?,
        text: String,
        fontSizePx: Float,
        originalWidthPx: Float
    ): Float {
        if (fontFamily is GenericFontFamily || text.isEmpty()) return originalWidthPx
        val fallbackWidthPx = fallbackFontPaint.apply { textSize = fontSizePx }.measureText(text)
        return max(originalWidthPx, fallbackWidthPx)
    }

    /**
     * Mirrors [com.datadog.android.sessionreplay.compose.internal.mappers.semantics.AbstractSemanticsNodeMapper.resolveTextLayoutInfoToTextStyle]'s
     * font-family handling — only a built-in [GenericFontFamily] (`FontFamily.Serif`/`SansSerif`/
     * `Monospace`/`Cursive`) has a name the player can substitute a matching web font for; a
     * custom/downloadable [FontFamily] has no equivalent there regardless, so [DEFAULT_FONT_FAMILY]
     * is the best available fallback either way. Previously this always returned
     * [DEFAULT_FONT_FAMILY] regardless of [fontFamily] — silently swapping e.g. `FontFamily.Serif`
     * (a common choice for a "display" price/heading) for a sans-serif player font. Different
     * fonts have different character widths at the same declared point size, so a box Compose
     * measured and placed correctly for the real (serif) font can wrap or overflow once the player
     * substitutes a narrower/wider one — this is what was producing the wrong-looking, wrapped
     * price text, not a font-*size* bug.
     */
    private fun resolveFontFamily(fontFamily: FontFamily?): String {
        return (fontFamily as? GenericFontFamily)?.name ?: DEFAULT_FONT_FAMILY
    }

    private companion object {
        /**
         * Defensive cap on pixel captures per Compose host per cycle. Text and pure-structural
         * containers are free (no capture at all), so in practice this only bounds pathological
         * trees with an unusually large number of distinct non-text visual leaves/backgrounds —
         * e.g. a long non-virtualized `Column` of image cards, not a `LazyColumn` (naturally
         * bounded by virtualization).
         */
        private const val MAX_CAPTURES_PER_HOST = 64

        /**
         * Used only when [TextLayoutResult] can't be resolved for a text node (should be rare —
         * `GetTextLayoutResult` is wired up by `BasicText`/`Text()` themselves) — a reasonable
         * fallback rather than a guess passed off as measured data.
         */
        private val DEFAULT_FONT_SIZE_SP = 14.sp
        private const val DEFAULT_TEXT_COLOR_ARGB = android.graphics.Color.BLACK
        private const val DEFAULT_FONT_FAMILY = "roboto, sans-serif"

        private const val GRAPHICS_LAYER_NAME_FALLBACK = "graphicsLayer"
        private const val CLIP_PROPERTY_NAME = "clip"
        private const val ALPHA_PROPERTY_NAME = "alpha"
        private const val SHAPE_PROPERTY_NAME = "shape"
        private const val PAINT_NAME_FALLBACK = "paint"
        private const val PAINTER_PROPERTY_NAME = "painter"

        /**
         * [ColorStringFormatter.formatColorAndAlphaAsHexString] emits `#RRGGBBAA` — this is that
         * suffix at full (255) alpha, used to recognize an opaque shape color for the
         * redundant-same-color-child skip in [walk].
         */
        private const val OPAQUE_HEX_SUFFIX = "ff"

        /**
         * See [isTooLargeToCollapse]: a drawing-effect node covering more than half the screen's
         * own area is treated as a page/root-level background rather than a specific element's
         * own, and is not collapsed into one atomic pixel capture.
         */
        private const val MAX_DRAWING_EFFECT_AREA_FRACTION = 0.5

        /**
         * See [isLikelyMidTransition]: how large a sibling node's own bounds must be, relative to
         * the screen, before it's even considered a candidate "destination" for the overlap check.
         * Measured on-device against a real Navigation Compose cross-fade: both competing
         * destinations sat at ~0.86 of the screen's area, so 0.75 keeps a comfortable margin below
         * that real case while excluding smaller, non-transition overlaps (e.g. a half-screen
         * bottom sheet over content, which shouldn't trip this check).
         */
        private const val MIN_TRANSITION_SIBLING_AREA_FRACTION = 0.75

        /**
         * See [isLikelyMidTransition]: how much two large siblings' bounds must overlap (relative
         * to the smaller of the two) before they're treated as competing, simultaneously-composed
         * destinations rather than incidentally-adjacent normal UI. Measured on-device: the real
         * cross-fade case had *identical* bounds for both destinations (overlap fraction of
         * exactly 1.0), so 0.9 keeps a comfortable margin below that while excluding partial,
         * incidental overlaps (e.g. a dialog/scrim over part of the screen).
         */
        private const val MIN_TRANSITION_OVERLAP_FRACTION = 0.9
    }
}

private val MobileSegment.Wireframe.id: Long
    get() = when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> id
        is MobileSegment.Wireframe.TextWireframe -> id
        is MobileSegment.Wireframe.ImageWireframe -> id
        is MobileSegment.Wireframe.PlaceholderWireframe -> id
        is MobileSegment.Wireframe.WebviewWireframe -> id
    }

private data class WireframeBounds(val x: Long, val y: Long, val width: Long, val height: Long)

private fun MobileSegment.Wireframe.bounds(): WireframeBounds = when (this) {
    is MobileSegment.Wireframe.ShapeWireframe -> WireframeBounds(x, y, width, height)
    is MobileSegment.Wireframe.TextWireframe -> WireframeBounds(x, y, width, height)
    is MobileSegment.Wireframe.ImageWireframe -> WireframeBounds(x, y, width, height)
    is MobileSegment.Wireframe.PlaceholderWireframe -> WireframeBounds(x, y, width, height)
    is MobileSegment.Wireframe.WebviewWireframe -> WireframeBounds(x, y, width, height)
}

/**
 * Namespaces a Compose `LayoutNode.semanticsId` (an `Int`, drawn from Compose's own internal
 * counter) away from the two disjoint id sources `CompositionTreeBuilder` already uses for native
 * Views — `resolveViewId` (identity hash) and `resolveChildUniqueIdentifier` (`SecureRandom`),
 * both bounded to the signed 32-bit range — plus its `SYNTHETIC_ROOT_LAYER_ID` sentinel
 * (`Int.MAX_VALUE + 1`). Shifted comfortably above all three, and well within the JS
 * safe-integer range (`2^53-1`) ids need to survive round-tripping through the JSON/JS-based
 * player.
 */
private fun composeNodeId(semanticsId: Int): Long = (1L shl 32) + semanticsId.toLong()

/**
 * Namespaces an opacity-wrapper [MobileSegment.CompositionLayer]'s id away from [composeNodeId]'s
 * own range — every value `composeNodeId` can produce is below `2^33` (`2^32 + Int.MAX_VALUE <
 * 2^33`), so offsetting by `2^33` guarantees no collision regardless of which wireframe id is
 * wrapped.
 */
private fun composeAlphaLayerId(wireframeId: Long): Long = (1L shl 33) + wireframeId
