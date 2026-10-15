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
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
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
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
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
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixColorFilter

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
 * with a recognized custom drawing modifier and no resolved text/background, or a childless leaf
 * with nothing else resolved for it, is pixel-captured (see [NodeInspection.isPixelCaptureCandidate] and
 * [resolvePendingCaptures]): redrawn via [View.draw] into a bitmap-backed software canvas, exactly
 * like the native View pixel-fallback path, rather than reconstructed property-by-property via
 * reflection into Compose's internal drawing/painter state. Whatever a pixel capture bakes in -
 * gradients, alpha compositing, shadows, custom paint - is therefore already correct in the image
 * with no separate extraction needed; the only privacy-relevant step left is the same
 * image-privacy-eligibility check the native path applies, plus the same downstream text-masking
 * every pixel capture (native or Compose) already goes through.
 *
 * In sibling groups with at least 32 nodes, leaves outside the caller's copied host bounds are
 * omitted before style extraction. The native snapshot builder always clips composition output
 * to those bounds, so these leaves would contribute no replay pixels. Parents, privacy placeholders,
 * and native interop retain their existing paths; every capture resolves current geometry.
 */
internal class GranularComposeDecomposer(
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(textCaptureCache = ComposeTextCaptureCache()),
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

    /** Copies only the graphics properties the capture understands, without an intermediate map. */
    private class GraphicsLayerProperties(modifier: InspectableValue) {
        var clip = false
        var shape: Shape? = null
        var colorFilter: ColorFilter? = null
        var shadowElevation: Float? = null
        var spotShadowColor: Color? = null
        var renderEffect: RenderEffect? = null
        var alpha: Float? = null

        init {
            for (property in modifier.inspectableElements) {
                when (property.name) {
                    CLIP_PROPERTY_NAME -> clip = property.value as? Boolean == true
                    SHAPE_PROPERTY_NAME -> shape = property.value as? Shape
                    COLOR_FILTER_PROPERTY_NAME -> colorFilter = property.value as? ColorFilter
                    SHADOW_ELEVATION_PROPERTY_NAME -> shadowElevation = property.value as? Float
                    SPOT_SHADOW_COLOR_PROPERTY_NAME -> spotShadowColor = property.value as? Color
                    RENDER_EFFECT_PROPERTY_NAME -> renderEffect = property.value as? RenderEffect
                    ALPHA_PROPERTY_NAME -> alpha = property.value as? Float
                }
            }
        }
    }

    /** One [decompose] call's state - a fresh instance per call, so this class itself stays stateless. */
    @Suppress("TooManyFunctions") // Keep the decomposition phases and deadline checkpoint in one capture session.
    private inner class DecomposeSession(
        private val hostView: View,
        private val request: CompositionHostDecomposeRequest
    ) {
        private val windowOffset = hostView.resolveComposeWindowOffset(request.screenDensity)
        private val hostClip = request.hostBounds
        private val nodes = mutableListOf<CapturedLayer>()
        private val wireframes = mutableListOf<CapturedWireframe>()
        private var culledLeaf = false
        private var nodesVisited = 0
        private var aborted = false
        private val pendingCaptures = mutableListOf<PendingCapture>()

        // These immutable values can be shared within this capture without retaining any UI nodes or text.
        private val resolvedColors = mutableMapOf<Long, String>()
        private var lastTextStyle: CapturedTextStyle? = null
        private var lastTextColor: ULong = 0uL
        private var lastTextSize = 0L

        private fun resolveColor(rawColor: Long): String? {
            resolvedColors[rawColor]?.let { return it }
            return convertColor(rawColor)?.also { color ->
                if (resolvedColors.size < MAX_CACHED_COLORS) resolvedColors[rawColor] = color
            }
        }

        private fun textStyleOf(info: TextLayoutInfo): CapturedTextStyle {
            val previous = lastTextStyle
            if (previous != null && lastTextColor == info.color && lastTextSize == info.fontSize) return previous
            return CapturedTextStyle(
                family = DEFAULT_FONT_FAMILY,
                size = info.fontSize,
                color = resolveColor(info.color.toLong()) ?: DEFAULT_TEXT_COLOR
            ).also {
                lastTextStyle = it
                lastTextColor = info.color
                lastTextSize = info.fontSize
            }
        }

        @Suppress("ReturnCount")
        fun run(): CompositionHostDecomposeResult? {
            val root = semanticsUtils.findRootSemanticsNode(hostView) ?: return null
            val rootChildren = mutableListOf<CapturedChild>()
            walkChildren(root.children, rootChildren)
            if (aborted || (rootChildren.isEmpty() && !culledLeaf)) return null
            // The batched draw itself is one potentially-expensive operation outside the per-node
            // checkpoint loop above, and (like any draw) can't be interrupted once started - so the
            // deadline is checked once more immediately before it, mirroring the native pixel-fallback
            // path's own "these checks must occur first because a started draw cannot be interrupted"
            // rule. Skipping it here degrades this generation the same way an aborted node walk
            // already does: no partial tree, the caller's own post-call re-check discards the window.
            if (pendingCaptures.isNotEmpty() && !request.shouldContinue()) return null
            if (!resolvePendingCaptures()) return null
            return CompositionHostDecomposeResult(rootChildren, nodes, wireframes)
        }

        @Suppress("ReturnCount")
        private fun walkNode(node: SemanticsNode, allowCulling: Boolean): CapturedChild? {
            if (!shouldVisitNode()) return null
            val nodeIdentity = request.identityFactory.composeNode(request.hostIdentity, node.id.toString())
            val bounds = boundsOf(node)

            if (semanticsUtils.isNodeHidden(node)) {
                return captureHiddenNode(nodeIdentity, bounds)
            }

            val interopView = reflectionUtils.getInteropView(node)
            if (interopView != null) {
                val subtree = request.nativeViewHandoff(interopView, nodeIdentity) ?: return null
                nodes += subtree.layers
                wireframes += subtree.wireframes
                return CapturedChild.Layer(subtree.rootLayer.identity)
            }

            // The native builder applies this same copied rectangle to all composition wireframes.
            // Retain parents to preserve overflowing children, and preserve privacy/interop above.
            var resolvedChildren: List<SemanticsNode>? = null
            if (allowCulling && hostClip != null && isFullyOutsideHostClip(bounds, hostClip)) {
                resolvedChildren = node.children
                if (resolvedChildren.isEmpty()) {
                    culledLeaf = true
                    return null
                }
            }

            // ModifierInfo and children getters rebuild Compose-owned lists; read each once in this UI turn.
            val inspection = NodeInspection(node, resolvedChildren)
            val modifiers = inspection.modifiers
            val textLayoutInfo = semanticsUtils.resolveTextLayoutInfo(node, internalLogger, modifiers)
            if (textLayoutInfo != null) {
                val textId = request.identityFactory.textWireframe(nodeIdentity)
                wireframes += toTextWireframe(textId, bounds, textLayoutInfo)
                return registerLayer(
                    nodeIdentity,
                    bounds,
                    listOf(CapturedChild.Wireframe(textId)),
                    modifiers = resolveModifiers(node, bounds, modifiers)
                )
            }

            val shapeWireframe = resolveShapeWireframe(node, nodeIdentity, bounds, modifiers)
            tryQueuePixelCapture(node, nodeIdentity, bounds, shapeWireframe, inspection)
                ?.let { return it }

            val children = mutableListOf<CapturedChild>()
            shapeWireframe?.let { shape ->
                wireframes += shape
                children += CapturedChild.Wireframe(shape.identity)
            }
            walkChildren(inspection.children, children)
            if (aborted) return null

            return registerLayer(nodeIdentity, bounds, children, modifiers = resolveModifiers(node, bounds, modifiers))
        }

        /** Poll the caller's deadline between bounded node operations; never return a partial tree. */
        private fun shouldVisitNode(): Boolean {
            if (aborted) return false
            nodesVisited++
            if (nodesVisited % nodesPerCheckpoint == 0 && !request.shouldContinue()) aborted = true
            return !aborted
        }

        private fun walkChildren(source: List<SemanticsNode>, destination: MutableList<CapturedChild>) {
            // Use the child list already needed for traversal; no scan, cache, or extra View reads.
            val allowCulling = hostClip != null && source.size >= MIN_CULLING_SIBLINGS
            for (child in source) {
                walkNode(child, allowCulling)?.let { destination += it }
                if (aborted) break
            }
        }

        private fun captureHiddenNode(identity: CapturedIdentity, bounds: CapturedBounds): CapturedChild {
            val placeholderId = request.identityFactory.placeholderWireframe(identity)
            wireframes += CapturedWireframe.PrivacyPlaceholder(placeholderId, bounds, label = HIDDEN_LABEL)
            return registerLayer(identity, bounds, listOf(CapturedChild.Wireframe(placeholderId)))
        }

        /**
         * Queues [node] for the single per-host batched capture in [resolvePendingCaptures] when it
         * has no [shapeWireframe] of its own and is a capture candidate (see
         * [NodeInspection.isPixelCaptureCandidate]) - a node with a recognized drawing element
         * (`drawBehind`/`drawWithContent`/`drawWithCache` or a legacy [DrawModifier]) can't be split into
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
            shapeWireframe: CapturedWireframe.Shape?,
            inspection: NodeInspection
        ): CapturedChild? {
            if (shapeWireframe != null ||
                pendingCaptures.size >= MAX_PIXEL_CAPTURES_PER_HOST ||
                !inspection.isPixelCaptureCandidate(reflectionUtils)
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
        @Suppress("ReturnCount") // Empty batches and expired draws leave no partial capture.
        private fun resolvePendingCaptures(): Boolean {
            if (pendingCaptures.isEmpty()) return true
            val eligible = pendingCaptures.filter { request.pixelCapturePlaceholderLabelFor(it.bounds) == null }
            val bitmaps = if (eligible.isEmpty()) {
                emptyList()
            } else {
                hostRasterizer.captureRegions(hostView, eligible.map { it.hostBoundsPx }, request.shouldContinue)
            }
            if (eligible.isNotEmpty() && !request.shouldContinue()) return false
            var eligibleIndex = 0
            pendingCaptures.forEach { pending ->
                // Eligible captures retain their original order and object identity.
                val isEligible = eligible.getOrNull(eligibleIndex) === pending
                val crop = if (isEligible) bitmaps.getOrNull(eligibleIndex++) else null
                val wireframe = resolveOneCapture(pending, crop)
                wireframes += wireframe
                nodes += CapturedLayer(
                    identity = pending.nodeIdentity,
                    kind = CapturedLayerKind.COMPOSE_NODE,
                    bounds = pending.bounds,
                    children = listOf(CapturedChild.Wireframe(wireframe.identity))
                )
            }
            return true
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
                textStyle = textStyleOf(textLayoutInfo)
            )
        }

        private fun resolveShapeWireframe(
            node: SemanticsNode,
            nodeIdentity: CapturedIdentity,
            bounds: CapturedBounds,
            modifiers: List<ModifierInfo>
        ): CapturedWireframe.Shape? {
            val colorLong = semanticsUtils.resolveBackgroundColor(modifiers) ?: return null
            val cornerRadius = semanticsUtils.resolveBackgroundShape(modifiers)?.let { shape ->
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
                    backgroundColor = resolveColor(colorLong),
                    cornerRadius = cornerRadius?.toDouble()
                )
            )
        }

        /**
         * `graphicsLayer` exposes every constructor parameter it has - `alpha`, `shape`, `clip`,
         * `shadowElevation`, `spotShadowColor` included - by name through [InspectableValue.inspectableElements],
         * the same public, tooling-sanctioned mechanism Android Studio's Layout Inspector uses (see
         * `GraphicsLayerElement.inspectableProperties()` in the Compose UI source). Deliberately
         * reading everything through this one public API instead of the reflection this pipeline is
         * meant to move away from - see [com.datadog.android.sessionreplay.compose.internal.utils.BackgroundResolver]
         * for the (pre-existing, unrelated) case where a comparable direct-field read still exists.
         * Does not cover `graphicsLayer`'s animated-lambda overload (`Modifier.graphicsLayer { alpha = ... }`),
         * which exposes no public readable current value at all, reflection or otherwise. Ordered
         * clip/shadow before opacity to match the iOS composition pipeline's own documented
         * modifier ordering (clip, filters, shadow, opacity, mask).
         */
        private fun resolveModifiers(
            node: SemanticsNode,
            bounds: CapturedBounds,
            modifierInfo: List<ModifierInfo>
        ): List<CapturedModifier> {
            val modifiers = mutableListOf<CapturedModifier>()
            for (info in modifierInfo) {
                val modifier = info.modifier
                if (modifier is InspectableValue && modifier.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK) {
                    modifiers += resolveGraphicsLayerModifiers(modifier, node, bounds)
                }
            }
            return modifiers
        }

        private fun resolveGraphicsLayerModifiers(
            modifier: InspectableValue,
            node: SemanticsNode,
            bounds: CapturedBounds
        ): List<CapturedModifier> {
            val properties = GraphicsLayerProperties(modifier)
            val density = node.layoutInfo.density
            val modifiers = mutableListOf<CapturedModifier>()
            if (properties.clip) {
                resolveClipModifier(properties.shape, bounds, density)?.let { modifiers += it }
            }
            resolveColorMatrixModifier(
                platformColorMatrixValues(properties.colorFilter)
            )
                ?.let { modifiers += it }
            resolveShadowModifier(
                properties.shadowElevation,
                properties.spotShadowColor,
                density
            )?.let { modifiers += it }
            resolveBlurModifier(
                properties.renderEffect,
                density
            )?.let { modifiers += it }
            val alpha = properties.alpha
            if (alpha != null && alpha < 1f) {
                modifiers += CapturedModifier.Opacity(alpha.toDouble())
            }
            return modifiers
        }

        /**
         * A shape without meaningful rounding clips to a plain rectangle, which the existing
         * per-wireframe ancestor-bounds crop ([com.datadog.android.internal.sessionreplay.composition.CapturedClip])
         * already represents - only a genuinely rounded (or otherwise non-rectangular) shape needs
         * this layer-level modifier, since that's what the rectangular crop can't express. Only
         * [androidx.compose.foundation.shape.RoundedCornerShape] (which `CircleShape` is itself an
         * instance of) is resolved to a real radius today; any other [Shape] - a custom
         * `GenericShape`, a cut/diagonal shape, etc. - is treated as unsupported and skipped, same
         * as [com.datadog.android.sessionreplay.compose.internal.utils.BackgroundResolver.resolveCornerRadius].
         */
        @Suppress("ReturnCount") // Each guard bails out at the point it's no longer worth a Clip modifier.
        private fun resolveClipModifier(
            shape: Shape?,
            bounds: CapturedBounds,
            density: Density
        ): CapturedModifier.Clip? {
            if (shape == null) return null
            val radius = semanticsUtils.resolveCornerRadius(
                shape,
                GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
                density
            )
            if (radius <= 0f) return null
            val clampedRadius = radius.toDouble().coerceAtMost(minOf(bounds.width, bounds.height) / 2.0)
            return CapturedModifier.Clip(path = roundedRectPath(bounds.width, bounds.height, clampedRadius))
        }

        /**
         * Android's elevation shadow is a geometric light-source simulation (see hwui/Skia's
         * ambient+spot shadow renderer), not a simple offset/blur formula - there is no principled
         * way to derive [CapturedModifier.Shadow]'s offset/radius from elevation directly. This
         * instead looks up Google's own published Material Design elevation table (the same one
         * Material Components Web/MUI use to replicate Android shadows in CSS), using only its
         * dominant "key" (umbra) layer - the table's penumbra/ambient layers and its spread
         * parameter have no equivalent in [CapturedModifier.Shadow]'s single-shadow model, so this
         * is a visual approximation, not an exact reconstruction. Skipped entirely for
         * zero/negative elevation - a flat node casts no shadow. [elevationPx] is raw pixels, same
         * as `graphicsLayer`'s other size-shaped parameters.
         */
        @Suppress("ReturnCount") // Each guard bails out at the point it's no longer worth a Shadow modifier.
        private fun resolveShadowModifier(
            elevationPx: Float?,
            spotColor: Color?,
            density: Density
        ): CapturedModifier.Shadow? {
            if (elevationPx == null || elevationPx <= 0f) return null
            val elevationDp = elevationPx / density.density
            val level = elevationDp.roundToInt().coerceIn(1, MAX_ELEVATION_DP)
            val (offsetYDp, blurDp) = MATERIAL_KEY_SHADOW_DP[level]
            return CapturedModifier.Shadow(
                color = formatShadowColor(spotColor ?: Color.Black),
                offsetX = 0.0,
                offsetY = offsetYDp,
                radius = blurDp
            )
        }

        private fun formatShadowColor(color: Color): String {
            val alpha = (color.alpha * SHADOW_KEY_OPACITY.toFloat() * OPAQUE_ALPHA_VALUE).roundToInt()
            return colorStringFormatter.formatColorAndAlphaAsHexString(color.toArgb(), alpha)
        }

        /**
         * Only a [BlurEffect] has a [CapturedModifier.GaussianBlur] equivalent - any other
         * [RenderEffect] (offset, chained, or a raw platform effect) has none and resolves to null.
         * [CapturedModifier.GaussianBlur] has a single [radius][CapturedModifier.GaussianBlur.radius],
         * so an elliptical blur (`radiusX != radiusY`) - not producible by [Modifier.blur]'s own
         * single-radius overload, but possible via its two-radius one - is left unrecognized rather
         * than guessed at. [radiusX]/[radiusY] are raw pixels, same as `graphicsLayer`'s other
         * size-shaped parameters.
         */
        @Suppress("ReturnCount") // Each guard bails out at the point it's no longer worth a GaussianBlur modifier.
        private fun resolveBlurModifier(renderEffect: RenderEffect?, density: Density): CapturedModifier.GaussianBlur? {
            val blurEffect = renderEffect as? BlurEffect ?: return null
            val (radiusXPx, radiusYPx) = reflectionUtils.getBlurRadii(blurEffect) ?: return null
            if (radiusXPx != radiusYPx) return null
            return CapturedModifier.GaussianBlur(radius = (radiusXPx / density.density).toDouble())
        }
    }

    private companion object {
        const val HIDDEN_LABEL = "Hidden"
        const val CAPTURE_UNAVAILABLE_LABEL = "Content"
        const val DEFAULT_FONT_FAMILY = "roboto, sans-serif"
        const val DEFAULT_TEXT_COLOR = "#000000FF"
        const val GRAPHICS_LAYER_NAME_FALLBACK = "graphicsLayer"
        const val ALPHA_PROPERTY_NAME = "alpha"
        const val CLIP_PROPERTY_NAME = "clip"
        const val SHAPE_PROPERTY_NAME = "shape"
        const val SHADOW_ELEVATION_PROPERTY_NAME = "shadowElevation"
        const val SPOT_SHADOW_COLOR_PROPERTY_NAME = "spotShadowColor"
        const val RENDER_EFFECT_PROPERTY_NAME = "renderEffect"
        const val COLOR_FILTER_PROPERTY_NAME = "colorFilter"
        const val COMPOSE_COLOR_SHIFT = 32
        const val NODES_PER_CHECKPOINT = 200
        const val MIN_CULLING_SIBLINGS = 32
        const val MAX_CACHED_COLORS = 64

        /** Defensive cap on pixel captures per Compose host per cycle - see [NodeInspection.isPixelCaptureCandidate]. */
        const val MAX_PIXEL_CAPTURES_PER_HOST = 100

        /** The Material Design elevation table's shared "key" shadow opacity - see [resolveShadowModifier]. */
        const val SHADOW_KEY_OPACITY = 0.2
        const val MAX_ELEVATION_DP = 24

        /**
         * (offsetY, blurRadius) in dp per elevation level, 0-24 - the "key"/umbra layer only, from
         * Google's own Material Design elevation table (values from
         * https://github.com/material-components/material-components-web/blob/master/packages/mdc-elevation/_variables.scss,
         * also used by Material Components Web/MUI to replicate these shadows in CSS). offsetX is
         * always 0 at every level; the table's spread parameter has no equivalent in
         * [CapturedModifier.Shadow] and is dropped. Index 0 is unused - [resolveShadowModifier]
         * never looks up a non-positive elevation.
         */
        val MATERIAL_KEY_SHADOW_DP = listOf(
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
    }
}

/**
 * Reuses Compose-owned modifier and child lists within one capture; the next capture reads them afresh.
 * Once text and background resolution have failed, recognized drawing elements make the node a pixel
 * candidate so its drawing and descendants are preserved together. A recognized platform overscroll
 * modifier with every edge effect finished only forwards content, so its children can be traversed.
 * Otherwise a childless node is the fallback for content without a structural mapping, such as
 * Image/Icon; empty leaves may also qualify.
 */
private class NodeInspection(node: SemanticsNode, initialChildren: List<SemanticsNode>?) {
    val modifiers = node.layoutInfo.getModifierInfo()
    private val node = node
    private var resolvedChildren = initialChildren

    val children: List<SemanticsNode>
        get() = resolvedChildren ?: node.children.also { resolvedChildren = it }

    @Suppress("DEPRECATION") // Older Compose runtimes can still expose legacy DrawModifier elements.
    fun isPixelCaptureCandidate(reflectionUtils: ReflectionUtils): Boolean =
        modifiers.any {
            (it.modifier is DrawModifier && !reflectionUtils.isInactiveOverscrollModifier(it.modifier)) ||
                reflectionUtils.isDrawModifierElement(it.modifier)
        } ||
            children.isEmpty()
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
 * A closed SVG path for a [width]x[height] rectangle with all four corners rounded by [radius] -
 * the same uniform-radius-from-`topStart` simplification [SemanticsUtils.resolveCornerRadius]
 * already applies for a single wireframe's own corner radius, reused here for a clip path covering
 * a whole layer. Coordinates are local to the layer's own rectangle, per
 * [CapturedModifier.Clip]'s wire contract. [radius] is expected to already be clamped to at most
 * half of the shorter side by the caller.
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
 * `graphicsLayer`'s `colorFilter` is a public [ColorFilter], but at this module's compiled-against
 * Compose UI version it has no public subtypes or introspection of its own - this bridges to the
 * real platform [android.graphics.ColorFilter] instead, which *does* have a public, stable,
 * long-standing introspectable subtype ([android.graphics.ColorMatrixColorFilter.getColorMatrix]).
 * A platform `BlendModeColorFilter`/`PorterDuffColorFilter` (tint) or
 * [android.graphics.LightingColorFilter] has no [CapturedModifier] equivalent and resolves to null.
 * Deliberately not unit-testable: `unitTests.isReturnDefaultValues = true` makes every real
 * [android.graphics.ColorMatrix]/[android.graphics.ColorMatrixColorFilter] method silently return
 * its default instead of actually storing/reporting a matrix, so a plain JVM unit test can never
 * observe real values through the genuine platform classes either way - kept as a single small,
 * directly inlined call instead of introducing a seam whose only purpose would be working around
 * that in tests. [resolveColorMatrixModifier], which this feeds, is the actual decision logic and
 * is fully unit-tested on its own, with plain [FloatArray] inputs.
 */
private fun platformColorMatrixValues(colorFilter: ColorFilter?): FloatArray? {
    val platformFilter = colorFilter?.asAndroidColorFilter() as? AndroidColorMatrixColorFilter ?: return null
    val matrix = AndroidColorMatrix()
    platformFilter.getColorMatrix(matrix)
    return matrix.getArray()
}

private const val COLOR_MATRIX_SIZE = 20

/** [ColorMatrix.setToSaturation]'s red luminance weight - see [resolveSaturateValue]. */
private const val RED_LUMINANCE_WEIGHT = 0.213f

/** Row 1, column 0 - the off-diagonal "red" term [ColorMatrix.setToSaturation] writes, used to solve for its saturation value. */
private const val SATURATION_PROBE_INDEX = 5
private const val SATURATION_MATCH_EPSILON = 0.001f

/**
 * Turns a raw color matrix into the most specific [CapturedModifier] it matches -
 * [resolveSaturateValue] first, then [resolveBrightnessValue], falling back to the general
 * [CapturedModifier.ColorMatrix] - or null if [values] itself is null (not a color-matrix filter to
 * begin with).
 */
@Suppress("ReturnCount")
internal fun resolveColorMatrixModifier(values: FloatArray?): CapturedModifier? {
    if (values == null) return null
    resolveSaturateValue(values)?.let { return CapturedModifier.Saturate(it) }
    resolveBrightnessValue(values)?.let { return CapturedModifier.BrightnessBias(it) }
    return CapturedModifier.ColorMatrix(values.map { it.toDouble() })
}

/**
 * Compose has no dedicated saturation parameter - [ColorMatrix.setToSaturation] just builds a
 * well-known matrix shape from a single value. This reverses that: solves for the saturation value
 * implied by the matrix's own (1,0) cell, rebuilds the matrix Compose's own formula would produce
 * for that value, and only reports it as a saturation value if the two genuinely match - a custom
 * tint, a hand-built brightness shift, a YUV conversion, or any other matrix is correctly left to
 * [resolveBrightnessValue] or the general [CapturedModifier.ColorMatrix] instead.
 */
internal fun resolveSaturateValue(values: FloatArray): Double? {
    if (values.size != COLOR_MATRIX_SIZE) return null
    val sat = 1f - values[SATURATION_PROBE_INDEX] / RED_LUMINANCE_WEIGHT
    val candidate = ColorMatrix().apply { setToSaturation(sat) }
    val matches = candidate.values.indices.all { i ->
        abs(candidate.values[i] - values[i]) < SATURATION_MATCH_EPSILON
    }
    return sat.toDouble().takeIf { matches }
}

private const val BRIGHTNESS_OFFSET_INDEX = 4

/** Android's [android.graphics.ColorMatrix] translation terms operate on the 0-255 color range, not 0.0-1.0. */
private const val BRIGHTNESS_OFFSET_SCALE = 255f

/**
 * Compose has no dedicated brightness API either - mirrors [resolveSaturateValue]'s own approach,
 * hand-written rather than calling any Android/Compose API: a brightness-adjusted matrix is conventionally built as an
 * identity matrix with an equal constant added to the R/G/B translation terms (indices 4, 9, 14),
 * leaving alpha (index 19) untouched. This solves for the brightness implied by the matrix's own
 * (row 0, col 4) cell, rebuilds the matrix that value would produce, and only reports it as a
 * brightness value if the two genuinely match and the value stays within
 * [CapturedModifier.BrightnessBias]'s documented `-1.0..1.0` range - any other matrix is correctly
 * left to the general [CapturedModifier.ColorMatrix] instead.
 */
internal fun resolveBrightnessValue(values: FloatArray): Double? {
    if (values.size != COLOR_MATRIX_SIZE) return null
    val brightness = values[BRIGHTNESS_OFFSET_INDEX] / BRIGHTNESS_OFFSET_SCALE
    val candidate = brightnessColorMatrix(brightness)
    val matches = candidate.indices.all { i -> abs(candidate[i] - values[i]) < SATURATION_MATCH_EPSILON }
    return brightness.toDouble().takeIf { matches && brightness in -1f..1f }
}

@Suppress("MagicNumber") // A literal identity-plus-translation color matrix shape, not arbitrary constants.
private fun brightnessColorMatrix(brightness: Float): FloatArray {
    val offset = brightness * BRIGHTNESS_OFFSET_SCALE
    return floatArrayOf(
        1f, 0f, 0f, 0f, offset,
        0f, 1f, 0f, 0f, offset,
        0f, 0f, 1f, 0f, offset,
        0f, 0f, 0f, 1f, 0f
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

    fun captureRegions(hostView: View, regions: List<Rect>, shouldContinue: () -> Boolean): List<Bitmap?> =
        captureRegions(hostView, regions)
}

internal class DefaultComposeHostCaptureRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ComposeHostCaptureRasterizer {
    private var scratch: java.lang.ref.WeakReference<Bitmap>? = null
    private var scratchInUse = false

    @Synchronized
    @Suppress("UnsafeThirdPartyFunctionCall") // Positive dimensions; failures are caught by rasterize.
    private fun acquire(width: Int, height: Int): Bitmap {
        // The weak, blank buffer is reclaimable under pressure. Nested captures get their own buffer.
        if (!scratchInUse && width.toLong() * height * ARGB_BYTES_PER_PIXEL <= MAX_REUSABLE_BITMAP_BYTES) {
            var current = scratch?.get()
            if (current == null || current.width != width || current.height != height) {
                current?.recycle()
                current = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                scratch = java.lang.ref.WeakReference(current)
            }
            scratchInUse = true
            return current
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    @Suppress("UnsafeThirdPartyFunctionCall") // Only this rasterizer owns and recycles the scratch bitmap.
    private fun release(bitmap: Bitmap) {
        if (bitmap === scratch?.get()) {
            // Do not retain pixels from a previous screen in the reusable buffer.
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            scratchInUse = false
        } else {
            bitmap.recycle()
        }
    }

    override fun captureRegions(hostView: View, regions: List<Rect>): List<Bitmap?> =
        captureRegions(hostView, regions) { true }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned regions; skip bitmap work after expiry.
    override fun captureRegions(hostView: View, regions: List<Rect>, shouldContinue: () -> Boolean): List<Bitmap?> {
        if (!shouldContinue() || regions.none { intersectsHost(it, hostView.width, hostView.height) }) {
            return regions.map { null }
        }
        val hostBitmap = rasterize(hostView) ?: return regions.map { null }
        return try {
            // One crop canvas and two temporary rectangles per batch, never shared with nested captures.
            val canvas = Canvas()
            val source = Rect()
            val destination = Rect()
            regions.map {
                if (shouldContinue()) {
                    cropSafely(hostBitmap, it, canvas, source, destination)
                } else {
                    null
                }
            }
        } finally {
            release(hostBitmap)
        }
    }

    private fun intersectsHost(region: Rect, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && region.left.coerceIn(0, width) < region.right.coerceIn(0, width) &&
            region.top.coerceIn(0, height) < region.bottom.coerceIn(0, height)

    @Suppress("TooGenericExceptionCaught")
    private fun rasterize(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        var acquired: Bitmap? = null
        var drawn = false
        return try {
            val bitmap = acquire(view.width, view.height)
            acquired = bitmap
            @Suppress("UnsafeThirdPartyFunctionCall") // view.draw runs arbitrary custom draw code; caught below
            view.draw(Canvas(bitmap))
            drawn = true
            bitmap
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to rasterize Compose host for pixel-fallback capture" },
                e
            )
            null
        } finally {
            if (!drawn) acquired?.let { release(it) }
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned rectangle set from dimensions clamped to the source bitmap.
    private fun cropSafely(
        bitmap: Bitmap,
        boundsPx: Rect,
        canvas: Canvas,
        source: Rect,
        destination: Rect
    ): Bitmap? {
        source.set(
            boundsPx.left.coerceIn(0, bitmap.width),
            boundsPx.top.coerceIn(0, bitmap.height),
            boundsPx.right.coerceIn(0, bitmap.width),
            boundsPx.bottom.coerceIn(0, bitmap.height)
        )
        if (source.width() <= 0 || source.height() <= 0) return null
        return try {
            copyCrop(bitmap, canvas, source, destination)
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

    // Source is our own sRGB ARGB_8888 software bitmap; each crop owns independent pixels.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun copyCrop(bitmap: Bitmap, canvas: Canvas, source: Rect, destination: Rect): Bitmap {
        val crop = Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.ARGB_8888)
        var copied = false
        try {
            crop.density = bitmap.density
            crop.setHasAlpha(bitmap.hasAlpha())
            crop.isPremultiplied = bitmap.isPremultiplied
            destination.set(0, 0, crop.width, crop.height)
            canvas.setBitmap(crop)
            canvas.drawBitmap(bitmap, source, destination, null)
            copied = true
            return crop
        } finally {
            // Detach before releasing either bitmap, including when a copy fails.
            canvas.setBitmap(null)
            if (!copied) crop.recycle()
        }
    }

    private companion object {
        const val ARGB_BYTES_PER_PIXEL = 4
        const val MAX_REUSABLE_BITMAP_BYTES = 16_777_216L // 16 MiB per rasterizer, weakly held.
    }
}

private fun isFullyOutsideHostClip(bounds: CapturedBounds, clip: CapturedBounds): Boolean =
    bounds.width > 0 && bounds.height > 0 && clip.width > 0 && clip.height > 0 &&
        (
            isOutsideAxis(bounds.x, bounds.width, clip.x, clip.width) ||
                isOutsideAxis(bounds.y, bounds.height, clip.y, clip.height)
            )

private fun isOutsideAxis(start: Long, size: Long, clipStart: Long, clipSize: Long): Boolean {
    val distance = if (start >= clipStart) start - clipStart else clipStart - start
    // Overflow makes the proof uncertain; preserve that node rather than lose content.
    return distance >= 0 && distance >= if (start >= clipStart) clipSize else size
}
