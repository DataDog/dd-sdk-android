/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import kotlin.math.max
import kotlin.math.min

internal data class CapturedWindow(
    val rootLayer: CapturedLayer,
    val layers: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)

/** Never reads Android UI state. Returns null when the shared generation expires. */
internal class CapturedNativeSnapshotBuilder(
    private val occlusionDetector: CapturedNativeViewOcclusionDetector = CapturedNativeViewOcclusionDetector()
) {
    @Suppress("ReturnCount") // Expiry discards all work.
    fun build(snapshot: CapturedNativeSnapshot, generation: CaptureGenerationContext): CapturedFullSnapshot? {
        val windows = mutableListOf<CapturedWindow>()
        for (window in snapshot.windows) {
            windows += buildWindow(window, generation) ?: return null
        }
        if (!generation.isActive()) return null
        val identityFactory = snapshot.identityFactory ?: DefaultCapturedIdentityFactory(snapshot.scope)

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned window lists; transform returns non-null layers.
        val layers = windows.flatMap { it.layers }

        @Suppress("UnsafeThirdPartyFunctionCall") // Owned window lists; transform returns non-null wireframes.
        val wireframes = windows.flatMap { it.wireframes }
        return CapturedFullSnapshot(
            timestamp = snapshot.timestamp,
            scope = snapshot.scope,
            root = CapturedLayer(
                identityFactory.screenRoot(),
                CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
                windows.firstOrNull()?.rootLayer?.bounds ?: CapturedBounds(0, 0, 0, 0),
                windows.map { CapturedChild.Layer(it.rootLayer.identity) }
            ),
            layers = layers,
            wireframes = wireframes + snapshot.hiddenEmbeddedWireframes
        )
    }

    @Suppress("ReturnCount") // Expiry discards all work.
    fun buildWindow(window: CapturedNativeWindow, generation: CaptureGenerationContext): CapturedWindow? {
        val layers = mutableListOf<CapturedLayer>()
        val wireframes = mutableListOf<CapturedWireframe>()

        @Suppress("UnsafeThirdPartyFunctionCall") // Empty worker-owned deque.
        val stack = ArrayDeque<PendingLayer>()
        val rootView = window.views.firstOrNull() ?: return null
        val identityFactory = DefaultCapturedIdentityFactory(rootView.identity.scope)
        val occluded = occlusionDetector.occludedViews(window, generation)
        for ((index, view) in window.views.withIndex()) {
            if (!generation.isActive()) return null
            while (stack.size > 0) {
                @Suppress("UnsafeThirdPartyFunctionCall") // Non-empty synchronous deque.
                val pending = stack.last()
                if (pending.index == view.parentIndex) break
                finishLayer(stack, layers, wireframes)
            }
            @Suppress("UnsafeThirdPartyFunctionCall") // Null returned for an empty owned deque.
            val parentClip = stack.lastOrNull()?.childClip
            val inheritedClip = if (view.clipToBounds) parentClip.intersectWith(view.bounds) else parentClip
            val clip = inheritedClip.intersectOptional(view.explicitClip)
            val children = mutableListOf<CapturedChild>()
            if (!occluded.isOccluded(index)) {
                addWireframes(view, window.screenDensity, identityFactory, clip, children, wireframes)
            }
            val childClip = clip.intersectOptional(view.childPaddingClip)
            view.composition?.let { composition ->
                // Compose leaf culling relies on this unconditional intersection with copied host bounds.
                if (!addComposition(
                        composition,
                        childClip.intersectWith(view.bounds),
                        children,
                        layers,
                        wireframes,
                        generation
                    )
                ) {
                    return null
                }
            }
            @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded worker-owned deque.
            stack.addLast(PendingLayer(index, view, children, clip, childClip))
        }
        while (stack.size > 0) {
            if (!generation.isActive()) return null
            finishLayer(stack, layers, wireframes)
        }
        if (!generation.isActive()) return null
        @Suppress("UnsafeThirdPartyFunctionCall") // Non-empty input contributes at least its root layer.
        return CapturedWindow(layers.last(), layers, wireframes)
    }

    @Suppress("LongParameterList", "UnsafeThirdPartyFunctionCall", "ReturnCount") // Expiry discards copied values.
    private fun addComposition(
        composition: CompositionHostDecomposeResult,
        clip: ClipBounds,
        children: MutableList<CapturedChild>,
        layers: MutableList<CapturedLayer>,
        wireframes: MutableList<CapturedWireframe>,
        generation: CaptureGenerationContext
    ): Boolean {
        for (layer in composition.nodes) {
            if (!generation.isActive()) return false
            layers += layer
        }
        for (wireframe in composition.wireframes) {
            if (!generation.isActive()) return false
            wireframes += wireframe.withClip(clip.clip(wireframe.bounds))
        }
        children += composition.rootChildren
        for (subtree in composition.nativeSubtrees) {
            if (!generation.isActive()) return false
            val resolved = subtree.resolve?.invoke() ?: return false
            layers += resolved.layers
            wireframes += resolved.wireframes.map { it.withClip(clip.clip(it.bounds)) }
        }
        return true
    }

    private fun addWireframes(
        view: CapturedNativeView,
        density: Float,
        identityFactory: CapturedIdentityFactory,
        clip: ClipBounds?,
        children: MutableList<CapturedChild>,
        wireframes: MutableList<CapturedWireframe>
    ) {
        val mapping = view.viewData.map(
            CapturedMappingContext(identityFactory, view.identity, density, viewScale = view.viewScale)
        )
        if (mapping !is CapturedViewMapperResult.Wireframes) return
        for (wireframe in mapping.wireframes) {
            val clipped = wireframe.withClip(wireframe.clip.intersectWith(clip?.clip(wireframe.bounds)))
                .withPermanentId(view.permanentId)
            wireframes += clipped
            children += CapturedChild.Wireframe(clipped.identity)
        }
    }

    private fun finishLayer(
        stack: ArrayDeque<PendingLayer>,
        layers: MutableList<CapturedLayer>,
        wireframes: MutableList<CapturedWireframe>
    ) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Called only after non-empty checks on this owned deque.
        val pending = stack.removeLast()
        val view = pending.view
        // Descendant coverage may cull the background, but never a foreground painted above it.
        // Use the view's own clip: clipToPadding only constrains its children.
        view.foreground?.let { foreground ->
            val wireframe = foreground.wireframe.withClip(pending.clip?.clip(foreground.wireframe.bounds))
            wireframes += wireframe
            layers += CapturedLayer(
                identity = foreground.identity,
                kind = CapturedLayerKind.COMPOSITION_LAYER,
                bounds = view.bounds,
                children = listOf(CapturedChild.Wireframe(wireframe.identity))
            )
            pending.children += CapturedChild.Layer(foreground.identity)
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // listOf wraps a non-null copied opacity.
        val modifiers = if (view.alpha == 1.0) emptyList() else listOf(CapturedModifier.Opacity(view.alpha))
        val layer = CapturedLayer(
            identity = view.identity,
            kind = view.kind,
            bounds = view.bounds,
            children = pending.children,
            modifiers = view.modifiers + modifiers
        )
        layers += layer
        @Suppress("UnsafeThirdPartyFunctionCall") // Null returned for an empty owned deque.
        val parent = stack.lastOrNull()
        parent?.children?.add(CapturedChild.Layer(layer.identity))
    }

    private data class PendingLayer(
        val index: Int,
        val view: CapturedNativeView,
        val children: MutableList<CapturedChild>,
        val clip: ClipBounds?,
        val childClip: ClipBounds?
    )
}

