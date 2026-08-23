/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.resolveComposeWindowOffset
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import kotlin.math.roundToInt

/**
 * Real implementation of [CompositionHostDecomposer], walking a Compose host's *unmerged* semantics
 * tree - the same structural basis the legacy mapper-based pipeline
 * ([com.datadog.android.sessionreplay.compose.internal.mappers.semantics.RootSemanticsNodeMapper])
 * already relies on to capture backgrounds/shapes for ordinary composables that carry no explicit
 * semantics of their own, which is the empirical evidence this mirrors the real `LayoutNode`
 * structure closely enough to use here too - reusing already-proven reflection utilities
 * ([SemanticsUtils], [ReflectionUtils]) end to end, rather than a fresh, riskier direct
 * `LayoutNode`/`Owner` walk requiring `@OptIn(InternalComposeUiApi::class)`.
 *
 * Preserves text content and resolvable background/shape styling, extracts `graphicsLayer` alpha as
 * [CapturedModifier.Opacity], and hands embedded interop `View`s back to native traversal. A node
 * with a real drawing effect of its own that can't be flattened to a plain shape (a custom
 * `drawBehind`/`drawWithContent`/`Canvas`, an `Image`/`Icon`, a shadow) - or a childless leaf with
 * nothing else resolved for it - is pixel-captured instead (see [isPixelCaptureCandidate] and
 * [resolvePendingCaptures]): redrawn via [View.draw] into a bitmap-backed software canvas, exactly
 * like the native View pixel-fallback path, rather than reconstructed property-by-property via
 * reflection into Compose's internal drawing/painter state. Whatever a pixel capture bakes in -
 * gradients, alpha compositing, shadows, custom paint - is therefore already correct in the image
 * with no separate extraction needed; the only privacy-relevant step left is the same
 * image-privacy-eligibility check the native path applies, plus the same downstream text-masking
 * every pixel capture (native or Compose) already goes through.
 */
