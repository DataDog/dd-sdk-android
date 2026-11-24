/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.composition.ViewPixelCapture
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
 * [IMAGE_DIMEN_CONSIDERED_PII_IN_DP] in either dimension. Whatever *is* captured may contain
 * text, so `PixelFallbackSnapshotProcessor` resolves it separately and fails closed to a
 * placeholder regardless of the image privacy level that let capture proceed at all.
 */
internal class CapturedPixelFallbackMapper(
    private val fallbackMapper: CapturedViewMapper<View>,
    private val internalLogger: InternalLogger,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    viewRasterizer: ViewRasterizer = DefaultViewRasterizer(internalLogger),
    private val pixelCapture: ViewPixelCapture = ViewPixelCapture(internalLogger, viewBoundsResolver, viewRasterizer)
) : CapturedViewMapper<View> {

    @UiThread
    @Suppress("ReturnCount")
    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        // Keep plain containers semantic when their background is sufficient; replacing them
        // with a privacy placeholder would cover the independently captured child content.
        // ImageView and ImageButton also draw an image above that background, so they must
        // still reach pixel capture.
        val fallbackResult = fallbackMapper.map(view, mappingContext)
        if (isDescribedWithoutPixels(view, mappingContext, fallbackResult is CapturedViewMapperResult.Wireframes)) {
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

        return pixelCapture.capture(view, mappingContext) ?: fallbackResult
    }

    @UiThread
    override fun capture(view: View, mappingContext: CapturedMappingContext): CapturedViewData {
        val fallback = fallbackMapper.capture(view, mappingContext)
        // Solid backgrounds retain their copied opacity/coverage flags and deferred style mapping.
        if (isDescribedWithoutPixels(view, mappingContext, fallback.isOcclusionBounded)) return fallback
        return CapturedViewData.Mapped(map(view, mappingContext))
    }

    /**
     * Whether [view] needs no pixels: it draws nothing, its fallback output already describes it
     * ([hasFallbackOutput]) unless an image draws above that, or it is a composition host, whose
     * content is described by its decomposition and which a raster would duplicate while bypassing
     * the privacy modifiers inside it.
     */
    @UiThread
    private fun isDescribedWithoutPixels(
        view: View,
        mappingContext: CapturedMappingContext,
        hasFallbackOutput: Boolean
    ): Boolean = mappingContext.isCompositionHost ||
        view.willNotDraw() ||
        (hasFallbackOutput && !hasImageContent(view))

    @UiThread
    private fun hasImageContent(view: View): Boolean = view is ImageView && view.drawable != null

    /**
     * `NestedScrollView`/`RecyclerView` are matched by class name rather than type, the same way
     * [com.datadog.android.sessionreplay.internal.composition.AndroidWindowTraversal] recognizes a
     * Compose host - this module doesn't depend on `androidx.core`/`androidx.recyclerview`, and a
     * hard dependency isn't worth adding just to widen this check. Apps and libraries subclass both
     * routinely, so a name is matched anywhere up the superclass chain, as a type check would.
     */
    private fun isEdgeEffectOnlyContainer(view: View): Boolean = when (view) {
        is ScrollView, is HorizontalScrollView, is AbsListView -> true
        else -> view.hasSuperclassNamed(EDGE_EFFECT_ONLY_CLASS_NAMES)
    }

    private fun View.hasSuperclassNamed(names: Set<String>): Boolean {
        var type: Class<*>? = javaClass
        while (type != null) {
            if (type.name in names) return true
            type = type.superclass
        }
        return false
    }

    private companion object {
        val EDGE_EFFECT_ONLY_CLASS_NAMES = setOf(
            "androidx.core.widget.NestedScrollView",
            "androidx.recyclerview.widget.RecyclerView"
        )
    }
}

/** Draws whole native content. */
internal fun interface ViewRasterizer {
    @UiThread
    fun rasterize(view: View): Bitmap?
}

internal class DefaultViewRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ViewRasterizer {
    @UiThread
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // App drawing failures are isolated.
    override fun rasterize(view: View): Bitmap? {
        var bitmap: Bitmap? = null
        return try {
            // A view over the raster budget is drawn smaller rather than allocated at full resolution.
            val (width, height) = PixelCaptureEligibility.rasterSize(view.width, view.height)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(width / view.width.toFloat(), height / view.height.toFloat())
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
                e,
                onlyOnce = true
            )
            null
        }
    }
}