private fun CapturedWireframe.withClip(clip: CapturedClip?): CapturedWireframe {
    val combinedClip = when {
        clip == null -> this.clip
        this.clip == null -> clip
        else -> CapturedClip(
            top = max(this.clip?.top ?: 0, clip.top ?: 0).takeIf { it > 0 },
            bottom = max(this.clip?.bottom ?: 0, clip.bottom ?: 0).takeIf { it > 0 },
            left = max(this.clip?.left ?: 0, clip.left ?: 0).takeIf { it > 0 },
            right = max(this.clip?.right ?: 0, clip.right ?: 0).takeIf { it > 0 }
        )
    }
    return when (this) {
        is CapturedWireframe.Shape -> copy(clip = combinedClip)
        is CapturedWireframe.Text -> copy(clip = combinedClip)
        is CapturedWireframe.WebView -> copy(clip = combinedClip)
        is CapturedWireframe.Pixel -> copy(clip = combinedClip)
        is CapturedWireframe.PrivacyPlaceholder -> copy(clip = combinedClip)
        is CapturedWireframe.EmbeddedContent -> copy(clip = combinedClip)
    }
}

private fun CapturedWireframe.withPermanentId(permanentId: String?): CapturedWireframe = when {
    permanentId == null -> this
    else -> when (this) {
        is CapturedWireframe.Shape -> copy(permanentId = permanentId)
        is CapturedWireframe.Text -> copy(permanentId = permanentId)
        is CapturedWireframe.WebView -> copy(permanentId = permanentId)
        is CapturedWireframe.Pixel -> copy(permanentId = permanentId)
        is CapturedWireframe.PrivacyPlaceholder -> copy(permanentId = permanentId)
        is CapturedWireframe.EmbeddedContent -> copy(permanentId = permanentId)
    }
}

