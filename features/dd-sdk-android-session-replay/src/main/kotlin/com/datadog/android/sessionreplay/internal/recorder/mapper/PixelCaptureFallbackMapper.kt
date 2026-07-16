/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Rect
import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.PixelCaptureEligibility
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import kotlin.math.max

/**
 * Mapper used as a fallback for views that have no registered mapper.
 *
 * Registers a [com.datadog.android.sessionreplay.internal.recorder.PendingPixelCapture] for
 * post-traversal processing by
 * [com.datadog.android.sessionreplay.internal.recorder.PixelCapture.processPendingCaptures],
 * which captures the view via [View.draw], isolated from anything drawn on top of it (see that
 * class's doc for the content-caching strategy used to avoid drawing on every cycle).
 *
 * Always captures [view]'s **full** bounds — never just the currently visible portion — so the
 * captured bitmap's size stays constant across scroll positions and
 * [com.datadog.android.sessionreplay.internal.recorder.PixelCapture]'s content cache (keyed by
 * captured size) actually applies: a view whose bounds exceed its scrolling ancestor's viewport
 * (e.g. a Compose host taller than its `NestedScrollView` — the normal case for any non-trivial
 * scrollable screen) would otherwise be captured at a different clipped size on nearly every
 * scroll frame, defeating the cache and forcing a fresh draw/encode/upload cycle per scroll tick.
 * Only *whether* to attempt a capture at all depends on visibility — [View.getGlobalVisibleRect]
 * must report *some* non-empty overlap; a view scrolled entirely off-screen still falls back to
 * [fallbackMapper], but a merely *partially* visible one (again, the permanent state of most
 * scrollable Compose content, not an edge case) no longer does.
 *
 * The part of [view] currently clipped by ancestors — the difference between its full bounds and
 * [View.getGlobalVisibleRect] — is reported via [MobileSegment.Wireframe.ImageWireframe.clip],
 * the same mechanism [com.datadog.android.sessionreplay.internal.processor.WireframeUtils.resolveWireframeClip]
 * already uses for ordinary (non-pixel-capture) scrollable content: the wireframe's `x/y/width/height`
 * describe the view's full logical bounds, and `clip` tells the player how much of that to crop
 * visually — so the (expensive, cached, potentially-uploaded) bitmap itself never needs to change
 * just because the user scrolled, only this cheap per-cycle metadata does.
 *
 * Falls back to [fallbackMapper] when nothing is visible at all, and — critically — based on
 * privacy, gated via [PixelCaptureEligibility]. Any privacy restriction beyond what
 * [PixelCaptureEligibility] allows — global, or from a per-view privacy tag override, both of
 * which are already resolved into [MappingContext] before this mapper runs — disables pixel
 * capture entirely for that view rather than risk uploading unmasked sensitive content. Eligibility
 * is judged against the view's *full* bounds, matching what's actually captured and uploaded.
 */
