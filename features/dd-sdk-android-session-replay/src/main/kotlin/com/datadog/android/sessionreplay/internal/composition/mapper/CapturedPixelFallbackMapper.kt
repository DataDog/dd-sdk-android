/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Rasterizing fallback for any [View]/[ViewGroup] [fallbackMapper] alone can't faithfully
 * represent: redraws the view into a bitmap via [View.draw] instead of describing it structurally.
 * Wraps [fallbackMapper] (the plain background-color-only fallback) as the escape hatch for every
 * case this mapper declines to handle itself - image privacy never disqualifies a region from
 * [fallbackMapper]'s own background-color capture, only from pixel capture.
 *
 * Image privacy is checked before any drawing happens, using the exact semantics
 * [DefaultImageWireframeHelper] already applies to ordinary images: `MASK_ALL` always
 * placeholders, `MASK_LARGE_ONLY` placeholders only regions at or above
 * [IMAGE_DIMEN_CONSIDERED_PII_IN_DP] in either dimension. Text-region masking of whatever *is*
 * captured is a separate, later concern (`PixelFallbackSnapshotProcessor`) - it applies regardless
 * of the image privacy level that let capture proceed at all.
 */
internal class CapturedPixelFallbackMapper(
    private val fallbackMapper: CapturedViewMapper<View>,
    private val internalLogger: InternalLogger,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val viewRasterizer: ViewRasterizer = DefaultViewRasterizer(internalLogger)
) : CapturedViewMapper<View> {

    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        if (view.width <= 0 || view.height <= 0) {
            return fallbackMapper.map(view, mappingContext)
        }

        val visibleRect = Rect()
        if (!view.getGlobalVisibleRect(visibleRect) || visibleRect.isEmpty) {
            return fallbackMapper.map(view, mappingContext)
        }

        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val placeholderLabel = imagePrivacyPlaceholderLabel(mappingContext.imagePrivacy, bounds)
        if (placeholderLabel != null) {
            val identity = mappingContext.identityFactory.placeholderWireframe(mappingContext.ownerIdentity)
            return CapturedViewMapperResult.Wireframes(
                listOf(
                    CapturedWireframe.PrivacyPlaceholder(
                        identity = identity,
                        bounds = bounds.toCaptured(),
                        label = placeholderLabel
                    )
                )
            )
        }

        if (isTooLargeToCapture(view) || containsHardwareSurface(view)) {
            return fallbackMapper.map(view, mappingContext)
        }

        val bitmap = viewRasterizer.rasterize(view) ?: return fallbackMapper.map(view, mappingContext)

        val identity = mappingContext.identityFactory.imageWireframe(mappingContext.ownerIdentity)
        mappingContext.pendingPixelCaptureSink.register(
            PendingPixelCapture(
                wireframeIdentity = identity,
                ownerIdentity = mappingContext.ownerIdentity,
                bitmap = bitmap
            )
        )
        return CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.Pixel(
                    identity = identity,
                    bounds = bounds.toCaptured(),
                    resource = PixelResource.Unresolved
                )
            )
        )
    }

    private fun imagePrivacyPlaceholderLabel(imagePrivacy: ImagePrivacy, boundsDp: GlobalBounds): String? =
        when (imagePrivacy) {
            ImagePrivacy.MASK_ALL -> DefaultImageWireframeHelper.MASK_ALL_CONTENT_LABEL
            ImagePrivacy.MASK_LARGE_ONLY -> if (isLarge(boundsDp)) {
                DefaultImageWireframeHelper.MASK_CONTEXTUAL_CONTENT_LABEL
            } else {
                null
            }
            ImagePrivacy.MASK_NONE -> null
        }

    private fun isLarge(boundsDp: GlobalBounds): Boolean =
        boundsDp.width >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP || boundsDp.height >= IMAGE_DIMEN_CONSIDERED_PII_IN_DP

    /** Pre-emptive OOM defense: an unbounded custom View could otherwise demand a huge bitmap. */
    private fun isTooLargeToCapture(view: View): Boolean {
        val displayMetrics = view.resources.displayMetrics
        val screenArea = displayMetrics.widthPixels.toLong() * displayMetrics.heightPixels.toLong()
        if (screenArea <= 0) return false
        val viewArea = view.width.toLong() * view.height.toLong()
        return viewArea > MAX_CAPTURABLE_AREA_IN_SCREENS * screenArea
    }

    /**
     * [SurfaceView]/[TextureView] content composites outside the [View.draw] path (SurfaceFlinger/
     * `SurfaceTexture`) - drawing over it would silently produce blank content, not an exception, so
     * this must be checked before attempting a draw at all. Intersection-aware by construction:
     * only the offending subtree's own mapper call is skipped, siblings are unaffected.
     */
    private fun containsHardwareSurface(view: View): Boolean = when (view) {
        is SurfaceView, is TextureView -> true
        is ViewGroup -> (0 until view.childCount).any { containsHardwareSurface(view.getChildAt(it)) }
        else -> false
    }

    private companion object {
        const val MAX_CAPTURABLE_AREA_IN_SCREENS = 8L
    }
}

/** Isolates the actual `View.draw()`-into-bitmap call so it can be substituted in tests. */
internal fun interface ViewRasterizer {
    fun rasterize(view: View): Bitmap?
}

internal class DefaultViewRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ViewRasterizer {

    @Suppress("TooGenericExceptionCaught")
    override fun rasterize(view: View): Bitmap? = try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.TELEMETRY,
            { "Failed to rasterize view for pixel-fallback capture" },
            e
        )
        null
    }
}
