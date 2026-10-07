/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedHiddenViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.SnapshotProducer.Companion.INVALID_PRIVACY_LEVEL_ERROR
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal sealed interface WindowWalkResult {
    data class Present(
        val rootLayer: CapturedLayer,
        val layers: List<CapturedLayer>,
        val wireframes: List<CapturedWireframe>,
        val touchOverrideAreas: Map<Rect, TouchPrivacy>
    ) : WindowWalkResult

    /** The window (e.g. not shown, or on a secondary display) contributes nothing - skip it. */
    object Filtered : WindowWalkResult

    /** The deadline expired mid-walk - the whole capture must be discarded, not just this window. */
    object Aborted : WindowWalkResult
}

/**
 * Walks one window's native View hierarchy into a [CapturedLayer] tree, combining legacy
 * `TreeViewTraversal` (per-view decisions) and `SnapshotProducer` (recursion) into a single pass,
 * with clip computed inline from an accumulated ancestor intersection instead of a separate flatten step.
 *
 * Every visited [View] becomes exactly one [CapturedLayer] (kind [CapturedLayerKind.NATIVE_VIEW],
 * or [CapturedLayerKind.WINDOW_ROOT] for the window's own root). Its identity is always created via
 * [CapturedIdentityFactory.view] against the *window's* identity - never the view's real structural
 * parent - per the flat-namespacing rule [CapturedIdentityFactory] enforces; real nesting is carried
 * purely by [CapturedLayer.children].
 *
 * The walk is iterative, not recursive: an explicit [WorkItem] stack stands in for the call stack a
 * plain recursive walk would use, specifically so it can pause between any two views - see
 * [com.datadog.android.sessionreplay.internal.composition.CaptureGenerationContext.sliceClock] - and
 * resume later exactly where it left off, instead of holding the main thread for a whole window (or
 * a single expensive subtree within one) in one uninterrupted pass.
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

    private val occlusionDetector = ViewOcclusionDetector(
        viewBoundsResolver = viewBoundsResolver,
        drawableToColorMapper = drawableToColorMapper,
        viewUtilsInternal = viewUtilsInternal,
        internalLogger = internalLogger
    )

    @UiThread
    fun traverseWindow(
        windowRoot: View,
        windowIdentity: CapturedIdentity,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext
    ): CaptureStep<WindowWalkResult> {
        val state = TraversalState(screenDensity = windowRoot.resources.displayMetrics.density)
        val stack = newWorkStack<WorkItem>()
        stack.push(
            WorkItem.Visit(
                view = windowRoot,
                ownIdentity = windowIdentity,
                ownKind = CapturedLayerKind.WINDOW_ROOT,
                windowIdentity = windowIdentity,
                identityFactory = identityFactory,
                ancestorClip = null,
                clipToBounds = true,
                sink = ResultSink.Root
            )
        )
        return runStack(stack, state, context)
    }

    /**
     * Drains [stack] one [WorkItem] at a time. Checked before every pop, not just periodically:
     * [CaptureGenerationContext.shouldContinue] can still discard the whole walk on an expired
     * deadline, while [com.datadog.android.sessionreplay.internal.composition.SliceYieldClock.shouldYield]
     * instead pauses it - handing back a continuation that resumes this exact call with the same
     * (mutated) [stack] and [state], picking up from precisely where it left off.
     */
    @Suppress("ReturnCount")
    @UiThread
    private fun runStack(
        stack: ArrayDeque<WorkItem>,
        state: TraversalState,
        context: CaptureGenerationContext
    ): CaptureStep<WindowWalkResult> {
        while (stack.isNotStackEmpty()) {
            if (!context.shouldContinue()) return CaptureStep.Done(WindowWalkResult.Aborted)
            if (context.sliceClock.shouldYield()) {
                return CaptureStep.Yielded { runStack(stack, state, context) }
            }
            when (val item = stack.pop()) {
                is WorkItem.Visit -> {
                    val aborted = visitItem(item, stack, state, context)
                    if (aborted) return CaptureStep.Done(WindowWalkResult.Aborted)
                }
                is WorkItem.VisitOccluded -> visitOccluded(item, stack, state)
                is WorkItem.Finish ->
                    completeLayer(item.ownIdentity, item.ownKind, item.bounds, item.children, item.sink, state)
            }
        }
        // The root Visit is the only one with a Root sink and always completes a layer, setting
        // rootLayer, unless filtered - the only other way this loop empties without it.
        return CaptureStep.Done(
            state.rootLayer?.let {
                WindowWalkResult.Present(it, state.layers, state.wireframes, state.touchOverrideAreas)
            }
                ?: WindowWalkResult.Filtered
        )
    }

    /** `true` only when the whole window walk must be discarded - a compose host handoff outlived the deadline. */
    @Suppress("ReturnCount")
    @UiThread
    private fun visitItem(
        item: WorkItem.Visit,
        stack: ArrayDeque<WorkItem>,
        state: TraversalState,
        context: CaptureGenerationContext
    ): Boolean {
        val view = item.view
        if (isFiltered(view)) return false

        collectTouchOverrideArea(view, state)

        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, state.screenDensity).toCaptured()
        val mappingContext = CapturedMappingContext(item.identityFactory, item.ownIdentity, state.screenDensity)
        val isHidden = view.getTag(R.id.datadog_hidden) == true

        val children = mutableListOf<CapturedChild>()
        val interopResult = composeHostCallback?.takeIf { isComposeHost(view) }?.map(view, mappingContext)
        if (interopResult != null && !context.shouldContinue()) return true
        val mapped = interopResult
            ?: (if (isHidden) hiddenViewMapper else mapperRegistry.resolve(view)).map(view, mappingContext)
        // clipChildren belongs to the containing ViewGroup: it clips this View's entire drawing
        // (including descendants) to this View's bounds. A window root is always bounded by its surface.
        val clip = if (item.clipToBounds) item.ancestorClip.intersectWith(bounds) else item.ancestorClip
        addWireframes(mapped, clip, children, state)

        val canHaveChildren = !isHidden && interopResult == null
        if (canHaveChildren && view is ViewGroup && view.childCount > 0) {
            stack.push(WorkItem.Finish(item.ownIdentity, item.ownKind, bounds, children, item.sink))
            pushChildren(view, item, state.screenDensity, clip, children, stack)
        } else {
            completeLayer(item.ownIdentity, item.ownKind, bounds, children, item.sink, state)
        }
        return false
    }

    private fun completeLayer(
        identity: CapturedIdentity,
        kind: CapturedLayerKind,
        bounds: CapturedBounds,
        children: MutableList<CapturedChild>,
        sink: ResultSink,
        state: TraversalState
    ) {
        val layer = CapturedLayer(identity = identity, kind = kind, bounds = bounds, children = children)
        state.layers.add(layer)
        when (sink) {
            is ResultSink.ChildOf -> sink.list.add(CapturedChild.Layer(identity))
            ResultSink.Root -> state.rootLayer = layer
        }
    }

    /**
     * A Compose host's interior is Compose's own node tree, not further Android child Views - its
     * content is fully described by whatever `composeHostCallback` returned in [visitItem], which
     * is why that case never reaches here. Pushed in reverse child order so the stack (LIFO) pops
     * them back out in effective painting order (custom order, then stable Z ordering).
     */
    @UiThread
    private fun pushChildren(
        viewGroup: ViewGroup,
        parent: WorkItem.Visit,
        screenDensity: Float,
        ancestorClip: ClipBounds?,
        appendTo: MutableList<CapturedChild>,
        stack: ArrayDeque<WorkItem>
    ) {
        val drawingOrder = drawingOrderResolver.resolve(viewGroup)
        val clipChildren = viewGroup.clipChildren
        // A covered child rectangle does not imply its overflowing subtree is covered.
        val occludedIndices = if (clipChildren) {
            occlusionDetector.occludedChildIndices(drawingOrder, screenDensity)
        } else {
            emptySet()
        }
        val childClip = if (viewGroup.clipToPadding && viewGroup.hasNonZeroPadding()) {
            ancestorClip.intersectWith(
                viewBoundsResolver.resolveViewPaddedBounds(viewGroup, screenDensity).toCaptured()
            )
        } else {
            ancestorClip
        }
        for (i in drawingOrder.children.indices.reversed()) {
            val child = drawingOrder.children[i]
            if (i in occludedIndices) {
                // No identity is minted here: an occluded child's identity is never read, since it
                // never reaches completeLayer() - see visitOccluded().
                stack.push(WorkItem.VisitOccluded(child, parent.windowIdentity, parent.identityFactory))
            } else {
                val childIdentity = parent.identityFactory.view(
                    parent.windowIdentity,
                    viewIdentifierResolver.resolveViewId(child).toString()
                )
                stack.push(
                    WorkItem.Visit(
                        view = child,
                        ownIdentity = childIdentity,
                        ownKind = CapturedLayerKind.NATIVE_VIEW,
                        windowIdentity = parent.windowIdentity,
                        identityFactory = parent.identityFactory,
                        ancestorClip = childClip,
                        clipToBounds = clipChildren,
                        sink = ResultSink.ChildOf(appendTo)
                    )
                )
            }
        }
    }

    private fun isFiltered(view: View): Boolean =
        viewUtilsInternal.isNotVisible(view) ||
            viewUtilsInternal.isSystemNoise(view) ||
            viewUtilsInternal.isOnSecondaryDisplay(view)

    /**
     * A visually occluded view contributes nothing to the final image, so it's never mapped or
     * turned into a wireframe/layer. It can still receive touches, though - occlusion only
     * guarantees nothing paints on screen, not that the content covering it actually consumes touch
     * events, since a non-interactive covering view lets touches fall through to whatever is beneath
     * it - so this still visits the whole subtree to collect touch-privacy tags.
     */
    @UiThread
    private fun visitOccluded(
        item: WorkItem.VisitOccluded,
        stack: ArrayDeque<WorkItem>,
        state: TraversalState
    ) {
        val view = item.view
        if (isFiltered(view)) return
        collectTouchOverrideArea(view, state)
        if (view !is ViewGroup) return
        val children = drawingOrderResolver.resolve(view).children
        for (i in children.indices.reversed()) {
            val child = children[i]
            stack.push(WorkItem.VisitOccluded(child, item.windowIdentity, item.identityFactory))
        }
    }

    private fun addWireframes(
        result: CapturedViewMapperResult,
        ancestorClip: ClipBounds?,
        children: MutableList<CapturedChild>,
        state: TraversalState
    ) {
        if (result !is CapturedViewMapperResult.Wireframes) return
        for (wireframe in result.wireframes) {
            val clipped = wireframe.withClip(ancestorClip?.clip(wireframe.bounds))
            state.wireframes.add(clipped)
            children.add(CapturedChild.Wireframe(clipped.identity))
        }
    }

    // MotionEvents report raw screen pixels, so the override area must come from the view's actual
    // on-screen geometry rather than the density-scaled CapturedBounds used for wireframes.
    @UiThread
    private fun collectTouchOverrideArea(view: View, state: TraversalState) {
        val touchPrivacyTag = view.getTag(R.id.datadog_touch_privacy) ?: return
        val locationOnScreen = IntArray(2)
        @Suppress("UnsafeThirdPartyFunctionCall") // this will always have size >= 2
        view.getLocationOnScreen(locationOnScreen)
        val x = locationOnScreen[0]
        val y = locationOnScreen[1]
        val viewArea = Rect(
            x - view.paddingLeft,
            y - view.paddingTop,
            x + view.width + view.paddingRight,
            y + view.height + view.paddingBottom
        )

        try {
            val privacyLevel = TouchPrivacy.valueOf(touchPrivacyTag.toString().uppercase(Locale.US))
            state.touchOverrideAreas[viewArea] = privacyLevel
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { INVALID_PRIVACY_LEVEL_ERROR },
                e
            )
        }
    }

    private sealed interface WorkItem {
        data class Visit(
            val view: View,
            val ownIdentity: CapturedIdentity,
            val ownKind: CapturedLayerKind,
            val windowIdentity: CapturedIdentity,
            val identityFactory: CapturedIdentityFactory,
            val ancestorClip: ClipBounds?,
            val clipToBounds: Boolean,
            val sink: ResultSink
        ) : WorkItem

        /**
         * A view inside an occluded subtree - deliberately carries no identity, bounds, or sink:
         * [visitOccluded] never maps it or builds a layer, only walks it for touch-privacy tags.
         */
        data class VisitOccluded(
            val view: View,
            val windowIdentity: CapturedIdentity,
            val identityFactory: CapturedIdentityFactory
        ) : WorkItem

        data class Finish(
            val ownIdentity: CapturedIdentity,
            val ownKind: CapturedLayerKind,
            val bounds: CapturedBounds,
            val children: MutableList<CapturedChild>,
            val sink: ResultSink
        ) : WorkItem
    }

    private sealed interface ResultSink {
        data class ChildOf(val list: MutableList<CapturedChild>) : ResultSink
        object Root : ResultSink
    }

    private class TraversalState(val screenDensity: Float) {
        var rootLayer: CapturedLayer? = null
        val layers = mutableListOf<CapturedLayer>()
        val wireframes = mutableListOf<CapturedWireframe>()

        // Owned by this walk's continuation. Cancellation can drop it without touching another capture.
        val touchOverrideAreas = mutableMapOf<Rect, TouchPrivacy>()
    }
}

