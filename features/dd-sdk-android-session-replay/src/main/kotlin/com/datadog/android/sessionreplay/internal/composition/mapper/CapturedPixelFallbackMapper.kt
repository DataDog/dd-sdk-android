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
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
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

    @Suppress("ReturnCount")
    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        // fallbackMapper already fully and harmlessly describes two distinct cases: a genuine
        // pass-through with nothing of its own to draw (None - willNotDraw() is the platform's own
        // answer to "does this instance paint anything besides its children"), or a plain
        // solid-color background (Wireframes - never privacy-sensitive, exactly what the legacy,
        // non-composition-tree pipeline has always shown for such views). Neither case should ever
        // reach the privacy-gated pixel-capture logic below: doing so turned every ordinary
        // ViewGroup with a themed background into a full-bounds opaque placeholder, burying the
        // real content its children would otherwise render. Only a view fallbackMapper genuinely
        // can't describe (a complex/non-solid background, or overridden custom onDraw content) is
        // an actual candidate for pixel capture.
        val fallbackResult = fallbackMapper.map(view, mappingContext)
        if (view.willNotDraw() || fallbackResult is CapturedViewMapperResult.Wireframes) {
            return fallbackResult
        }

        // Scrolling containers (ScrollView, RecyclerView, ...) always report willNotDraw() ==
        // false, regardless of background: the platform reserves their onDraw()/draw() override
        // for the overscroll edge-glow, which paints nothing outside an active fling-past-the-end
        // gesture. Absent an actual background of their own, there is no persistent visual content
        // here to protect or capture - only the momentary glow, which isn't worth a pixel capture
        // and isn't privacy-sensitive. A view with a real background (even non-solid) still falls
        // through to the normal capture-or-placeholder logic below, since that background is
        // genuine, persistent content.
        if (view.background == null && isEdgeEffectOnlyContainer(view)) {
            return fallbackResult
        }

        if (view.width <= 0 || view.height <= 0) {
            return fallbackResult
        }

        val visibleRect = Rect()
        @Suppress("UnsafeThirdPartyFunctionCall") // visibleRect is a freshly-allocated non-null Rect, never null
        if (!view.getGlobalVisibleRect(visibleRect) || visibleRect.isEmpty) {
            return fallbackResult
        }

        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val placeholderLabel = PixelCaptureEligibility.placeholderLabelFor(mappingContext.imagePrivacy, bounds)
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
            return fallbackResult
        }

        val bitmap = viewRasterizer.rasterize(view) ?: return fallbackResult

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

    /** Pre-emptive OOM defense: an unbounded custom View could otherwise demand a huge bitmap. */
    private fun isTooLargeToCapture(view: View): Boolean {
        val displayMetrics = view.resources.displayMetrics
        val screenArea = displayMetrics.widthPixels.toLong() * displayMetrics.heightPixels.toLong()
        val viewArea = view.width.toLong() * view.height.toLong()
        return PixelCaptureEligibility.isTooLargeToCapture(viewArea, screenArea)
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

    /**
     * `NestedScrollView`/`RecyclerView` are matched by class name rather than type, the same way
     * [com.datadog.android.sessionreplay.internal.composition.AndroidWindowTraversal] recognizes a
     * Compose host - this module doesn't depend on `androidx.core`/`androidx.recyclerview`, and a
     * hard dependency isn't worth adding just to widen this check.
     */
    private fun isEdgeEffectOnlyContainer(view: View): Boolean = when (view) {
        is ScrollView, is HorizontalScrollView -> true
        else ->
            view.javaClass.name == "androidx.core.widget.NestedScrollView" ||
                view.javaClass.name == "androidx.recyclerview.widget.RecyclerView"
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
        @Suppress("UnsafeThirdPartyFunctionCall") // view.draw runs arbitrary custom draw code; caught below
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