internal class PixelCaptureFallbackMapper(
    private val fallbackMapper: WireframeMapper<View>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<View>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    @UiThread
    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val pixelCaptureCallback = mappingContext.pixelCaptureCallback
        if (pixelCaptureCallback == null) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        if (view.width <= 0 || view.height <= 0) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        // Full-bounds capture (see the class doc) means a view many screens taller than its
        // viewport — a long non-virtualized Compose Column, not just a normal product page —
        // would otherwise be captured in one shot. A 1080x50000 ARGB_8888 bitmap is ~200MB; this
        // pipeline runs in arbitrary third-party apps, so that has to be bounded defensively
        // rather than assumed away. Falls back rather than silently truncating: a partial capture
        // of arbitrarily-chosen content would be as misleading as the bug this class fixes.
        if (isTooLargeToCapture(view, mappingContext)) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val visibleRect = Rect()
        if (!view.getGlobalVisibleRect(visibleRect) || visibleRect.isEmpty) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val density = mappingContext.systemInformation.screenDensity
        val globalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)

        if (!PixelCaptureEligibility.isEligible(
                textAndInputPrivacy = mappingContext.textAndInputPrivacy,
                imagePrivacy = mappingContext.imagePrivacy,
                boundsDp = globalBounds
            )
        ) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val nodeId = resolveViewId(view)

        // Isolation clip rect: the view's own full bounds, drawn into its own canvas — always
        // this size regardless of how much is currently scrolled into view (see the class doc).
        val isolationClipRect = Rect(0, 0, view.width, view.height)

        val wireframeClip = resolveWireframeClip(view, visibleRect, density)

        val imageWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = nodeId,
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            clip = wireframeClip,
            isEmpty = true
        )

        // Mutable so the pixel-capture pipeline can swap this entry for a placeholder if the
        // per-cycle capture budget runs out before this capture is processed.
        val wireframes = mutableListOf<MobileSegment.Wireframe>(imageWireframe)

        pixelCaptureCallback.registerPendingCapture(
            nodeId = nodeId,
            dpBounds = globalBounds,
            isolationView = view,
            isolationClipRect = isolationClipRect,
            wireframe = imageWireframe,
            wireframeSlot = WireframeSlot { wireframes[0] = it },
            asyncJobStatusCallback = asyncJobStatusCallback,
            textAndInputPrivacy = mappingContext.textAndInputPrivacy,
            imagePrivacy = mappingContext.imagePrivacy
        )

        return wireframes
    }

    /**
     * How much of [view]'s full bounds is currently clipped by an ancestor (e.g. a scrolling
     * container), in dp — the difference between its full screen location/size and
     * [visibleRect] ([View.getGlobalVisibleRect], already in screen pixels). Mirrors
     * [com.datadog.android.sessionreplay.internal.processor.WireframeUtils.resolveWireframeClip]'s
     * max-overflow-per-edge approach, collapsed to the single view/visible-rect relationship
     * already computed here instead of iterating a parents list.
     */
    private fun resolveWireframeClip(
        view: View,
        visibleRect: Rect,
        screenDensity: Float
    ): MobileSegment.WireframeClip? {
        val locationOnScreen = IntArray(2)
        view.getLocationOnScreen(locationOnScreen)
        val fullRect = Rect(
            locationOnScreen[0],
            locationOnScreen[1],
            locationOnScreen[0] + view.width,
            locationOnScreen[1] + view.height
        )

        val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
        val clipTop = max(0, visibleRect.top - fullRect.top)
        val clipBottom = max(0, fullRect.bottom - visibleRect.bottom)
        val clipLeft = max(0, visibleRect.left - fullRect.left)
        val clipRight = max(0, fullRect.right - visibleRect.right)

        if (clipTop == 0 && clipBottom == 0 && clipLeft == 0 && clipRight == 0) return null

        return MobileSegment.WireframeClip(
            top = (clipTop * inverseDensity).toLong(),
            bottom = (clipBottom * inverseDensity).toLong(),
            left = (clipLeft * inverseDensity).toLong(),
            right = (clipRight * inverseDensity).toLong()
        )
    }

    /**
     * True if [view]'s full pixel area exceeds [MAX_CAPTURABLE_AREA_IN_SCREENS] times the
     * current screen's own pixel area — scaled to the device's actual screen rather than a fixed
     * pixel count, so the same relative allowance applies whether this runs on a small phone or
     * a large tablet/foldable.
     */
    private fun isTooLargeToCapture(view: View, mappingContext: MappingContext): Boolean {
        val density = mappingContext.systemInformation.screenDensity
        val screenBounds = mappingContext.systemInformation.screenBounds
        val screenAreaPx = (screenBounds.width * density) * (screenBounds.height * density)
        val viewAreaPx = view.width.toLong() * view.height.toLong()
        return viewAreaPx > screenAreaPx * MAX_CAPTURABLE_AREA_IN_SCREENS
    }

    private companion object {
        /**
         * How many multiples of the device's own screen area [view] is allowed to cover before
         * pixel capture gives up on it — generous enough for a legitimately long scrollable page
         * (this pipeline captures a view's *full* bounds, not just what's visible, so a page a
         * few screens tall is normal and expected — see the class doc), bounded enough to reject
         * pathological content (e.g. a long chat log or article laid out in one non-virtualized
         * Compose `Column` instead of a `LazyColumn`) that would otherwise risk an OOM-sized
         * bitmap allocation or blow through this cycle's capture budget.
         */
        private const val MAX_CAPTURABLE_AREA_IN_SCREENS = 8f
    }
}
