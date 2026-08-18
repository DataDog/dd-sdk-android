/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.resolveComposeWindowOffset
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
 * Deliberately narrow for this initial landing: preserves text content and resolvable
 * background/shape styling, extracts `graphicsLayer` alpha as [CapturedModifier.Opacity], and hands
 * embedded interop `View`s back to native traversal. It does not attempt shadow/blur/color-matrix/
 * saturate/brightness/mask extraction - each needs its own on-device-verified conversion from
 * Compose's effect model to this module's wire-agnostic one, and does not pixel-capture unsupported
 * drawing (that capability doesn't exist yet - see the RFC's "Pixel capture and excluded surfaces"
 * workstream); a node whose own drawing effect can't be resolved as text/shape simply contributes no
 * wireframe of its own, but its children are still walked, so resolvable content beneath it is not
 * silently lost.
 */
internal class GranularComposeDecomposer(
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val reflectionUtils: ReflectionUtils = ReflectionUtils(),
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND,
    private val compatibilityGate: GranularComposeCompatibilityGate = GranularComposeCompatibilityGate.SHARED
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

    /** One [decompose] call's state - a fresh instance per call, so this class itself stays stateless. */
    private inner class DecomposeSession(
        private val hostView: View,
        private val request: CompositionHostDecomposeRequest
    ) {
        private val windowOffset = hostView.resolveComposeWindowOffset(request.screenDensity)
        private val nodes = mutableListOf<CapturedLayer>()
        private val wireframes = mutableListOf<CapturedWireframe>()

        @Suppress("ReturnCount")
        fun run(): CompositionHostDecomposeResult? {
            val root = semanticsUtils.findRootSemanticsNode(hostView) ?: return null
            val rootChildren = root.children.mapNotNull { walkNode(it) }
            if (rootChildren.isEmpty()) return null
            return CompositionHostDecomposeResult(rootChildren, nodes, wireframes)
        }

        @Suppress("ReturnCount")
        private fun walkNode(node: SemanticsNode): CapturedChild? {
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

            val children = mutableListOf<CapturedChild>()
            resolveShapeWireframe(node, nodeIdentity, bounds)?.let { shape ->
                wireframes += shape
                children += CapturedChild.Wireframe(shape.identity)
            }
            node.children.forEach { child -> walkNode(child)?.let { children += it } }

            return registerLayer(nodeIdentity, bounds, children, modifiers = resolveModifiers(node))
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
         * Both [SemanticsUtils.resolveBackgroundColor] and the `color` carried by
         * [TextLayoutInfo] are the raw internal `Color.value` bit pattern (as a `Long`), not a
         * plain packed 0xAARRGGBB value - the same shift-then-reconstruct approach the legacy
         * mapper pipeline's `AbstractSemanticsNodeMapper.convertColor` already uses in production
         * for both, reused here verbatim rather than re-derived.
         */
        private fun convertColor(rawColorValue: Long): String? {
            if (rawColorValue == SemanticsUtils.COLOR_UNSPECIFIED) return null
            val color = Color(rawColorValue shr COMPOSE_COLOR_SHIFT)
            return colorStringFormatter.formatColorAndAlphaAsHexString(
                color.toArgb(),
                (color.alpha * OPAQUE_ALPHA_VALUE).roundToInt()
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
        const val DEFAULT_FONT_FAMILY = "roboto, sans-serif"
        const val DEFAULT_TEXT_COLOR = "#000000FF"
        const val GRAPHICS_LAYER_NAME_FALLBACK = "graphicsLayer"
        const val ALPHA_PROPERTY_NAME = "alpha"
        const val COMPOSE_COLOR_SHIFT = 32
    }
}