/**
 * Immutable intersection of all inherited rectangular clipping constraints. Four edges replace
 * depth-sized ancestor lists, so adding a constraint and clipping a wireframe are both O(1).
 * Right/bottom may be at or before left/top: keeping those inverted edges preserves empty clips
 * through further intersections. Null in a work item means unbounded, never empty.
 */
private class ClipBounds private constructor(
    private val left: Long,
    private val top: Long,
    private val right: Long,
    private val bottom: Long
) {
    constructor(bounds: CapturedBounds) : this(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)

    fun intersect(bounds: CapturedBounds): ClipBounds {
        val left = max(this.left, bounds.x)
        val top = max(this.top, bounds.y)
        val right = min(this.right, bounds.x + bounds.width)
        val bottom = min(this.bottom, bounds.y + bounds.height)
        val hasSameHorizontalBounds = left == this.left && right == this.right
        val hasSameVerticalBounds = top == this.top && bottom == this.bottom
        return if (hasSameHorizontalBounds && hasSameVerticalBounds) {
            this
        } else {
            ClipBounds(left, top, right, bottom)
        }
    }

    fun clip(bounds: CapturedBounds): CapturedClip? {
        val clipTop = top - bounds.y
        val clipBottom = bounds.y + bounds.height - bottom
        val clipLeft = left - bounds.x
        val clipRight = bounds.x + bounds.width - right
        val hasHorizontalClip = clipLeft > 0 || clipRight > 0
        val hasVerticalClip = clipTop > 0 || clipBottom > 0
        if (!hasHorizontalClip && !hasVerticalClip) return null
        return CapturedClip(
            top = clipTop.takeIf { it > 0 },
            bottom = clipBottom.takeIf { it > 0 },
            left = clipLeft.takeIf { it > 0 },
            right = clipRight.takeIf { it > 0 }
        )
    }
}

private fun ClipBounds?.intersectWith(bounds: CapturedBounds): ClipBounds =
    this?.intersect(bounds) ?: ClipBounds(bounds)

private fun BooleanArray.isOccluded(index: Int): Boolean = index in indices && this[index]

private fun ClipBounds?.intersectOptional(bounds: CapturedBounds?): ClipBounds? =
    bounds?.let { intersectWith(it) } ?: this
