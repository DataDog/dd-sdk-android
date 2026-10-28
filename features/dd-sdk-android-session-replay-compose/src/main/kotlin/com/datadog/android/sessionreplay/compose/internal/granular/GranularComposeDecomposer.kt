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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.toRect
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedPadding
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeBorder
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTextPosition
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTruncationMode
import com.datadog.android.internal.sessionreplay.composition.CapturedVerticalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.ImageSemanticsNodeMapper
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.editableTextCoordinates
import com.datadog.android.sessionreplay.compose.internal.utils.resolveComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.transformCapturedText
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.recorder.composition.CompositionNativeSubtree
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixColorFilter

/**
 * Walks a Compose host's unmerged semantics boundaries together with layout-only drawing nodes
 * in paint order. Optional runtime reflection recovers those visual nodes and already-applied
 * placement clips; unsupported runtimes retain the existing semantics traversal. Real semantics
 * boundaries remain authoritative for privacy, text, source images and native interop.
 *
 * Preserves text content and resolvable background/shape styling, extracts `graphicsLayer` alpha as
 * [CapturedModifier.Opacity], and hands embedded interop `View`s back to native traversal. A node
 * with a recognized custom drawing modifier and no resolved text/background, or a childless leaf
 * with nothing else resolved for it, is pixel-captured (see [NodeInspection.isPixelCaptureCandidate] and
 * [resolvePendingCaptures]): redrawn via [View.draw] into a bitmap-backed software canvas, exactly
 * like the native View pixel-fallback path, rather than reconstructed property-by-property via
 * reflection into Compose's internal drawing/painter state. Whatever a pixel capture bakes in -
 * gradients, alpha compositing, shadows, custom paint - is therefore already correct in the image
 * with no separate extraction needed. Raster capture is allowed only after checking inherited
 * privacy and restricted descendants; mixed pixels still require downstream text masking.
 * Supported source images use their existing image-privacy rules and do not require OCR.
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
    private val hostRasterizer: ComposeHostCaptureRasterizer = DefaultComposeHostCaptureRasterizer(internalLogger),
    private val textRasterizer: ComposeTextRasterizer = DefaultComposeTextRasterizer(semanticsUtils),
    private val sourceImageClipper: ComposeSourceImageClipper = DefaultComposeSourceImageClipper(),
    private val drawBehindColorResolver: ComposeDrawBehindColorResolver =
        DefaultComposeDrawBehindColorResolver(reflectionUtils),
    private val editableCoordinatesResolver: (List<ModifierInfo>) -> LayoutCoordinates? = ::editableTextCoordinates
) : CompositionHostDecomposer {
    private val visualChildrenResolver = ComposeVisualChildrenResolver()
    private val placementClipResolver = ComposePlacementClipResolver()
    private val imageMapper = ImageSemanticsNodeMapper(colorStringFormatter, semanticsUtils)

    // Only runtime linkage failures indicate an unsupported Compose binary. Ordinary bugs and
    // fatal VM errors must not permanently disable the process-wide compatibility gate.
    override fun canDecompose(view: View): Boolean {
        if (!compatibilityGate.isAvailable()) return false
        return try {
            semanticsUtils.findRootSemanticsNode(view) != null
        } catch (t: LinkageError) {
            compatibilityGate.markIncompatible(t, internalLogger)
            false
        }
    }

    override fun decompose(view: View, request: CompositionHostDecomposeRequest): CompositionHostDecomposeResult? {
        if (!compatibilityGate.isAvailable()) return null

        return try {
            DecomposeSession(semanticsUtils.findComposeRootView(view) ?: view, request).run()
        } catch (t: LinkageError) {
            compatibilityGate.markIncompatible(t, internalLogger)
            null
        }
    }

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned, guarded deque.
    override fun readTouchPrivacy(view: View, shouldContinue: () -> Boolean): Map<Rect, TouchPrivacy>? {
        if (!compatibilityGate.isAvailable()) return null
        return try {
            val root = semanticsUtils.findRootSemanticsNode(view) ?: return null
            val offset by lazy {
                (semanticsUtils.findComposeRootView(view) ?: view)
                    .resolveComposeWindowOffset(view.resources.displayMetrics.density)
            }
            val pending = ArrayDeque<SemanticsNode>()
            pending.addLast(root)
            val areas = mutableMapOf<Rect, TouchPrivacy>()
            while (pending.isNotEmpty()) {
                if (!shouldContinue()) return null
                val node = pending.removeLast()
                node.children.forEach { pending.addLast(it) }
                val privacy = semanticsUtils.getTouchPrivacyOverride(node) ?: continue
                val rect = node.boundsInRoot.toAndroidRectF().toRect()
                rect.offset(offset.xPx, offset.yPx)
                if (areas[rect] != TouchPrivacy.HIDE) areas[rect] = privacy
            }
            areas
        } catch (t: LinkageError) {
            compatibilityGate.markIncompatible(t, internalLogger)
            null
        }
    }

    private data class NodePrivacy(
        val image: ImagePrivacy,
        val text: TextAndInputPrivacy,
        val contentColor: String? = null,
        val masksInputState: Boolean = false,
        val inEditable: Boolean = false
    )

    private fun privacyOf(node: SemanticsNode, parent: NodePrivacy): NodePrivacy {
        val text = semanticsUtils.getTextAndInputPrivacyOverride(node) ?: parent.text
        return NodePrivacy(
            semanticsUtils.getImagePrivacyOverride(node) ?: parent.image,
            text,
            parent.contentColor,
            parent.masksInputState || semanticsUtils.isPassword(node) ||
                (semanticsUtils.hasInputState(node) && text != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS),
            parent.inEditable || semanticsUtils.isEditable(node)
        )
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
        val hostBoundsPx: Rect,
        val privacy: NodePrivacy
    )

    /** Rectangular Compose clips must be explicit: layer bounds do not clip their children. */
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    private fun resolveClipModifier(
        shape: Shape?,
        bounds: CapturedBounds,
        density: Density,
        layoutDirection: LayoutDirection,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0
    ): CapturedModifier.Clip? {
        if (shape == null) return null
        val radius = semanticsUtils.resolveCornerRadius(
            shape,
            GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
            density
        )
        if (radius > 0f) {
            val clamped = radius.toDouble().coerceAtMost(minOf(bounds.width, bounds.height) / 2.0)
            return CapturedModifier.Clip(
                path = roundedRectPath(bounds.width, bounds.height, clamped, offsetX, offsetY)
            )
        }
        val outline = shape.createOutline(
            Size(bounds.width * density.density, bounds.height * density.density),
            layoutDirection,
            density
        ) as? Outline.Rectangle ?: return null
        val rect = outline.rect
        val left = rect.left / density.density + offsetX
        val top = rect.top / density.density + offsetY
        val right = rect.right / density.density + offsetX
        val bottom = rect.bottom / density.density + offsetY
        return CapturedModifier.Clip("M $left,$top L $right,$top L $right,$bottom L $left,$bottom Z")
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

    private fun imageClip(
        bounds: GlobalBounds,
        visible: GlobalBounds,
        viewport: GlobalBounds = bounds
    ): CapturedClip? {
        val top = (maxOf(visible.y, viewport.y) - bounds.y).coerceIn(0L, bounds.height)
        val bottom = (bounds.y + bounds.height - minOf(visible.y + visible.height, viewport.y + viewport.height))
            .coerceIn(0L, bounds.height)
        val left = (maxOf(visible.x, viewport.x) - bounds.x).coerceIn(0L, bounds.width)
        val right = (bounds.x + bounds.width - minOf(visible.x + visible.width, viewport.x + viewport.width))
            .coerceIn(0L, bounds.width)
        if (maxOf(top, bottom) == 0L && maxOf(left, right) == 0L) return null
        return CapturedClip(
            top.takeIf { it > 0 },
            bottom.takeIf { it > 0 },
            left.takeIf { it > 0 },
            right.takeIf { it > 0 }
        )
    }

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

    /** Preserve an equivalent rounded rectangle for players rendering the flat wireframe list. */
    // Skip unrelated or non-equivalent clips in the owned modifier list.
    @Suppress("LoopWithTooManyJumpStatements", "UnsafeThirdPartyFunctionCall")
    private fun resolveSimpleClipRadius(
        node: SemanticsNode,
        bounds: CapturedBounds,
        modifiers: List<ModifierInfo>,
        windowOffset: ComposeWindowOffset,
        image: Boolean = false
    ): Float? {
        val background = drawingModifierIndex(modifiers, image)
        var radius: Float? = null
        for (index in 0 until background) {
            val info = modifiers[index]
            val modifier = info.modifier
            if (modifier !is InspectableValue || modifier.nameFallback != GRAPHICS_LAYER_NAME_FALLBACK) continue
            val properties = GraphicsLayerProperties(modifier)
            val shape = properties.shape ?: continue
            if (!properties.clip) continue
            val clipBounds = semanticsUtils.resolveModifierBounds(node, info, windowOffset) ?: continue
            // A differently positioned or sized clip requires the composition tree.
            if (clipBounds != GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)) continue
            val resolved = semanticsUtils.resolveCornerRadius(shape, clipBounds, node.layoutInfo.density)
            radius = maxOf(radius ?: 0f, resolved.coerceAtMost(minOf(bounds.width, bounds.height) / 2f))
        }
        return radius
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Scan an owned modifier list for its drawing element.
    private fun drawingModifierIndex(modifiers: List<ModifierInfo>, image: Boolean): Int = modifiers.indexOfLast {
        if (image) {
            reflectionUtils.isImagePainterModifier(
                it.modifier
            )
        } else {
            reflectionUtils.isBackgroundElement(it.modifier)
        }
    }

    private data class EditableContent(
        val source: SemanticsNode,
        val layout: TextLayoutInfo
    )

    /** One [decompose] call's state - a fresh instance per call, so this class itself stays stateless. */
    @Suppress("TooManyFunctions", "LargeClass") // One capture session owns the phases and deadline.
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
        private var textPixelCaptures = 0
        private val editableLayouts = IdentityHashMap<LayoutCoordinates, EditableContent>()
        private val nativeSubtrees = mutableListOf<CompositionNativeSubtree>()
        private val protectedRasterBounds = mutableListOf<CapturedBounds>()

        // These immutable values can be shared within this capture without retaining any UI nodes or text.
        private val resolvedColors = mutableMapOf<Long, String>()
        private var lastTextStyle: CapturedTextStyle? = null
        private var lastTextInfo: TextLayoutInfo? = null
        private var lastFallbackColor: String? = null

        private fun resolveColor(rawColor: Long): String? {
            resolvedColors[rawColor]?.let { return it }
            return convertColor(rawColor)?.also { color ->
                if (resolvedColors.size < MAX_CACHED_COLORS) resolvedColors[rawColor] = color
            }
        }

        private fun textStyleOf(info: TextLayoutInfo, fallbackColor: String?): CapturedTextStyle {
            val previous = lastTextStyle
            val last = lastTextInfo
            if (previous != null && last != null && matchesTextStyle(last, info, fallbackColor)
            ) {
                return previous
            }
            return CapturedTextStyle(
                family = when (info.fontFamily) {
                    FontFamily.SansSerif -> DEFAULT_FONT_FAMILY
                    is GenericFontFamily -> info.fontFamily.name
                    else -> DEFAULT_FONT_FAMILY
                },
                size = info.fontSize,
                color = resolveColor(info.color.toLong()) ?: fallbackColor ?: DEFAULT_TEXT_COLOR,
                truncationMode = info.textOverflow?.let { CapturedTruncationMode.valueOf(it.name) }
            ).also {
                lastTextStyle = it
                lastTextInfo = info
                lastFallbackColor = fallbackColor
            }
        }

        private fun matchesTextStyle(last: TextLayoutInfo, info: TextLayoutInfo, fallbackColor: String?): Boolean {
            val sameFont = last.fontFamily == info.fontFamily && last.fontSize == info.fontSize
            val sameColor = last.color == info.color && lastFallbackColor == fallbackColor
            return sameFont && sameColor && last.textOverflow == info.textOverflow
        }

        @Suppress("ReturnCount")
        fun run(): CompositionHostDecomposeResult? {
            val root = semanticsUtils.findRootSemanticsNode(hostView) ?: return null
            val rootChildren = mutableListOf<CapturedChild>()
            val privacy = privacyOf(root, NodePrivacy(request.imagePrivacy, request.textAndInputPrivacy)).copy(
                contentColor = semanticsUtils.resolveBackgroundColor(root)?.let { resolveColor(it) }
            )
            if (semanticsUtils.isNodeHidden(root)) {
                rootChildren +=
                    captureHiddenNode(
                        request.identityFactory.composeNode(request.hostIdentity, root.id.toString()),
                        boundsOf(root)
                    )
            } else {
                walkChildren(childrenOf(root), rootChildren, privacy)
            }
            if (aborted || (rootChildren.isEmpty() && !culledLeaf)) return null
            // The batched draw itself is one potentially-expensive operation outside the per-node
            // checkpoint loop above, and (like any draw) can't be interrupted once started - so the
            // deadline is checked once more immediately before it, mirroring the native pixel-fallback
            // path's own "these checks must occur first because a started draw cannot be interrupted"
            // rule. Skipping it here degrades this generation the same way an aborted node walk
            // already does: no partial tree, the caller's own post-call re-check discards the window.
            if (pendingCaptures.isNotEmpty() && !request.shouldContinue()) return null
            if (!resolvePendingCaptures()) return null
            return CompositionHostDecomposeResult(rootChildren, nodes, wireframes, nativeSubtrees)
        }

        @Suppress("ReturnCount")
        private fun walkNode(node: SemanticsNode, allowCulling: Boolean, inherited: NodePrivacy): CapturedChild? {
            if (!shouldVisitNode()) return null
            val nodeIdentity = request.identityFactory.composeNode(request.hostIdentity, node.id.toString())
            val bounds = boundsOf(node)
            val privacy = privacyOf(node, inherited)
            if (requiresSemanticCapture(node, privacy)) protectedRasterBounds += protectedBoundsOf(node, bounds)

            if (semanticsUtils.isNodeHidden(node)) {
                return captureHiddenNode(nodeIdentity, bounds)
            }

            val interopView = reflectionUtils.getInteropView(node)
            if (interopView != null) {
                return captureInterop(interopView, nodeIdentity, privacy)
            }

            // The native builder applies this same copied rectangle to all composition wireframes.
            // Retain parents to preserve overflowing children, and preserve privacy/interop above.
            var resolvedChildren: List<SemanticsNode>? = null
            if (allowCulling && hostClip != null && isFullyOutsideHostClip(bounds, hostClip)) {
                resolvedChildren = childrenOf(node)
                if (resolvedChildren.isEmpty()) {
                    culledLeaf = true
                    return null
                }
            }

            // ModifierInfo and children getters rebuild Compose-owned lists; read each once in this UI turn.
            val inspection = NodeInspection(node, resolvedChildren, ::childrenOf, modifiersOf(node))
            val modifiers = inspection.modifiers
            captureNodeContent(node, nodeIdentity, bounds, privacy, modifiers)?.let { return it }

            val shapeWireframe = resolveShapeWireframe(node, nodeIdentity, bounds, modifiers)
            val drawingModifiers = hasDrawingModifiers(modifiers)
            if (isEmptyVisualLayout(node, inspection, drawingModifiers)) return null
            tryQueuePixelCapture(node, nodeIdentity, bounds, shapeWireframe, inspection, privacy)
                ?.let { return it }

            val contentStart = wireframes.size
            val children = mutableListOf<CapturedChild>()
            addFallbackShape(shapeWireframe, drawingModifiers, children)
            walkChildren(
                inspection.children,
                children,
                privacy.copy(
                    contentColor =
                    shapeWireframe?.style?.backgroundColor ?: privacy.contentColor
                )
            )
            if (aborted) return null

            return captureDecoratedLayer(node, nodeIdentity, bounds, modifiers, children, contentStart)
        }

        private fun captureNodeContent(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            privacy: NodePrivacy,
            modifiers: List<ModifierInfo>
        ): CapturedChild? {
            return captureEditableLayout(node, identity, bounds, privacy, modifiers)
                ?: captureSemanticNode(node, identity, bounds, privacy, modifiers)
        }

        private fun captureSemanticNode(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            privacy: NodePrivacy,
            modifiers: List<ModifierInfo>
        ): CapturedChild? {
            if (semanticsUtils.isImage(node) || isPainterImage(node, modifiers)) {
                return captureSourceImage(node, identity, privacy, modifiers)
            }
            val progress = semanticsUtils.getProgressBarRangeInfo(node)
            return if (progress != null) {
                captureProgress(node, identity, bounds, privacy, progress, modifiers)
            } else {
                captureText(node, identity, bounds, privacy, modifiers)
            }
        }

        private fun captureInterop(
            view: View,
            nodeIdentity: CapturedIdentity,
            privacy: NodePrivacy
        ): CapturedChild? {
            val handoff = request.nativeViewHandoffWithPrivacy
            val subtree = if (handoff != null) {
                handoff(view, nodeIdentity, privacy.image, privacy.text)
            } else {
                request.nativeViewHandoff(view, nodeIdentity)
            } ?: return null
            if (subtree.resolve == null) {
                nodes += subtree.layers
                wireframes += subtree.wireframes
            } else {
                nativeSubtrees += subtree
            }
            return CapturedChild.Layer(subtree.rootLayer.identity)
        }

        /** Poll the caller's deadline between bounded node operations; never return a partial tree. */
        private fun shouldVisitNode(): Boolean {
            if (aborted) return false
            nodesVisited++
            if (nodesVisited % nodesPerCheckpoint == 0 && !request.shouldContinue()) aborted = true
            return !aborted
        }

        private fun walkChildren(
            source: List<SemanticsNode>,
            destination: MutableList<CapturedChild>,
            privacy: NodePrivacy
        ) {
            // Use the child list already needed for traversal; no scan, cache, or extra View reads.
            val allowCulling = hostClip != null && source.size >= MIN_CULLING_SIBLINGS
            for (child in source) {
                walkNode(child, allowCulling, privacy)?.let {
                    destination += withPlacementClip(child, it)
                }
                if (aborted) break
            }
        }

        @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Owned immutable layer children and clip.
        private fun withPlacementClip(node: SemanticsNode, content: CapturedChild): CapturedChild {
            val current = placementClipResolver.resolve(node, windowOffset, modifiersOf(node)) ?: return content
            val clip = resolveClipModifier(
                current.shape,
                current.bounds,
                node.layoutInfo.density,
                node.layoutInfo.layoutDirection
            ) ?: return content
            val modifiers = mutableListOf<CapturedModifier>(clip)
            val hasModifierShadow = modifiersOf(node).any {
                val value = it.modifier as? InspectableValue
                value?.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK &&
                    (GraphicsLayerProperties(value).shadowElevation ?: 0f) > 0f
            }
            if (!hasModifierShadow) {
                resolveShadowModifier(current.shadowElevation, current.spotShadowColor, node.layoutInfo.density)
                    ?.let(modifiers::add)
            }
            val identity = request.identityFactory.composeNode(content.identity, "placement-clip")
            return registerLayer(identity, current.bounds, listOf(content), modifiers)
        }

        private fun captureText(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            privacy: NodePrivacy,
            modifiers: List<ModifierInfo>
        ): CapturedChild? {
            val layout = semanticsUtils.resolveTextLayoutInfo(node, internalLogger, modifiers)
            val editable = semanticsUtils.isEditable(node)
            if (deferEditableText(node, layout, modifiers) || (layout == null && !editable)) return null
            val info = layout ?: TextLayoutInfo(
                semanticsUtils.editableText(node).orEmpty(),
                Color.Black.value,
                DEFAULT_FONT_SIZE,
                null
            )
            val textId = request.identityFactory.textWireframe(identity)
            val contentStart = wireframes.size
            val drawingModifiers = hasDrawingModifiers(modifiers)
            val children = mutableListOf<CapturedChild>()
            resolveShapeWireframe(node, identity, bounds, modifiers)?.takeUnless { drawingModifiers }?.let { shape ->
                wireframes += shape
                children += CapturedChild.Wireframe(shape.identity)
            }
            val textPixels = captureTextPixels(node, identity, info, privacy, modifiers)
            val textWireframe = textPixels ?: toTextWireframe(textId, bounds, info, node, privacy)
            wireframes += textWireframe
            children += CapturedChild.Wireframe(textWireframe.identity)
            return captureDecoratedLayer(node, identity, bounds, modifiers, children, contentStart)
        }

        @Suppress("ReturnCount") // Missing layout or coordinates retain the existing semantic fallback.
        private fun deferEditableText(
            node: SemanticsNode,
            layout: TextLayoutInfo?,
            modifiers: List<ModifierInfo>
        ): Boolean {
            if (!semanticsUtils.isEditable(node) || layout == null || childrenOf(node).isEmpty()) return false
            val coordinates = editableCoordinatesResolver(modifiers) ?: return false
            editableLayouts[coordinates] = EditableContent(node, layout)
            return true
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned map; input stays semantic.
        private fun captureEditableLayout(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            privacy: NodePrivacy,
            modifiers: List<ModifierInfo>
        ): CapturedChild? {
            val content = editableLayouts.remove(node.layoutInfo.coordinates) ?: return null
            val start = wireframes.size
            val text = toTextWireframe(
                request.identityFactory.textWireframe(identity),
                bounds,
                content.layout,
                content.source,
                privacy,
                editableBounds = bounds
            )
            wireframes += text
            return captureDecoratedLayer(
                node,
                identity,
                bounds,
                modifiers,
                listOf(CapturedChild.Wireframe(text.identity)),
                start
            )
        }

        @Suppress("ReturnCount") // Privacy and the capture budget are checked before allocating glyph pixels.
        private fun captureTextPixels(
            node: SemanticsNode,
            identity: CapturedIdentity,
            info: TextLayoutInfo,
            privacy: NodePrivacy,
            modifiers: List<ModifierInfo>
        ): CapturedWireframe.Pixel? {
            val standardFont = info.fontFamily == null || info.fontFamily == FontFamily.Default ||
                info.fontFamily is GenericFontFamily
            val textIsUnmasked = privacy.text != TextAndInputPrivacy.MASK_ALL &&
                !semanticsUtils.isEditable(node) && !privacy.inEditable && !semanticsUtils.isPassword(node) &&
                !privacy.masksInputState && !request.hasOptionSelectorParent
            val hasBudget = textPixelCaptures + pendingCaptures.size < MAX_PIXEL_CAPTURES_PER_HOST &&
                request.shouldContinue()
            val requiresPixels = !standardFont || info.requiresPixelCapture
            if (!requiresPixels || !textIsUnmasked || !hasBudget) {
                return null
            }
            val captured = textRasterizer.rasterize(node, info, modifiers, windowOffset) ?: return null
            val pixelIdentity = request.identityFactory.imageWireframe(identity)
            request.pendingPixelCaptureSink.register(
                PendingPixelCapture(pixelIdentity, identity, captured.bitmap, isTextOnly = true)
            )
            textPixelCaptures++
            return CapturedWireframe.Pixel(
                identity = pixelIdentity,
                bounds = CapturedBounds(
                    captured.bounds.x,
                    captured.bounds.y,
                    captured.bounds.width,
                    captured.bounds.height
                ),
                resource = PixelResource.Unresolved,
                clip = clipToVisible(node, captured.bounds)
            )
        }

        private fun clipToVisible(node: SemanticsNode, bounds: GlobalBounds): CapturedClip? =
            imageClip(bounds, semanticsUtils.resolveVisibleBounds(node, windowOffset))

        private fun clipToVisible(node: SemanticsNode, bounds: CapturedBounds): CapturedClip? =
            clipToVisible(node, GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height))

        private fun captureHiddenNode(identity: CapturedIdentity, bounds: CapturedBounds): CapturedChild {
            val placeholderId = request.identityFactory.placeholderWireframe(identity)
            wireframes += CapturedWireframe.PrivacyPlaceholder(placeholderId, bounds, label = HIDDEN_LABEL)
            return registerLayer(identity, bounds, listOf(CapturedChild.Wireframe(placeholderId)))
        }

        private fun captureSourceImage(
            node: SemanticsNode,
            identity: CapturedIdentity,
            privacy: NodePrivacy,
            modifiers: List<ModifierInfo>
        ): CapturedChild {
            val layoutBounds = visualLayoutBounds(node) ?: semanticsUtils.resolveUnclippedBounds(node, windowOffset)
            val painterBounds = semanticsUtils.resolvePainterBounds(node, modifiers, windowOffset)
            val painter = modifiers.firstOrNull { reflectionUtils.isImagePainterModifier(it.modifier) }
            val visibleBounds = painter?.let { semanticsUtils.resolveModifierVisibleBounds(node, it, windowOffset) }
                ?: semanticsUtils.resolveVisibleBounds(node, windowOffset)
            val bounds = CapturedBounds(layoutBounds.x, layoutBounds.y, layoutBounds.width, layoutBounds.height)
            // Resolve the same painter and scaling metadata as the existing Compose image mapper.
            // This is a source bitmap, never a screenshot of this node or its descendants.
            val image = if (privacy.image == ImagePrivacy.MASK_ALL) {
                null
            } else {
                semanticsUtils.resolveCompositionPainter(node, internalLogger)
            }

            val bitmap = image?.bitmap
            val contextualMasked = privacy.image == ImagePrivacy.MASK_LARGE_ONLY && image?.isContextualImage == true
            val wireframe = if (image != null && bitmap != null && !contextualMasked) {
                val scaled = imageMapper.calculateScaledImageBounds(
                    painterBounds,
                    image,
                    request.screenDensity
                )
                val clipped = clipSourceImage(node, modifiers, bitmap, scaled.bounds, painterBounds)
                // The clip is also retained in the composition tree. Baking it into this permitted
                // source image preserves the same pixels in players consuming only flat wireframes.
                val pixelBounds = if (clipped != null) painterBounds else scaled.bounds
                if (clipped != null && clipped !== bitmap) bitmap.recycle()
                val pixelId = request.identityFactory.imageWireframe(identity)
                request.pendingPixelCaptureSink.register(
                    PendingPixelCapture(pixelId, identity, clipped ?: bitmap, isSourceImage = true)
                )
                CapturedWireframe.Pixel(
                    pixelId,
                    CapturedBounds(pixelBounds.x, pixelBounds.y, pixelBounds.width, pixelBounds.height),
                    imageClip(pixelBounds, visibleBounds, painterBounds),
                    resource = PixelResource.Unresolved
                )
            } else {
                bitmap?.recycle()
                CapturedWireframe.PrivacyPlaceholder(
                    request.identityFactory.placeholderWireframe(identity),
                    CapturedBounds(painterBounds.x, painterBounds.y, painterBounds.width, painterBounds.height),
                    clip = imageClip(painterBounds, visibleBounds),
                    label = if (privacy.image == ImagePrivacy.MASK_LARGE_ONLY) "Content Image" else "Image"
                )
            }
            val contentStart = wireframes.size
            wireframes += wireframe
            return captureDrawingModifiers(
                node,
                identity,
                bounds,
                modifiers,
                listOf(CapturedChild.Wireframe(wireframe.identity)),
                contentStart,
                visibleBounds
            )
        }

        private fun clipSourceImage(
            node: SemanticsNode,
            modifiers: List<ModifierInfo>,
            bitmap: Bitmap,
            scaledBounds: GlobalBounds,
            painterBounds: GlobalBounds
        ): Bitmap? {
            var completed = false
            return try {
                val radius = resolveSimpleClipRadius(
                    node,
                    CapturedBounds(painterBounds.x, painterBounds.y, painterBounds.width, painterBounds.height),
                    modifiers,
                    windowOffset,
                    image = true
                )
                radius?.let {
                    sourceImageClipper.clip(bitmap, scaledBounds, painterBounds, it, request.screenDensity)
                }.also { completed = true }
            } finally {
                // This source copy has not reached the pending-capture owner if clipping failed.
                if (!completed) bitmap.recycle()
            }
        }

        private fun addFallbackShape(
            shape: CapturedWireframe.Shape?,
            drawingModifiers: Boolean,
            children: MutableList<CapturedChild>
        ) {
            if (drawingModifiers || shape == null) return
            wireframes += shape
            children += CapturedChild.Wireframe(shape.identity)
        }

        private fun captureDecoratedLayer(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            modifiers: List<ModifierInfo>,
            children: List<CapturedChild>,
            contentStart: Int
        ): CapturedChild = if (hasDrawingModifiers(modifiers)) {
            captureDrawingModifiers(node, identity, bounds, modifiers, children, contentStart)
        } else {
            registerLayer(identity, bounds, children, resolveModifiers(node, bounds, modifiers))
        }

        private fun hasDrawingModifiers(modifiers: List<ModifierInfo>): Boolean = modifiers.any {
            reflectionUtils.isBackgroundElement(it.modifier) || isBorderModifier(it.modifier) ||
                reflectionUtils.isDrawBehindElementClass(it.modifier)
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Bounded Compose-owned inspector properties.
        private fun isBorderModifier(modifier: Any): Boolean =
            modifier is InspectableValue && modifier.nameFallback == "border" &&
                modifier.inspectableElements.any { it.name == "color" || it.value is SolidColor }

        /** Rebuild drawing modifiers from the content outwards using each modifier's coordinates. */
        @Suppress("UnsafeThirdPartyFunctionCall") // Owned immutable child lists and bounded modifier traversal.
        private fun captureDrawingModifiers(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            modifiers: List<ModifierInfo>,
            content: List<CapturedChild>,
            contentStart: Int,
            visibleBounds: GlobalBounds? = null
        ): CapturedChild {
            val backgrounds = linkedMapOf<CapturedIdentity, CapturedWireframe.Shape>()
            val borders = mutableListOf<CapturedWireframe.Shape>()
            var children = content
            for (index in modifiers.indices.reversed()) {
                val info = modifiers[index]
                val modifier = info.modifier
                val graphicsLayer =
                    modifier is InspectableValue && modifier.nameFallback == GRAPHICS_LAYER_NAME_FALLBACK
                val border = isBorderModifier(modifier)
                val drawBehind = reflectionUtils.isDrawBehindElementClass(modifier)
                if (!graphicsLayer && !hasDrawingModifiers(listOf(info))) continue
                val globalBounds = semanticsUtils.resolveModifierBounds(node, info, windowOffset)
                    ?: GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)
                val modifierBounds = CapturedBounds(
                    globalBounds.x,
                    globalBounds.y,
                    globalBounds.width,
                    globalBounds.height
                )
                if (graphicsLayer) {
                    children = wrapImageModifier(
                        node,
                        request.identityFactory.composeNode(identity, "image-modifier:$index"),
                        modifier as InspectableValue,
                        modifierBounds,
                        children,
                        backgrounds
                    )
                } else if (border) {
                    resolveBorderWireframe(node, identity, index, info, modifierBounds)?.let { stroke ->
                        borders += stroke
                        children = children + CapturedChild.Wireframe(stroke.identity)
                    }
                } else {
                    val background = resolveDrawingBackground(node, identity, index, info, modifierBounds, drawBehind)
                    background?.let { shape ->
                        backgrounds[shape.identity] = shape.copy(
                            clip = imageClip(
                                globalBounds,
                                semanticsUtils.resolveModifierVisibleBounds(node, info, windowOffset)
                                    ?: visibleBounds ?: semanticsUtils.resolveVisibleBounds(node, windowOffset)
                            )
                        )
                        children = listOf(CapturedChild.Wireframe(shape.identity)) + children
                    }
                }
            }
            wireframes.addAll(contentStart, backgrounds.values.reversed())
            wireframes += borders
            return registerLayer(identity, bounds, children)
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned singleton modifier list.
        private fun resolveDrawingBackground(
            node: SemanticsNode,
            identity: CapturedIdentity,
            index: Int,
            info: ModifierInfo,
            bounds: CapturedBounds,
            drawBehind: Boolean
        ): CapturedWireframe.Shape? {
            val owner = request.identityFactory.composeNode(identity, "image-background:$index")
            if (!drawBehind) return resolveShapeWireframe(node, owner, bounds, listOf(info))
            return drawBehindColorResolver.resolve(node, info)?.let { color ->
                CapturedWireframe.Shape(
                    request.identityFactory.shapeWireframe(owner),
                    bounds,
                    style = CapturedShapeStyle(backgroundColor = resolveColor(color.value.toLong()))
                )
            }
        }

        // Guard unsupported strokes; read finite dp and Compose-owned inspector values.
        @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
        private fun resolveBorderWireframe(
            node: SemanticsNode,
            identity: CapturedIdentity,
            index: Int,
            info: ModifierInfo,
            bounds: CapturedBounds
        ): CapturedWireframe.Shape? {
            val properties = (info.modifier as InspectableValue).inspectableElements.associate { it.name to it.value }
            val width = (properties["width"] as? Dp)?.value ?: return null
            val color = properties["color"] as? Color ?: (properties["brush"] as? SolidColor)?.value ?: return null
            if (!width.isFinite() || width < 0f || color == Color.Unspecified) return null
            val radius = (properties["shape"] as? Shape)?.let {
                semanticsUtils.resolveCornerRadius(
                    it,
                    GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
                    node.layoutInfo.density
                )
            }
            val owner = request.identityFactory.composeNode(identity, "border:$index")
            return CapturedWireframe.Shape(
                request.identityFactory.shapeWireframe(owner),
                bounds,
                style = CapturedShapeStyle(cornerRadius = radius?.toDouble()),
                clip = imageClip(
                    GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
                    semanticsUtils.resolveModifierVisibleBounds(node, info, windowOffset)
                        ?: semanticsUtils.resolveVisibleBounds(node, windowOffset)
                ),
                border = CapturedShapeBorder(
                    resolveColor(color.value.toLong()) ?: return null,
                    width.roundToLong().coerceAtLeast(1)
                )
            )
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned immutable child list.
        private fun wrapImageModifier(
            node: SemanticsNode,
            identity: CapturedIdentity,
            modifier: InspectableValue,
            bounds: CapturedBounds,
            children: List<CapturedChild>,
            backgrounds: MutableMap<CapturedIdentity, CapturedWireframe.Shape>
        ): List<CapturedChild> {
            val properties = GraphicsLayerProperties(modifier)
            val captured = resolveGraphicsLayerModifiers(modifier, node, bounds, properties)
            if (captured.isEmpty()) return children
            val background = children.firstOrNull()?.identity?.let { backgrounds[it] }
            // Preserve rounding in the flat shape representation as well as the composition clip.
            if (properties.clip && background?.bounds == bounds) {
                val radius = properties.shape?.let {
                    semanticsUtils.resolveCornerRadius(
                        it,
                        GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
                        node.layoutInfo.density
                    )
                }
                backgrounds[background.identity] = background.copy(
                    style = background.style?.copy(cornerRadius = radius?.toDouble())
                )
            }
            return listOf(registerLayer(identity, bounds, children, captured))
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // isFinite operates on primitive floats.
        private fun captureProgress(
            node: SemanticsNode,
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            privacy: NodePrivacy,
            progress: androidx.compose.ui.semantics.ProgressBarRangeInfo,
            modifiers: List<ModifierInfo>
        ): CapturedChild {
            val trackId = request.identityFactory.shapeWireframe(identity)
            val trackHeight = TRACK_HEIGHT
            val track = CapturedWireframe.Shape(
                trackId,
                CapturedBounds(bounds.x, bounds.y + (bounds.height - trackHeight) / 2, bounds.width, trackHeight),
                style = CapturedShapeStyle(backgroundColor = DEFAULT_TEXT_COLOR, cornerRadius = 2L)
            )
            wireframes += track
            val children = mutableListOf<CapturedChild>(CapturedChild.Wireframe(trackId))
            val range = progress.range.endInclusive - progress.range.start
            val canShowValue = !semanticsUtils.isAdjustable(node) ||
                (privacy.text == TextAndInputPrivacy.MASK_SENSITIVE_INPUTS && !request.hasOptionSelectorParent)
            val validRange = range > 0 && range.isFinite()
            if (canShowValue && validRange && progress.current.isFinite()) {
                val fraction = ((progress.current - progress.range.start) / range).coerceIn(0f, 1f)
                val thumbOwner = request.identityFactory.composeNode(request.hostIdentity, "${node.id}:thumb")
                val thumbId = request.identityFactory.shapeWireframe(thumbOwner)
                wireframes += CapturedWireframe.Shape(
                    thumbId,
                    CapturedBounds(
                        bounds.x + (fraction * bounds.width).toLong(),
                        bounds.y + (bounds.height - THUMB_SIZE) / 2,
                        THUMB_SIZE,
                        THUMB_SIZE
                    ),
                    style = CapturedShapeStyle(backgroundColor = DEFAULT_TEXT_COLOR, cornerRadius = THUMB_SIZE / 2)
                )
                children += registerLayer(thumbOwner, bounds, listOf(CapturedChild.Wireframe(thumbId)))
            }
            return registerLayer(identity, bounds, children, resolveModifiers(node, bounds, modifiers))
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
            inspection: NodeInspection,
            privacy: NodePrivacy
        ): CapturedChild? {
            if (inspection.modifiers.any { isBorderModifier(it.modifier) }) return null
            if (shapeWireframe != null ||
                pendingCaptures.size + textPixelCaptures >= MAX_PIXEL_CAPTURES_PER_HOST ||
                !inspection.isPixelCaptureCandidate(reflectionUtils)
            ) {
                return null
            }
            if (captureLabel(privacy, bounds) != null) {
                protectedRasterBounds += bounds
                pendingCaptures += PendingCapture(nodeIdentity, bounds, Rect(), privacy)
                return CapturedChild.Layer(nodeIdentity)
            }
            // Keep restricted descendants semantic: OCR cannot hide images, control state or native interop.
            if (containsRestrictedDescendant(node, privacy)) return null
            // Compiler instrumentation can attach real semantics to a zero-height Canvas.
            // Use measured geometry for that drawing, while empty instrumented spacers stay uncaptured.
            val degenerateDrawing = (bounds.width <= 0 || bounds.height <= 0) &&
                inspection.hasDrawModifier(reflectionUtils)
            val useMeasuredBounds = node.layoutInfo in visualOnlyNodes || degenerateDrawing
            val hostBoundsPx = hostRelativeBoundsPx(node, useMeasuredBounds) ?: return null
            // Use the same visible rectangle for the crop and replay, rather than combining
            // an outer semantics position with an inner measured size. Round dimensions so a
            // 5 px divider at density 2.625 remains 2 dp instead of being truncated to 1 dp.
            val captureBounds = captureBoundsOf(hostBoundsPx)
            pendingCaptures += PendingCapture(nodeIdentity, captureBounds, hostBoundsPx, privacy)
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
            val eligible = pendingCaptures.filter { canRasterize(it) }
            if (!request.shouldContinue()) return false
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
                val padded = ComposePixelCapturePadding.pad(cropped, pending.bounds)
                if (padded.bitmap !== cropped) cropped.recycle()
                val pixelIdentity = request.identityFactory.imageWireframe(pending.nodeIdentity)
                request.pendingPixelCaptureSink.register(
                    PendingPixelCapture(
                        wireframeIdentity = pixelIdentity,
                        ownerIdentity = pending.nodeIdentity,
                        bitmap = padded.bitmap
                    )
                )
                return CapturedWireframe.Pixel(
                    identity = pixelIdentity,
                    bounds = padded.bounds,
                    clip = padded.clip,
                    resource = PixelResource.Unresolved
                )
            }
            val placeholderLabel = captureLabel(pending.privacy, pending.bounds) ?: CAPTURE_UNAVAILABLE_LABEL
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

        private val visualOnlyNodes = mutableSetOf<LayoutInfo>()
        private val modifierLists = IdentityHashMap<LayoutInfo, List<ModifierInfo>>()
        private val childLists = IdentityHashMap<LayoutInfo, List<SemanticsNode>>()

        private fun isEmptyVisualLayout(
            node: SemanticsNode,
            inspection: NodeInspection,
            drawingModifiers: Boolean
        ): Boolean {
            if (drawingModifiers || inspection.hasDrawModifier(reflectionUtils)) return false
            return node.layoutInfo in visualOnlyNodes && inspection.children.isEmpty()
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned map, used only during this capture.
        private fun modifiersOf(node: SemanticsNode): List<ModifierInfo> =
            modifierLists.getOrPut(node.layoutInfo) { node.layoutInfo.getModifierInfo() }

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned map, used only during this capture.
        private fun childrenOf(node: SemanticsNode): List<SemanticsNode> = childLists.getOrPut(node.layoutInfo) {
            visualChildrenResolver.children(node) { visualOnlyNodes += it.layoutInfo } ?: node.children
        }

        private fun isPainterImage(node: SemanticsNode, modifiers: List<ModifierInfo>): Boolean {
            if (!modifiers.any { reflectionUtils.isImagePainterModifier(it.modifier) }) return false
            val hasText = node.config.getOrNull(SemanticsProperties.Text) != null
            val hasTextOrInput = hasText || semanticsUtils.hasInputState(node)
            return !hasTextOrInput && childrenOf(node).isEmpty()
        }

        private fun visualLayoutBounds(node: SemanticsNode): GlobalBounds? =
            if (node.layoutInfo in visualOnlyNodes) semanticsUtils.resolveLayoutBounds(node, windowOffset) else null

        private fun boundsOf(node: SemanticsNode): CapturedBounds {
            val globalBounds = visualLayoutBounds(node) ?: semanticsUtils.resolveInnerBounds(node, windowOffset)
            return CapturedBounds(globalBounds.x, globalBounds.y, globalBounds.width, globalBounds.height)
        }

        private fun toTextWireframe(
            identity: CapturedIdentity,
            bounds: CapturedBounds,
            textLayoutInfo: TextLayoutInfo,
            node: SemanticsNode,
            privacy: NodePrivacy,
            editableBounds: CapturedBounds? = null
        ): CapturedWireframe.Text {
            val editable = semanticsUtils.isEditable(node)
            val contentBounds = if (editable) null else semanticsUtils.resolveTextBounds(node, windowOffset)
            val textBounds =
                editableBounds ?: contentBounds?.let { CapturedBounds(it.x, it.y, it.width, it.height) } ?: bounds
            return CapturedWireframe.Text(
                identity = identity,
                bounds = textBounds,
                clip = clipToVisible(node, textBounds),
                text = if (semanticsUtils.isPassword(node)) {
                    "***"
                } else {
                    transformCapturedText(
                        semanticsUtils.editableText(node) ?: textLayoutInfo.text,
                        privacy.text,
                        editable || privacy.inEditable || request.hasOptionSelectorParent
                    )
                },
                textStyle = textStyleOf(textLayoutInfo, privacy.contentColor).let {
                    if (editableBounds == null) {
                        it
                    } else {
                        it.copy(
                            size = (it.size * node.layoutInfo.density.fontScale).roundToLong()
                        )
                    }
                },
                textPosition = if (editable) {
                    CapturedTextPosition(
                        CapturedPadding(
                            left = if (editableBounds !=
                                null
                            ) {
                                0L
                            } else {
                                (EDIT_TEXT_START_PADDING * request.screenDensity).toLong()
                            }
                        ),
                        CapturedAlignment(CapturedHorizontalAlignment.LEFT, CapturedVerticalAlignment.CENTER)
                    )
                } else {
                    CapturedTextPosition(
                        alignment = CapturedAlignment(
                            horizontal = when (textLayoutInfo.textAlign) {
                                TextAlign.End, TextAlign.Right -> CapturedHorizontalAlignment.RIGHT
                                TextAlign.Center, TextAlign.Justify -> CapturedHorizontalAlignment.CENTER
                                else -> CapturedHorizontalAlignment.LEFT
                            }
                        )
                    )
                }
            )
        }

        private fun captureLabel(privacy: NodePrivacy, bounds: CapturedBounds): String? {
            val resolver = request.pixelCapturePlaceholderLabelForPrivacy
            if (resolver == null) return request.pixelCapturePlaceholderLabelFor(bounds)
            return resolver(privacy.image, bounds)
        }

        @Suppress("UnsafeThirdPartyFunctionCall", "ReturnCount") // Owned deque; expiry and restrictions stop the scan.
        private fun containsRestrictedDescendant(node: SemanticsNode, privacy: NodePrivacy): Boolean {
            val pending = ArrayDeque<Pair<SemanticsNode, NodePrivacy>>()
            pending.addLast(node to privacy)
            while (pending.isNotEmpty()) {
                if (!request.shouldContinue()) { aborted = true; return true }
                val (current, inherited) = pending.removeLast()
                val effective = privacyOf(current, inherited)
                if (isRasterRestricted(current, effective)) return true
                childrenOf(current).forEach { pending.addLast(it to effective) }
            }
            return false
        }

        private fun isRasterRestricted(node: SemanticsNode, privacy: NodePrivacy): Boolean =
            requiresSemanticCapture(node, privacy) || captureLabel(privacy, boundsOf(node)) != null

        // The shared host draw also paints siblings. Never crop protected sibling pixels into a resource.
        // This session owns the bounds list; each comparison checks the deadline.
        @Suppress("UnsafeThirdPartyFunctionCall")
        private fun canRasterize(capture: PendingCapture): Boolean =
            captureLabel(capture.privacy, capture.bounds) == null &&
                protectedRasterBounds.all {
                    request.shouldContinue() && isFullyOutsideHostClip(capture.bounds, it)
                }

        private fun protectedBoundsOf(node: SemanticsNode, bounds: CapturedBounds): CapturedBounds {
            if (bounds.width > 0 && bounds.height > 0) return bounds
            // A hidden zero-height sibling can still paint a hairline into a shared host crop.
            return hostRelativeBoundsPx(node, useLayoutCoordinates = true)?.let(::captureBoundsOf) ?: bounds
        }

        private fun captureBoundsOf(pixels: Rect): CapturedBounds = CapturedBounds(
            ((pixels.left + windowOffset.xPx) / request.screenDensity).roundToLong(),
            ((pixels.top + windowOffset.yPx) / request.screenDensity).roundToLong(),
            (pixels.width() / request.screenDensity).roundToLong().coerceAtLeast(1),
            (pixels.height() / request.screenDensity).roundToLong().coerceAtLeast(1)
        )

        private fun requiresSemanticCapture(node: SemanticsNode, privacy: NodePrivacy): Boolean {
            val sourceImage = semanticsUtils.isImage(node) || isPainterImage(node, modifiersOf(node))
            val semanticContent = semanticsUtils.isNodeHidden(node) || reflectionUtils.getInteropView(node) != null ||
                sourceImage
            val maskedState =
                semanticsUtils.hasInputState(node) && privacy.text != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
            val restrictedInput = privacy.masksInputState || maskedState || semanticsUtils.isPassword(node)
            return semanticContent || restrictedInput
        }

        private fun resolveShapeWireframe(
            node: SemanticsNode,
            nodeIdentity: CapturedIdentity,
            bounds: CapturedBounds,
            modifiers: List<ModifierInfo>
        ): CapturedWireframe.Shape? {
            val colorLong = semanticsUtils.resolveBackgroundColor(modifiers) ?: return null
            val backgroundRadius = semanticsUtils.resolveBackgroundShape(modifiers)?.let { shape ->
                semanticsUtils.resolveCornerRadius(
                    shape,
                    GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height),
                    node.layoutInfo.density
                )
            }
            val clipRadius = resolveSimpleClipRadius(node, bounds, modifiers, windowOffset)
            val cornerRadius = listOfNotNull(backgroundRadius, clipRadius).maxOrNull()
            return CapturedWireframe.Shape(
                identity = request.identityFactory.shapeWireframe(nodeIdentity),
                bounds = bounds,
                clip = clipToVisible(node, bounds),
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
                    val global = semanticsUtils.resolveModifierBounds(node, info, windowOffset)
                    val actual = global?.let { CapturedBounds(it.x, it.y, it.width, it.height) } ?: bounds
                    modifiers += resolveGraphicsLayerModifiers(modifier, node, actual, clipOrigin = bounds)
                }
            }
            return modifiers
        }

        private fun resolveGraphicsLayerModifiers(
            modifier: InspectableValue,
            node: SemanticsNode,
            bounds: CapturedBounds,
            properties: GraphicsLayerProperties = GraphicsLayerProperties(modifier),
            clipOrigin: CapturedBounds = bounds
        ): List<CapturedModifier> {
            val density = node.layoutInfo.density
            val modifiers = mutableListOf<CapturedModifier>()
            if (properties.clip) {
                resolveClipModifier(
                    properties.shape,
                    bounds,
                    density,
                    node.layoutInfo.layoutDirection,
                    (bounds.x - clipOrigin.x).toDouble(),
                    (bounds.y - clipOrigin.y).toDouble()
                )?.let { modifiers += it }
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
    }

    private companion object {
        const val EDIT_TEXT_START_PADDING = 4L
        const val DEFAULT_FONT_SIZE = 12L
        const val TRACK_HEIGHT = 4L
        const val THUMB_SIZE = 8L
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
private class NodeInspection(
    node: SemanticsNode,
    initialChildren: List<SemanticsNode>?,
    private val childrenOf: (SemanticsNode) -> List<SemanticsNode>,
    val modifiers: List<ModifierInfo>
) {
    private val node = node
    private var resolvedChildren = initialChildren

    val children: List<SemanticsNode>
        get() = resolvedChildren ?: childrenOf(node).also { resolvedChildren = it }

    @Suppress("DEPRECATION") // Older Compose runtimes can still expose legacy DrawModifier elements.
    fun hasDrawModifier(reflectionUtils: ReflectionUtils): Boolean = modifiers.any {
        (it.modifier is DrawModifier && !reflectionUtils.isInactiveOverscrollModifier(it.modifier)) ||
            reflectionUtils.isDrawModifierElement(it.modifier)
    }

    fun isPixelCaptureCandidate(reflectionUtils: ReflectionUtils): Boolean =
        hasDrawModifier(reflectionUtils) || children.isEmpty()
}

/**
 * [node]'s bounds in raw pixels relative to its Compose host's own origin - the same coordinate
 * space [ComposeHostCaptureRasterizer] draws the host into - for cropping, as opposed to the
 * screen-absolute dp bounds used for the wireframe itself. Null if the node's bounds can't be read
 * or are degenerate, in which case the node is left with no visual content of its own rather than
 * risk cropping a meaningless region.
 */
@Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Attached coordinates, read in the same UI-thread traversal.
private fun hostRelativeBoundsPx(node: SemanticsNode, useLayoutCoordinates: Boolean = false): Rect? {
    val boundsInRoot = if (useLayoutCoordinates) {
        val coordinates = node.layoutInfo.coordinates
        if (!coordinates.isAttached) return null
        val visible = coordinates.boundsInRoot()
        val size = coordinates.size
        if (size.width == 0 || size.height == 0) {
            val position = coordinates.positionInRoot()
            val density = node.layoutInfo.density.density
            // Hairline Canvas nodes have zero layout thickness but paint a physical pixel.
            // Crop complete dp cells around that pixel so its bitmap is never stretched.
            androidx.compose.ui.geometry.Rect(
                if (size.width == 0) kotlin.math.floor((position.x - 1f) / density) * density else position.x,
                if (size.height == 0) kotlin.math.floor((position.y - 1f) / density) * density else position.y,
                if (size.width ==
                    0
                ) {
                    kotlin.math.ceil((position.x + 1f) / density) * density
                } else {
                    position.x + size.width
                },
                if (size.height ==
                    0
                ) {
                    kotlin.math.ceil((position.y + 1f) / density) * density
                } else {
                    position.y + size.height
                }
            )
        } else {
            visible
        }
    } else {
        node.boundsInRoot
    }
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
private fun roundedRectPath(
    width: Long,
    height: Long,
    radius: Double,
    x: Double = 0.0,
    y: Double = 0.0
): String {
    if (x == 0.0 && y == 0.0) {
        val w = width.toDouble()
        val h = height.toDouble()
        return "M $radius,0 " +
            "L ${w - radius},0 A $radius,$radius 0 0 1 $w,$radius " +
            "L $w,${h - radius} A $radius,$radius 0 0 1 ${w - radius},$h " +
            "L $radius,$h A $radius,$radius 0 0 1 0,${h - radius} " +
            "L 0,$radius A $radius,$radius 0 0 1 $radius,0 Z"
    }
    val right = x + width
    val bottom = y + height
    return "M ${x + radius},$y " +
        "L ${right - radius},$y A $radius,$radius 0 0 1 $right,${y + radius} " +
        "L $right,${bottom - radius} A $radius,$radius 0 0 1 ${right - radius},$bottom " +
        "L ${x + radius},$bottom A $radius,$radius 0 0 1 $x,${bottom - radius} " +
        "L $x,${y + radius} A $radius,$radius 0 0 1 ${x + radius},$y Z"
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
            // Canvas failures propagate to the capture entry point; view.draw runs app code.
            @Suppress("UnsafeThirdPartyFunctionCall")
            val canvas = Canvas(bitmap)
            try {
                @Suppress("UnsafeThirdPartyFunctionCall")
                view.draw(canvas)
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
            }
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
