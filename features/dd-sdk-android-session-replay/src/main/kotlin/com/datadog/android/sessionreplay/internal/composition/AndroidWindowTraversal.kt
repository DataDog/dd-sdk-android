/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
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
 * text styling and wireframe/layer construction operate on the copy on the processing executor.
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
    private val composeHostCallback: CapturedInteropViewCallback? = null,
    private val drawingOrderResolver: ViewGroupDrawingOrderResolver = ViewGroupDrawingOrderResolver(internalLogger)
) {
    private val occlusionDetector = ViewOcclusionDetector(drawableToColorMapper, viewUtilsInternal, internalLogger)

    @UiThread
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // UI-owned deque/map; pops are guarded by isNotEmpty.
    fun traverseWindow(
        windowRoot: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext
    ): WindowCopyResult {
        val state = CopyState(windowRoot.resources.displayMetrics.density, windowIdentity, identityFactory)
        val stack = ArrayDeque<WorkItem>()
        stack.addLast(WorkItem(windowRoot, -1, clipToBounds = true))
        while (stack.isNotEmpty()) {
            if (!context.shouldContinue()) return WindowCopyResult.Aborted
            val item = stack.removeLast()
            if (!tryVisit(item, state)) continue
            collectTouchOverrideArea(item.view, state.touchOverrideAreas)
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
            WindowCopyResult.Present(CapturedNativeWindow(state.density, state.views), state.touchOverrideAreas)
        }
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // The identity map is owned by this synchronous walk.
    private fun tryVisit(item: WorkItem, state: CopyState): Boolean {
        val view = item.view
        return !viewUtilsInternal.isNotVisible(view) && !viewUtilsInternal.isSystemNoise(view) &&
            !viewUtilsInternal.isOnSecondaryDisplay(view) && view.parent === item.expectedParent &&
            state.visited.put(view, true) == null
    }

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
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, state.density).toCaptured()
        val mappingContext = CapturedMappingContext(state.identityFactory, identity, state.density, bounds)
        val hidden = view.getTag(R.id.datadog_hidden) == true
        val interop = composeHostCallback?.takeIf {
            !hidden && view.javaClass.name == COMPOSE_VIEW_CLASS_NAME
        }?.map(view, mappingContext)
        if (!context.shouldContinue()) return
        val mapping = interop?.let { CapturedViewData.Mapped(it) }
            ?: (if (hidden) hiddenViewMapper else mapperRegistry.resolve(view)).capture(view, mappingContext)
        val children = if (!hidden && interop == null && !mapping.isTerminal) view as? ViewGroup else null
        val paddingClip = if (children != null && children.clipToPadding && children.hasNonZeroPadding()) {
            viewBoundsResolver.resolveViewPaddedBounds(children, state.density).toCaptured()
        } else {
            null
        }
        val index = state.views.size
        state.views += CapturedNativeView(
            identity,
            item.parentIndex,
            bounds,
            view.alpha.toDouble(),
            mapping,
            item.clipToBounds,
            view.clipBoundsOnScreen(state.density),
            paddingClip
        )
        if (children != null) {
            pushChildren(children, index, stack)
        } else if (hidden && view is ViewGroup) {
            pushTouchChildren(view, stack)
        }
    }

    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // addLast cannot fail for this unbounded, UI-owned deque.
    private fun pushChildren(viewGroup: ViewGroup, parentIndex: Int, stack: ArrayDeque<WorkItem>) {
        val drawingOrder = drawingOrderResolver.resolve(viewGroup)
        val clipChildren = viewGroup.clipChildren
        val occluded = if (clipChildren) occlusionDetector.occludedChildIndices(drawingOrder) else emptySet()
        for (i in drawingOrder.children.indices.reversed()) {
            stack.addLast(WorkItem(drawingOrder.children[i], parentIndex, clipChildren, i in occluded))
        }
    }

    // Occlusion affects drawing, not touch delivery: keep collecting tags throughout covered subtrees.
    @UiThread
    @Suppress("UnsafeThirdPartyFunctionCall") // addLast cannot fail for this unbounded, UI-owned deque.
    private fun pushTouchChildren(viewGroup: ViewGroup, stack: ArrayDeque<WorkItem>) {
        if (viewGroup is WebView) return
        val children = drawingOrderResolver.resolve(viewGroup).children
        for (i in children.indices.reversed()) stack.addLast(WorkItem(children[i], -1, false, touchOnly = true))
    }

    // MotionEvents report raw screen pixels, so the override area must come from the view's actual
    // on-screen geometry rather than the density-scaled CapturedBounds used for wireframes.
    @UiThread
    private fun collectTouchOverrideArea(view: View, touchOverrideAreas: MutableMap<Rect, TouchPrivacy>) {
        collectTouchOverrideArea(view, touchOverrideAreas, internalLogger)
    }

    private class CopyState(
        val density: Float,
        val windowIdentity: CapturedIdentity,
        val identityFactory: CapturedIdentityFactory
    ) {
        val views = mutableListOf<CapturedNativeView>()
        val touchOverrideAreas = mutableMapOf<Rect, TouchPrivacy>()
        val visited = IdentityHashMap<View, Boolean>()
    }

    private data class WorkItem(
        val view: View,
        val parentIndex: Int,
        val clipToBounds: Boolean,
        val touchOnly: Boolean = false,
        val expectedParent: ViewParent? = view.parent
    )

    private companion object {
        const val COMPOSE_VIEW_CLASS_NAME = "androidx.compose.ui.platform.ComposeView"
    }
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