internal class GranularComposeDecomposer(
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val reflectionUtils: ReflectionUtils = ReflectionUtils(),
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND,
    private val compatibilityGate: GranularComposeCompatibilityGate = GranularComposeCompatibilityGate.SHARED,
    private val nodesPerCheckpoint: Int = NODES_PER_CHECKPOINT,
    private val hostRasterizer: ComposeHostCaptureRasterizer = DefaultComposeHostCaptureRasterizer(internalLogger)
) : CompositionHostDecomposer {

    // Deliberately catches Throwable, not Exception: a Compose UI version outside this artifact's
    // tested range can fail with a LinkageError/NoSuchMethodError, which the gate must still catch
    // to disable granular decomposition rather than crash the host application.
    @Suppress("TooGenericExceptionCaught")
    override fun canDecompose(view: View): Boolean {
        if (!compatibilityGate.isAvailable()) return false
        return try {
            semanticsUtils.findRootSemanticsNode(view) != null
        } catch (t: Throwable) {
            compatibilityGate.markIncompatible(t, internalLogger)
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun decompose(view: View, request: CompositionHostDecomposeRequest): CompositionHostDecomposeResult? {
        if (!compatibilityGate.isAvailable()) return null
        return try {
            DecomposeSession(view, request).run()
        } catch (t: Throwable) {
            compatibilityGate.markIncompatible(t, internalLogger)
            null
        }
    }

    /**
     * Both [SemanticsUtils.resolveBackgroundColor] and the `color` carried by [TextLayoutInfo] are
     * the raw internal `Color.value` bit pattern (as a `Long`), not a plain packed 0xAARRGGBB value
     * - the same shift-then-reconstruct approach the legacy mapper pipeline's
     * `AbstractSemanticsNodeMapper.convertColor` already uses in production for both, reused here
     * verbatim rather than re-derived.
     */
    private fun convertColor(rawColorValue: Long): String? {
        if (rawColorValue == SemanticsUtils.COLOR_UNSPECIFIED) return null
        val color = Color(rawColorValue shr COMPOSE_COLOR_SHIFT)
        return colorStringFormatter.formatColorAndAlphaAsHexString(
            color.toArgb(),
            (color.alpha * OPAQUE_ALPHA_VALUE).roundToInt()
        )
    }

    /** One leaf awaiting the single per-host batched capture in [DecomposeSession.resolvePendingCaptures]. */
    private class PendingCapture(
        val nodeIdentity: CapturedIdentity,
        val bounds: CapturedBounds,
        val hostBoundsPx: Rect
    )

    /** One [decompose] call's state - a fresh instance per call, so this class itself stays stateless. */
    private inner class DecomposeSession(
        private val hostView: View,
        private val request: CompositionHostDecomposeRequest
    ) {
        private val windowOffset = hostView.resolveComposeWindowOffset(request.screenDensity)
        private val nodes = mutableListOf<CapturedLayer>()
        private val wireframes = mutableListOf<CapturedWireframe>()
        private var nodesVisited = 0
        private var aborted = false
        private val pendingCaptures = mutableListOf<PendingCapture>()

        @Suppress("ReturnCount")
        fun run(): CompositionHostDecomposeResult? {
            val root = semanticsUtils.findRootSemanticsNode(hostView)
            if (root == null) return null
            val rootChildren = root.children.mapNotNull { walkNode(it) }
            if (aborted || rootChildren.isEmpty()) return null
            // The batched draw itself is one potentially-expensive operation outside the per-node
            // checkpoint loop above, and (like any draw) can't be interrupted once started - so the
            // deadline is checked once more immediately before it, mirroring the native pixel-fallback
            // path's own "these checks must occur first because a started draw cannot be interrupted"
            // rule. Skipping it here degrades this generation the same way an aborted node walk
            // already does: no partial tree, the caller's own post-call re-check discards the window.
            if (pendingCaptures.isNotEmpty() && !request.shouldContinue()) return null
            resolvePendingCaptures()
            return CompositionHostDecomposeResult(rootChildren, nodes, wireframes)
        }

        /**
         * The caller's generation deadline isn't otherwise visible across this module boundary, so
         * this walk polls [CompositionHostDecomposeRequest.shouldContinue] itself every
         * [nodesPerCheckpoint] nodes - mirroring the native traversal's own `viewsPerCheckpoint`
         * cadence in `AndroidWindowTraversal`. On failure the whole walk is abandoned (never a
         * partial tree): every further [walkNode] call short-circuits via [aborted], and [run]
         * returns null, which the caller's own post-call deadline re-check turns into discarding the
         * entire window capture, exactly like a native-traversal abort.
         */
        @Suppress("ReturnCount")
        private fun walkNode(node: SemanticsNode): CapturedChild? {
            if (aborted) return null
            nodesVisited++
            if (nodesVisited % nodesPerCheckpoint == 0 && !request.shouldContinue()) {
                aborted = true
                return null
            }
            val nodeIdentity = request.identityFactory.composeNode(request.hostIdentity, node.id.toString())
            val bounds = boundsOf(node)

            if (semanticsUtils.isNodeHidden(node)) {
                val placeholderId = request.identityFactory.placeholderWireframe(nodeIdentity)
                wireframes += CapturedWireframe.PrivacyPlaceholder(
                    identity = placeholderId,
                    bounds = bounds,
                    label = HIDDEN_LABEL
                )
                return registerLayer(
                    nodeIdentity,
                    bounds,
                    listOf(CapturedChild.Wireframe(placeholderId))
                )
            }

            val interopView = reflectionUtils.getInteropView(node)
            if (interopView != null) {
                val subtree = request.nativeViewHandoff(interopView, nodeIdentity) ?: return null
                nodes += subtree.layers
                wireframes += subtree.wireframes
                return CapturedChild.Layer(subtree.rootLayer.identity)
            }

            val textLayoutInfo = semanticsUtils.resolveTextLayoutInfo(node, internalLogger)
            if (textLayoutInfo != null) {
                val textId = request.identityFactory.textWireframe(nodeIdentity)
                wireframes += toTextWireframe(textId, bounds, textLayoutInfo)
                return registerLayer(
                    nodeIdentity,
                    bounds,
                    listOf(CapturedChild.Wireframe(textId)),
                    modifiers = resolveModifiers(node)
                )
            }

            val shapeWireframe = resolveShapeWireframe(node, nodeIdentity, bounds)
            tryQueuePixelCapture(node, nodeIdentity, bounds, shapeWireframe)?.let { return it }

            val children = mutableListOf<CapturedChild>()
            shapeWireframe?.let { shape ->
                wireframes += shape
                children += CapturedChild.Wireframe(shape.identity)
            }
            node.children.forEach { child -> walkNode(child)?.let { children += it } }

            return registerLayer(nodeIdentity, bounds, children, modifiers = resolveModifiers(node))
        }

        /**
         * Queues [node] for the single per-host batched capture in [resolvePendingCaptures] when it
         * has no [shapeWireframe] of its own and is a capture candidate (see
         * [isPixelCaptureCandidate]) - a node with a real drawing effect of its own (a
         * `DrawModifierNode` - `drawBehind`/`drawWithContent`/`Canvas`/shadow) can't be split into
         * its children without losing that effect, so it's captured atomically instead, background
         * and all descendants (including any text) baked into the same image; that's also why a
         * queued node never recurses into its own children (see the call site in [walkNode]).
         * Returns the layer child standing in for the queued node, or null when [node] isn't
         * queued (it already has a shape wireframe, the per-host cap is reached, it isn't a capture
         * candidate, or its bounds can't be resolved) - [walkNode] then falls through to its normal
         * child-recursion path.
         */
        @Suppress("ReturnCount")
        private fun tryQueuePixelCapture(
            node: SemanticsNode,
            nodeIdentity: CapturedIdentity,
            bounds: CapturedBounds,
            shapeWireframe: CapturedWireframe.Shape?
        ): CapturedChild? {
            if (shapeWireframe != null ||
                pendingCaptures.size >= MAX_PIXEL_CAPTURES_PER_HOST ||
                !isPixelCaptureCandidate(node)
            ) {
                return null
            }
            val hostBoundsPx = hostRelativeBoundsPx(node) ?: return null
            pendingCaptures += PendingCapture(nodeIdentity, bounds, hostBoundsPx)
            return CapturedChild.Layer(nodeIdentity)
        }

        /**
         * Privacy is checked *before* asking [hostRasterizer] to draw anything, same as the native
         * pixel-fallback path - an ineligible region is never even included in the batched draw
         * request. [hostRasterizer] still draws [hostView] at most once regardless of how many
         * [pendingCaptures] end up eligible, since it's the sole implementation of the "one draw, N
         * crops" contract - see [ComposeHostCaptureRasterizer]'s own doc for why that's a
         * correctness requirement here, not just a performance optimization.
         */
        private fun resolvePendingCaptures() {
            if (pendingCaptures.isEmpty()) return
            val eligible = pendingCaptures.filter { request.pixelCapturePlaceholderLabelFor(it.bounds) == null }
            val crops = if (eligible.isEmpty()) {
                emptyMap()
            } else {
                val bitmaps = hostRasterizer.captureRegions(hostView, eligible.map { it.hostBoundsPx })

                // zip pairs elements positionally and never throws; hostRasterizer.captureRegions
                // returns exactly one bitmap per requested region, in the same order, so the two
                // lists are always the same size.
                @Suppress("UnsafeThirdPartyFunctionCall")
                val paired = eligible.map { it.nodeIdentity }.zip(bitmaps)
                paired.toMap()
            }
            pendingCaptures.forEach { pending ->
                val wireframe = resolveOneCapture(pending, crops[pending.nodeIdentity])
                wireframes += wireframe
                nodes += CapturedLayer(
                    identity = pending.nodeIdentity,
                    kind = CapturedLayerKind.COMPOSE_NODE,
                    bounds = pending.bounds,
                    children = listOf(CapturedChild.Wireframe(wireframe.identity))
                )
            }
        }

        private fun resolveOneCapture(pending: PendingCapture, cropped: Bitmap?): CapturedWireframe {
            if (cropped != null) {
                val pixelIdentity = request.identityFactory.imageWireframe(pending.nodeIdentity)
                request.pendingPixelCaptureSink.register(
                    PendingPixelCapture(
                        wireframeIdentity = pixelIdentity,
                        ownerIdentity = pending.nodeIdentity,
                        bitmap = cropped
                    )
                )
                return CapturedWireframe.Pixel(
                    identity = pixelIdentity,
                    bounds = pending.bounds,
                    resource = PixelResource.Unresolved
                )
            }
            val placeholderLabel = request.pixelCapturePlaceholderLabelFor(pending.bounds) ?: CAPTURE_UNAVAILABLE_LABEL
            return CapturedWireframe.PrivacyPlaceholder(
                identity = request.identityFactory.placeholderWireframe(pending.nodeIdentity),
                bounds = pending.bounds,
                label = placeholderLabel
            )
        }

        private fun registerLayer(
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            children: List<CapturedChild>,
            modifiers: List<CapturedModifier> = emptyList()
        ): CapturedChild {
            val layer = CapturedLayer(
                identity = identity,
                kind = CapturedLayerKind.COMPOSE_NODE,
                bounds = bounds,
                children = children,
                modifiers = modifiers
            )
            nodes += layer
            return CapturedChild.Layer(identity)
        }

        private fun boundsOf(node: SemanticsNode): CapturedBounds {
            val globalBounds = semanticsUtils.resolveInnerBounds(node, windowOffset)
            return CapturedBounds(globalBounds.x, globalBounds.y, globalBounds.width, globalBounds.height)
        }

        private fun toTextWireframe(
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            textLayoutInfo: TextLayoutInfo
        ): CapturedWireframe.Text {
            return CapturedWireframe.Text(
                identity = identity,
                bounds = bounds,
                text = textLayoutInfo.text,
                textStyle = CapturedTextStyle(
                    family = DEFAULT_FONT_FAMILY,
                    size = textLayoutInfo.fontSize,
                    color = convertColor(textLayoutInfo.color.toLong()) ?: DEFAULT_TEXT_COLOR
                )
            )
        }

        private fun resolveShapeWireframe(
            node: SemanticsNode,
            nodeIdentity: CapturedIdentity,
            bounds: CapturedBounds
        ): CapturedWireframe.Shape? {
            val colorLong = semanticsUtils.resolveBackgroundColor(node) ?: return null
            val cornerRadius = semanticsUtils.resolveBackgroundShape(node)?.let { shape ->
                semanticsUtils.resolveCornerRadius(
                    shape,
                    GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
                    node.layoutInfo.density
                )
            }
            return CapturedWireframe.Shape(
                identity = request.identityFactory.shapeWireframe(nodeIdentity),
                bounds = bounds,
                style = CapturedShapeStyle(
                    backgroundColor = convertColor(colorLong),
                    cornerRadius = cornerRadius?.toDouble()
                )
            )
        }

        /**
         * Only `graphicsLayer`'s fixed-value `alpha` is extracted, read via [InspectableValue] the
         * same way [com.datadog.android.sessionreplay.compose.internal.utils.BackgroundResolver]
         * already reads `graphicsLayer`'s `clip`/`shape` properties. Does not cover the animated
         * lambda overload (`Modifier.graphicsLayer { alpha = ... }`), which exposes no public
         * readable current value.
         */
        private fun resolveModifiers(node: SemanticsNode): List<CapturedModifier> {
            for (info in node.layoutInfo.getModifierInfo()) {
                val modifier = info.modifier
                if (modifier is InspectableValue && modifier.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK) {
                    val alpha = modifier.inspectableElements
                        .firstOrNull { it.name == ALPHA_PROPERTY_NAME }
                        ?.value as? Float
                    if (alpha != null && alpha < 1f) {
                        return listOf(CapturedModifier.Opacity(alpha.toDouble()))
                    }
                }
            }
            return emptyList()
        }
    }

    private companion object {
        const val HIDDEN_LABEL = "Hidden"
        const val CAPTURE_UNAVAILABLE_LABEL = "Content"
        const val DEFAULT_FONT_FAMILY = "roboto, sans-serif"
        const val DEFAULT_TEXT_COLOR = "#000000FF"
        const val GRAPHICS_LAYER_NAME_FALLBACK = "graphicsLayer"
        const val ALPHA_PROPERTY_NAME = "alpha"
        const val COMPOSE_COLOR_SHIFT = 32
        const val NODES_PER_CHECKPOINT = 200

        /** Defensive cap on pixel captures per Compose host per cycle - see [isPixelCaptureCandidate]. */
        const val MAX_PIXEL_CAPTURES_PER_HOST = 100
    }
}

/**
 * A node with a real drawing effect of its own (a `DrawModifierNode` - `drawBehind`/
 * `drawWithContent`/`Canvas`/shadow) can't be split into its children without losing that effect -
 * captured atomically instead, background and all descendants (including any text) baked into the
 * same image, which is why capture-candidate nodes never recurse into their own children (see the
 * call site in [GranularComposeDecomposer]'s `walkNode`). A childless leaf with no text and no
 * resolvable background (an `Image`/`Icon`, or any other composable this decomposer has no
 * structural mapping for) is also a candidate - `Modifier.paint()` (what `Image`/`Icon` use
 * internally) doesn't attach a `DrawModifierNode` and has no other public, reflection-free signal
 * this decomposer can check for, so a childless node with real bounds and nothing else resolved for
 * it is the fallback signal instead. This can occasionally capture a genuinely empty leaf (e.g. a
 * bare `Spacer`) as a small blank image; the existing hash-based resource dedup means every such
 * capture across a whole snapshot collapses to one cheap, already-cached resource rather than N
 * uploads.
 */
private fun isPixelCaptureCandidate(node: SemanticsNode): Boolean {
    val hasDrawingEffect = node.layoutInfo.getModifierInfo().any { it.modifier is DrawModifierNode }
    return hasDrawingEffect || node.children.isEmpty()
}

/**
 * [node]'s bounds in raw pixels relative to its Compose host's own origin - the same coordinate
 * space [ComposeHostCaptureRasterizer] draws the host into - for cropping, as opposed to the
 * screen-absolute dp bounds used for the wireframe itself. Null if the node's bounds can't be read
 * or are degenerate, in which case the node is left with no visual content of its own rather than
 * risk cropping a meaningless region.
 */
private fun hostRelativeBoundsPx(node: SemanticsNode): Rect? {
    val boundsInRoot = node.boundsInRoot
    if (boundsInRoot.width <= 0f || boundsInRoot.height <= 0f) return null
    return Rect(
        boundsInRoot.left.roundToInt(),
        boundsInRoot.top.roundToInt(),
        boundsInRoot.right.roundToInt(),
        boundsInRoot.bottom.roundToInt()
    )
}

/**
 * Isolates the actual host-`View.draw()`-then-crop mechanics so it can be substituted in tests -
 * mirrors `CapturedPixelFallbackMapper`'s own `ViewRasterizer` seam for the native path. [regions]
 * are in raw pixels relative to [hostView]'s own origin; the returned list has exactly one entry
 * per input region, in the same order, null where that specific crop failed.
 */
internal fun interface ComposeHostCaptureRasterizer {
    fun captureRegions(hostView: View, regions: List<Rect>): List<Bitmap?>
}

internal class DefaultComposeHostCaptureRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ComposeHostCaptureRasterizer {

    override fun captureRegions(hostView: View, regions: List<Rect>): List<Bitmap?> {
        val hostBitmap = rasterize(hostView) ?: return regions.map { null }
        return regions.map { cropSafely(hostBitmap, it) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun rasterize(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            @Suppress("UnsafeThirdPartyFunctionCall") // view.draw runs arbitrary custom draw code; caught below
            view.draw(Canvas(bitmap))
            bitmap
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to rasterize Compose host for pixel-fallback capture" },
                e
            )
            null
        }
    }

    private fun cropSafely(bitmap: Bitmap, boundsPx: Rect): Bitmap? {
        val clamped = Rect(
            boundsPx.left.coerceIn(0, bitmap.width),
            boundsPx.top.coerceIn(0, bitmap.height),
            boundsPx.right.coerceIn(0, bitmap.width),
            boundsPx.bottom.coerceIn(0, bitmap.height)
        )
        if (clamped.width() <= 0 || clamped.height() <= 0) return null
        return try {
            // out-of-bounds region throws IllegalArgumentException, caught below
            @Suppress("UnsafeThirdPartyFunctionCall")
            Bitmap.createBitmap(bitmap, clamped.left, clamped.top, clamped.width(), clamped.height())
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to crop Compose host capture region" },
                e
            )
            null
        }
    }
}
