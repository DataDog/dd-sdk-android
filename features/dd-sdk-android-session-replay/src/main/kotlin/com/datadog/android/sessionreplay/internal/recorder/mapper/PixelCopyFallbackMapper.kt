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
import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * Mapper used as a fallback for views that have no registered mapper.
 *
 * Registers a [com.datadog.android.sessionreplay.internal.recorder.PendingPixelCrop] for
 * post-traversal processing by [com.datadog.android.sessionreplay.internal.recorder.PixelCopyCapture.processPendingCrops].
 * That method decides, once all wireframe bounds are known, whether to:
 *
 * - **Crop from the stored PixelCopy bitmap** (full fidelity — parent alpha and transforms
 *   included) when no overlay sits above this view.
 * - **Call [View.draw] in isolation** (no overlay contamination) when another mapped
 *   element physically overlaps this view's region.
 *
 * Falls back to [fallbackMapper] for views that are not fully on-screen, and — critically —
 * based on privacy:
 * - [MappingContext.textAndInputPrivacy] must be [TextAndInputPrivacy.MASK_SENSITIVE_INPUTS]
 *   (its baseline — [TextAndInputPrivacy] has no fully-permissive "off" state); anything
 *   stricter disables pixel capture unconditionally.
 * - [MappingContext.imagePrivacy] gates on the view's size, mirroring how
 *   [ImagePrivacy.MASK_LARGE_ONLY] already behaves for regular images elsewhere in SR:
 *   [ImagePrivacy.MASK_NONE] always allows capture; [ImagePrivacy.MASK_LARGE_ONLY] allows it
 *   only when the view is smaller than [IMAGE_DIMEN_CONSIDERED_PII_IN_DP] on both axes (small
 *   views are unlikely to be meaningful content); [ImagePrivacy.MASK_ALL] never allows it.
 *
 * This pipeline captures raw pixels with no knowledge of what a given unmapped view actually
 * renders — unlike the semantic mapper chain, it can't selectively mask only sensitive text
 * or contextual images. Any privacy restriction beyond what's allowed above — global, or from
 * a per-view privacy tag override, both of which are already resolved into [MappingContext]
 * before this mapper runs — disables pixel capture entirely for that view rather than risk
 * uploading unmasked sensitive content.
 */
internal class PixelCopyFallbackMapper(
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
        val pixelCropCallback = mappingContext.pixelCropCallback
        if (pixelCropCallback == null ||
            mappingContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS ||
            mappingContext.imagePrivacy == ImagePrivacy.MASK_ALL
        ) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val visibleRect = Rect()
        if (!view.getGlobalVisibleRect(visibleRect) ||
            visibleRect.width() != view.width ||
            visibleRect.height() != view.height
        ) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        if (view.width <= 0 || view.height <= 0) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val density = mappingContext.systemInformation.screenDensity
        val globalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)

        // MASK_LARGE_ONLY: only allow capture for views small enough to be unlikely to hold
        // meaningful content (icons, decorations) — same threshold used for regular images.
        val isLarge = globalBounds.width >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP ||
            globalBounds.height >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP
        if (mappingContext.imagePrivacy == ImagePrivacy.MASK_LARGE_ONLY && isLarge) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val nodeId = resolveViewId(view)

        // Window-pixel rect for the PixelCopy crop path.
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val windowRect = Rect(
            location[0], location[1],
            location[0] + view.width, location[1] + view.height
        )

        // Isolation clip rect: view draws into its own canvas, full size.
        val isolationClipRect = Rect(0, 0, view.width, view.height)

        val imageWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = nodeId,
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            isEmpty = true
        )

        pixelCropCallback.registerPendingCrop(
            nodeId = nodeId,
            windowRect = windowRect,
            dpBounds = globalBounds,
            isolationView = view,
            isolationClipRect = isolationClipRect,
            wireframe = imageWireframe,
            asyncJobStatusCallback = asyncJobStatusCallback
        )

        return listOf(imageWireframe)
    }
}
