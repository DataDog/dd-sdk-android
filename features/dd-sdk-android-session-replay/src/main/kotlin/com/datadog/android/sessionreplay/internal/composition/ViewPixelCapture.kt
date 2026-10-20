/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.AbsSeekBar
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.NumberPicker
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.DefaultViewRasterizer
import com.datadog.android.sessionreplay.internal.composition.mapper.PixelCaptureEligibility
import com.datadog.android.sessionreplay.internal.composition.mapper.ViewRasterizer
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import java.util.IdentityHashMap

/** Shared privacy, allocation limits and resource registration for native pixel captures. */
@Suppress("ReturnCount") // Privacy and size guards must exit before allocating or reading pixels.
internal class ViewPixelCapture(
    private val internalLogger: InternalLogger,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val rasterizer: ViewRasterizer = DefaultViewRasterizer(internalLogger)
) {
    /** Null lets the ordinary fallback retain semantic output and continue traversing children. */
    @UiThread
    fun capture(view: View, context: CapturedMappingContext): CapturedViewMapperResult? {
        val bounds =
            context.bounds ?: viewBoundsResolver.resolveViewGlobalBounds(view, context.screenDensity).toCaptured()
        return captureSubtree(view, context, bounds, view.pixelSize()) { rasterizer.rasterize(view) }
    }

    /** Unrepresentable transforms are terminal: dropping the raster must not expose untransformed children. */
    @UiThread
    fun captureTransformed(
        view: View,
        context: CapturedMappingContext,
        transform: ViewTransform?,
        clipToBounds: Boolean
    ): CapturedViewMapperResult {
        val pixels = transform?.let { if (clipToBounds) it.pixelBounds else windowBounds(view) ?: it.pixelBounds }
        val bounds = pixels?.toTransformedReplayBounds(context.screenDensity)
            ?: context.bounds ?: CapturedBounds(0, 0, 0, 0)
        val result = if (transform != null && pixels != null) {
            captureSubtree(view, context, bounds, pixels) {
                rasterizer.rasterize(view, ViewTransform(transform.matrix, pixels), clipToBounds)
            }
        } else {
            null
        }
        return result ?: placeholder(context, bounds)
    }

    /** Foreground chrome has no native children of its own and follows background image privacy. */
    @UiThread
    fun captureDecoration(
        view: View,
        context: CapturedMappingContext,
        isTextFree: Boolean = false,
        rasterize: () -> Bitmap?
    ): CapturedWireframe? {
        if (PixelCaptureEligibility.placeholderLabelForBackground(context.imagePrivacy) != null) return null
        val bounds = context.bounds ?: return null
        return captureBitmap(view, context, bounds, view.pixelSize(), rasterize, isTextFree)
    }

    @UiThread
    private fun captureSubtree(
        view: View,
        context: CapturedMappingContext,
        bounds: CapturedBounds,
        pixels: GlobalBounds,
        rasterize: () -> Bitmap?
    ): CapturedViewMapperResult? {
        val imageBounds = GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)
        val label = PixelCaptureEligibility.placeholderLabelFor(context.imagePrivacy, imageBounds)
        if (label != null) return placeholder(context, bounds, label)
        if (containsRestrictedContent(view, context, bounds)) return null
        val wireframe = captureBitmap(view, context, bounds, pixels, rasterize) ?: return null
        return CapturedViewMapperResult.Wireframes(listOf(wireframe), isTerminal = true)
    }

    @UiThread
    private fun captureBitmap(
        view: View,
        context: CapturedMappingContext,
        bounds: CapturedBounds,
        pixels: GlobalBounds,
        rasterize: () -> Bitmap?,
        isTextFree: Boolean = false
    ): CapturedWireframe.Pixel? {
        if (!isCapturable(pixels, view)) return null
        val bitmap = rasterize() ?: return null
        val identity = context.identityFactory.imageWireframe(context.ownerIdentity)
        context.pendingPixelCaptureSink.register(
            PendingPixelCapture(identity, context.ownerIdentity, bitmap, isTextFree = isTextFree)
        )
        return CapturedWireframe.Pixel(identity, bounds, resource = PixelResource.Unresolved)
    }

    private fun placeholder(
        context: CapturedMappingContext,
        bounds: CapturedBounds,
        label: String = "Image"
    ): CapturedViewMapperResult = CapturedViewMapperResult.Wireframes(
        listOf(
            CapturedWireframe.PrivacyPlaceholder(
                context.identityFactory.placeholderWireframe(context.ownerIdentity),
                bounds,
                label = label
            )
        ),
        isTerminal = true
    )

    // Both whole-view paths must respect stronger descendant overrides and hidden/hardware content.
    @Suppress("UnsafeThirdPartyFunctionCall") // UI-owned work stack and valid child indices.
    @UiThread
    private fun containsRestrictedContent(
        view: View,
        context: CapturedMappingContext,
        bounds: CapturedBounds
    ): Boolean {
        val stack = ArrayDeque<Pair<View, EffectivePrivacy>>()
        stack.addLast(view to EffectivePrivacy(context.imagePrivacy, context.textAndInputPrivacy))
        val visited = IdentityHashMap<View, Boolean>()
        while (stack.isNotEmpty()) {
            if (!context.shouldContinue()) return true
            val (current, parentPrivacy) = stack.removeLast()
            if (visited.put(current, true) != null || current.visibility != View.VISIBLE) continue
            val privacy = resolveEffectivePrivacy(current, parentPrivacy, internalLogger)
            if (isRestricted(current, privacy, context, bounds.takeIf { current === view })) return true
            if (current is ViewGroup) {
                for (index in 0 until current.childCount) stack.addLast(current.getChildAt(index) to privacy)
            }
        }
        return false
    }

    @UiThread
    private fun isRestricted(
        view: View,
        privacy: EffectivePrivacy,
        context: CapturedMappingContext,
        copiedBounds: CapturedBounds?
    ): Boolean {
        val unsupported = view is SurfaceView || view is TextureView || view is WebView
        if (unsupported || view.getTag(R.id.datadog_hidden) == true) return true
        // Native raster inspection cannot inspect Compose semantics or redact non-text input state.
        if (isComposeHostByClassName(view)) return true
        val maskedInput = privacy.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        if (view is EditText || (maskedInput && isInputControl(view))) return true
        if (maskedInput && context.hasOptionSelectorParent) return true
        if (maskedInput && view is ViewGroup && context.isOptionSelector(view)) return true
        return isImageRestricted(view, privacy.imagePrivacy, context, copiedBounds, viewBoundsResolver)
    }

    @UiThread
    private fun isCapturable(bounds: GlobalBounds, view: View): Boolean {
        if (bounds.width !in 1..Int.MAX_VALUE || bounds.height !in 1..Int.MAX_VALUE) return false
        val metrics = view.resources.displayMetrics
        return !PixelCaptureEligibility.isTooLargeToCapture(
            bounds.width * bounds.height,
            metrics.widthPixels.toLong() * metrics.heightPixels
        )
    }

    @UiThread
    private fun windowBounds(view: View): GlobalBounds? {
        val root = view.rootView ?: return null
        val location = IntArray(2)
        @Suppress("UnsafeThirdPartyFunctionCall") // Owned two-element coordinate array.
        root.getLocationOnScreen(location)
        return GlobalBounds(location[0].toLong(), location[1].toLong(), root.width.toLong(), root.height.toLong())
    }
}

@UiThread
private fun View.pixelSize() = GlobalBounds(0, 0, width.toLong(), height.toLong())

private fun isInputControl(view: View): Boolean = when (view) {
    is CompoundButton, is CheckedTextView, is AbsSeekBar, is NumberPicker -> true
    else -> false
}

@UiThread
private fun isImageRestricted(
    view: View,
    privacy: ImagePrivacy,
    context: CapturedMappingContext,
    copiedBounds: CapturedBounds?,
    boundsResolver: ViewBoundsResolver
): Boolean = when (privacy) {
    ImagePrivacy.MASK_ALL -> true
    ImagePrivacy.MASK_NONE -> false
    ImagePrivacy.MASK_LARGE_ONLY -> {
        val bounds = copiedBounds
            ?: ViewTransformResolver.resolve(view)?.pixelBounds?.toTransformedReplayBounds(context.screenDensity)
            ?: boundsResolver.resolveViewGlobalBounds(view, context.screenDensity).toCaptured()
        PixelCaptureEligibility.placeholderLabelFor(
            privacy,
            GlobalBounds(bounds.x, bounds.y, bounds.width, bounds.height)
        ) != null
    }
}
