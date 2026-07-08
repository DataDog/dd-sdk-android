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
 * based on privacy, gated via [PixelCaptureEligibility] (shared with the Compose-side
 * dark-spot detection in `RootSemanticsNodeMapper`). Any privacy restriction beyond what
 * [PixelCaptureEligibility] allows — global, or from a per-view privacy tag override, both of
 * which are already resolved into [MappingContext] before this mapper runs — disables pixel
 * capture entirely for that view rather than risk uploading unmasked sensitive content.
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
        if (pixelCropCallback == null) {
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

        if (!PixelCaptureEligibility.isEligible(
                textAndInputPrivacy = mappingContext.textAndInputPrivacy,
                imagePrivacy = mappingContext.imagePrivacy,
                boundsDp = globalBounds
            )
        ) {
            return fallbackMapper.map(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val nodeId = resolveViewId(view)

        // Window-pixel rect for the PixelCopy crop path.
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val windowRect = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
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

        // Mutable so the pixel-capture pipeline can swap this entry for a placeholder if the
        // per-cycle capture budget runs out before this crop is processed.
        val wireframes = mutableListOf<MobileSegment.Wireframe>(imageWireframe)

        pixelCropCallback.registerPendingCrop(
            nodeId = nodeId,
            windowRect = windowRect,
            dpBounds = globalBounds,
            isolationView = view,
            isolationClipRect = isolationClipRect,
            wireframe = imageWireframe,
            wireframeSlot = WireframeSlot { wireframes[0] = it },
            asyncJobStatusCallback = asyncJobStatusCallback
        )

        return wireframes
    }
}
