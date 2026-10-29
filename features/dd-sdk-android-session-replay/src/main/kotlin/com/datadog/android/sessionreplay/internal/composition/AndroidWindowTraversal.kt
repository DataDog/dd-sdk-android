/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedHiddenViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.IdentityHashMap

internal sealed interface WindowCopyResult {
    data class Present(
        val window: CapturedNativeWindow,
        val touchOverrideAreas: Map<Rect, TouchPrivacy>
    ) : WindowCopyResult
    object Filtered : WindowCopyResult
    object Aborted : WindowCopyResult
}

/**
 * Copies one native window in a single UI-thread turn. Only this phase reads live Views; clipping,
 * text styling and wireframe/layer construction operate on the copy on the construction executor.
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
    private val interopViewCallback: CapturedInteropViewCallback? = null,
    private val drawingOrderResolver: ViewGroupDrawingOrderResolver = ViewGroupDrawingOrderResolver(internalLogger)
) {
    private val occlusionDetector = ViewOcclusionDetector(drawableToColorMapper, viewUtilsInternal, internalLogger)

    @UiThread
    @Suppress("ReturnCount") // Expiry discards all work.
    fun traverseWindow(
        windowRoot: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext
    ): WindowCopyResult {
        val state = CopyState(windowRoot.resources.displayMetrics.density, windowIdentity, identityFactory)

        @Suppress("UnsafeThirdPartyFunctionCall") // Empty UI-owned deque.
        val stack = ArrayDeque<WorkItem>()
        @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded UI-owned deque.
        stack.addLast(WorkItem(windowRoot, -1, clipToBounds = true))
        while (stack.size > 0) {
            if (!context.shouldContinue()) return WindowCopyResult.Aborted
            @Suppress("UnsafeThirdPartyFunctionCall") // Non-empty check above; synchronous ownership.
            val item = stack.removeLast()
            if (!tryVisit(item, state)) continue
            // Separate surfaces remain uncertain even inside a subtree omitted by sibling culling.
            state.isDrawingOrderReliable = state.isDrawingOrderReliable && item.view.sharesNativePaintOrder()
            collectTouchOverrideArea(item.view, state.touchOverrideAreas, internalLogger)
            if (item.touchOnly) {
                (item.view as? ViewGroup)?.let { pushTouchChildren(it, stack) }
            } else {
                copyView(item, stack, state, context)
            }
        }
        if (!context.shouldContinue()) return WindowCopyResult.Aborted
        return if (state.views.isEmpty()) {
            WindowCopyResult.Filtered
        } else {
            // Lists are owned by this copy and are never mutated after this handoff.
            WindowCopyResult.Present(
                CapturedNativeWindow(state.density, state.views, state.isDrawingOrderReliable),
                state.touchOverrideAreas
            )
        }
    }

    @UiThread
    private fun tryVisit(item: WorkItem, state: CopyState): Boolean {
        val view = item.view
        val isFiltered = viewUtilsInternal.isNotVisible(view) || viewUtilsInternal.isSystemNoise(view)
        if (isFiltered || viewUtilsInternal.isOnSecondaryDisplay(view) ||
            !(view.parent isSameInstanceAs item.expectedParent)
        ) {
            return false
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // UI-owned IdentityHashMap accepts this non-null key/value.
        return state.visited.put(view, true) == null
    }

    // Queued work belongs to one parent instance, even when distinct parents compare equal.
    private infix fun ViewParent?.isSameInstanceAs(other: ViewParent?): Boolean = this === other

    @UiThread
    private fun copyView(
        item: WorkItem,
        stack: ArrayDeque<WorkItem>,
        state: CopyState,
        context: CaptureGenerationContext
    ) {
        val view = item.view
        val identity = if (item.parentIndex < 0) {
            state.windowIdentity
        } else {
            state.identityFactory.view(state.windowIdentity, viewIdentifierResolver.resolveViewId(view).toString())
        }
        // Read geometry once. Retain the original pixels for coverage and derive the same replay bounds.
        val pixels = resolvePixelBounds(view, state.locationOnScreen)
        val bounds = pixels?.toNormalizedBounds(state.density)
            ?: viewBoundsResolver.resolveViewGlobalBounds(view, state.density).toCaptured()
        val mappingContext = CapturedMappingContext(state.identityFactory, identity, state.density, bounds)
        val hidden = view.getTag(R.id.datadog_hidden) == true
        val interop = if (hidden) null else interopViewCallback?.tryMap(view, mappingContext)
        if (!context.shouldContinue()) return
        val viewData = interop?.let { CapturedViewData.Mapped(it) }
            ?: (if (hidden) hiddenViewMapper else mapperRegistry.resolve(view)).capture(view, mappingContext)
        val children = if (!hidden && interop == null && !viewData.isTerminal) view as? ViewGroup else null
        val pixelPaddingClip = children?.let { pixels?.paddingClip(it) }
        val paddingClip = resolvePaddingClip(children, pixelPaddingClip, state.density)
        val pixelClip = pixels?.explicitClip(view)
        val clip = resolveExplicitClip(view, pixels, pixelClip, state.density)
        val geometryIsReliable = item.isGeometryReliable && view.hasReliableGeometry(item.parentIndex)
        val index = state.views.size
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
            geometryIsReliable
        )
        if (children != null) {
            pushChildren(children, index, geometryIsReliable, stack, state)
        } else {
            pushHiddenTouchChildren(view, hidden, stack)
        }
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
        density: Float
    ): CapturedBounds? = if (pixels != null) clip?.toNormalizedClip(density) else view.clipBoundsOnScreen(density)

    @UiThread
    private fun pushChildren(
        viewGroup: ViewGroup,
        parentIndex: Int,
        geometryIsReliable: Boolean,
        stack: ArrayDeque<WorkItem>,
        state: CopyState
    ) {
        val drawingOrder = drawingOrderResolver.resolve(viewGroup)
        state.isDrawingOrderReliable = state.isDrawingOrderReliable && drawingOrder.isReliable
        val clipChildren = viewGroup.clipChildren
        val occluded = if (clipChildren) occlusionDetector.occludedChildIndices(drawingOrder) else emptySet()
        for (i in drawingOrder.children.indices.reversed()) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded UI-owned deque.
            stack.addLast(
                WorkItem(drawingOrder.children[i], parentIndex, clipChildren, i in occluded, geometryIsReliable)
            )
        }
    }

    // Occlusion affects drawing, not touch delivery: keep collecting tags throughout covered subtrees.
    @UiThread
    private fun pushTouchChildren(viewGroup: ViewGroup, stack: ArrayDeque<WorkItem>) {
        if (viewGroup is WebView) return
        val children = drawingOrderResolver.resolve(viewGroup).children
        for (i in children.indices.reversed()) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Unbounded UI-owned deque.
            stack.addLast(WorkItem(children[i], -1, false, touchOnly = true))
        }
    }

    /** Visual hiding still allows descendant touch overrides; WebView internals remain opaque. */
    @UiThread
    private fun pushHiddenTouchChildren(view: View, hidden: Boolean, stack: ArrayDeque<WorkItem>) {
        if (hidden && view is ViewGroup) pushTouchChildren(view, stack)
    }

    private class CopyState(
        val density: Float,
        val windowIdentity: CapturedIdentity,
        val identityFactory: CapturedIdentityFactory
    ) {
        var isDrawingOrderReliable = true
        val locationOnScreen = IntArray(2)
        val views = mutableListOf<CapturedNativeView>()
        val touchOverrideAreas = mutableMapOf<Rect, TouchPrivacy>()
        val visited = IdentityHashMap<View, Boolean>()
    }

    private data class WorkItem(
        val view: View,
        val parentIndex: Int,
        val clipToBounds: Boolean,
        val touchOnly: Boolean = false,
        val isGeometryReliable: Boolean = true,
        val expectedParent: ViewParent? = view.parent
    )
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

private fun View.hasReliableGeometry(parentIndex: Int): Boolean =
    (parentIndex < 0 || !clipToOutline) && hasStableGeometry()

private fun GlobalBounds.paddingClip(view: ViewGroup): GlobalBounds? =
    if (view.clipToPadding && view.hasNonZeroPadding()) paddedBounds(view) else null
