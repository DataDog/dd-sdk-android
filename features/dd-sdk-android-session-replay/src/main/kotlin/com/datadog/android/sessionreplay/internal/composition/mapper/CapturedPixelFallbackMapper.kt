/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.internal.composition.ViewPixelCapture
import com.datadog.android.sessionreplay.internal.composition.ViewTransform
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
 *
 * A [ViewGroup] with its own children and a real background (non-solid, since [fallbackMapper]
 * already failed to reduce it to one) is common decorative chrome - a themed `Toolbar`,
 * `CardView`, etc. Rasterizing the *whole* view for that case would bake its children's content
 * (a title `TextView`'s text, say) into one opaque bitmap, at which point
 * `PixelFallbackSnapshotProcessor`'s text-detection safety net has no choice but to blanket-mask
 * anything it finds - text that a dedicated `TextView`/`Button` mapper would otherwise have shown
 * unmasked, per [com.datadog.android.sessionreplay.TextAndInputPrivacy]. [map] rasterizes only the
 * background in that case (via [backgroundRasterizer], `isTextFree = true` - a background alone
 * structurally can't contain text) and reports `pixelFallbackTerminal = false`, so
 * [com.datadog.android.sessionreplay.internal.composition.AndroidWindowTraversal] still visits
 * those children on their own instead of treating this capture as standing in for the whole
 * subtree. A leaf (no children) or a childless/backgroundless `ViewGroup` has no such content to
 * protect, so it keeps the original whole-view rasterization.
 */
internal class CapturedPixelFallbackMapper(
    private val fallbackMapper: CapturedViewMapper<View>,
    private val internalLogger: InternalLogger,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    viewRasterizer: ViewRasterizer = DefaultViewRasterizer(internalLogger),
    private val pixelCapture: ViewPixelCapture = ViewPixelCapture(internalLogger, viewBoundsResolver, viewRasterizer),
    private val backgroundRasterizer: ViewBackgroundRasterizer = DefaultViewBackgroundRasterizer()
) : CapturedViewMapper<View> {

    @UiThread
    @Suppress("ReturnCount")
    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        // Keep plain containers semantic when their background is sufficient; replacing them
        // with a privacy placeholder would cover the independently captured child content.
        // ImageView and ImageButton also draw an image above that background, so they must
        // still reach pixel capture.
        val fallbackResult = fallbackMapper.map(view, mappingContext)
        if (view.willNotDraw() || (fallbackResult is CapturedViewMapperResult.Wireframes && !hasImageContent(view))) {
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

        if (isBackgroundOnlyEligible(view)) {
            return captureBackground(view, mappingContext) ?: fallbackResult
        }
        return pixelCapture.capture(view, mappingContext) ?: fallbackResult
    }

    @UiThread
    override fun capture(view: View, mappingContext: CapturedMappingContext): CapturedViewData {
        val fallback = fallbackMapper.capture(view, mappingContext)
        // Solid backgrounds retain their copied opacity/coverage flags and deferred style mapping.
        if (view.willNotDraw() || (fallback.isOcclusionBounded && !hasImageContent(view))) return fallback
        return CapturedViewData.Mapped(map(view, mappingContext))
    }

    @UiThread
    private fun hasImageContent(view: View): Boolean = view is ImageView && view.drawable != null

    private fun resolveBounds(view: View, mappingContext: CapturedMappingContext): GlobalBounds =
        mappingContext.bounds?.let { GlobalBounds(it.x, it.y, it.width, it.height) }
            ?: viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)

    private fun isBackgroundOnlyEligible(view: View): Boolean =
        view is ViewGroup && view.childCount > 0 && view.background != null

    @UiThread
    private fun captureBackground(view: View, context: CapturedMappingContext): CapturedViewMapperResult? {
        val bounds = resolveBounds(view, context)
        val label = PixelCaptureEligibility.placeholderLabelFor(context.imagePrivacy, bounds)
        if (label != null) {
            return CapturedViewMapperResult.Wireframes(
                listOf(
                    CapturedWireframe.PrivacyPlaceholder(
                        context.identityFactory.placeholderWireframe(context.ownerIdentity),
                        bounds.toCaptured(),
                        label = label
                    )
                ),
                isTerminal = true
            )
        }
        val wireframe = if (containsHardwareSurface(view)) {
            null
        } else {
            pixelCapture.captureDecoration(view, context.copy(bounds = bounds.toCaptured()), isTextFree = true) {
                backgroundRasterizer.rasterize(view)
            }
        }
        return wireframe?.let {
            @Suppress("UnsafeThirdPartyFunctionCall") // One capture-owned, non-null wireframe.
            val wireframes = listOf(it)
            CapturedViewMapperResult.Wireframes(wireframes, isTerminal = false)
        }
    }

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

/** Draws whole native content, with an optional copied transform for unsupported geometry. */
internal fun interface ViewRasterizer {
    @UiThread
    fun rasterize(view: View): Bitmap?

    @UiThread
    fun rasterize(view: View, transform: ViewTransform, clipToBounds: Boolean): Bitmap? = null
}

internal class DefaultViewRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ViewRasterizer {
    @UiThread
    override fun rasterize(view: View): Bitmap? = draw(view, null, false)

    @UiThread
    override fun rasterize(view: View, transform: ViewTransform, clipToBounds: Boolean): Bitmap? =
        draw(view, transform, clipToBounds)

    @UiThread
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // App drawing failures are isolated.
    private fun draw(view: View, transform: ViewTransform?, clipToBounds: Boolean): Bitmap? {
        var bitmap: Bitmap? = null
        return try {
            val width = transform?.pixelBounds?.width?.toInt() ?: view.width
            val height = transform?.pixelBounds?.height?.toInt() ?: view.height
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (transform != null) {
                canvas.translate(-transform.pixelBounds.x.toFloat(), -transform.pixelBounds.y.toFloat())
                canvas.concat(Matrix().apply { setValues(transform.matrix) })
                if (clipToBounds) canvas.clipRect(0, 0, view.width, view.height)
            }
            // Match the scroll translation normally applied by the parent before View.draw.
            canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            view.clipBounds?.let(canvas::clipRect)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            bitmap?.recycle()
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to rasterize view for pixel-fallback capture" },
                e
            )
            null
        }
    }
}