private const val COMPOSE_VIEW_CLASS_NAME = "androidx.compose.ui.platform.ComposeView"

private fun <T> newWorkStack(): ArrayDeque<T> {
    @Suppress("UnsafeThirdPartyFunctionCall") // no-arg constructor, cannot fail
    return ArrayDeque()
}

@Suppress("UnsafeThirdPartyFunctionCall") // an unbounded ArrayDeque's addLast never throws
private fun <T> ArrayDeque<T>.push(item: T) = addLast(item)

@Suppress("UnsafeThirdPartyFunctionCall") // only ever called guarded by a prior isNotStackEmpty() check
private fun <T> ArrayDeque<T>.pop(): T = removeLast()

@Suppress("UnsafeThirdPartyFunctionCall") // pure size check, cannot throw
private fun <T> ArrayDeque<T>.isNotStackEmpty(): Boolean = isNotEmpty()

/**
 * Detected by class name only, deliberately with no compile-time `androidx.compose` dependency from
 * this module. `ComposeView` is the public entry point apps add to a native layout; the internal
 * `AndroidComposeView` it creates as its single child is Compose-owned and never itself an
 * addressable child in the surrounding native hierarchy.
 */
private fun isComposeHost(view: View): Boolean = view.javaClass.name == COMPOSE_VIEW_CLASS_NAME

private fun CapturedWireframe.withClip(clip: CapturedClip?): CapturedWireframe = when (this) {
    is CapturedWireframe.Shape -> copy(clip = clip)
    is CapturedWireframe.Text -> copy(clip = clip)
    is CapturedWireframe.WebView -> copy(clip = clip)
    is CapturedWireframe.Pixel -> copy(clip = clip)
    is CapturedWireframe.PrivacyPlaceholder -> copy(clip = clip)
}

// Android applies clipToPadding only when at least one padding edge is non-zero.
private fun ViewGroup.hasNonZeroPadding(): Boolean =
    paddingLeft != 0 || paddingTop != 0 || paddingRight != 0 || paddingBottom != 0

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
