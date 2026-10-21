/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.text.TextDirectionHeuristicsCompat
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedPadding
import com.datadog.android.internal.sessionreplay.composition.CapturedTextPosition
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTruncationMode
import com.datadog.android.internal.sessionreplay.composition.CapturedVerticalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Copies masked text and native styling on the UI thread for wireframe construction on the worker.
 * Non-solid backgrounds and compound drawables contribute privacy-gated raster captures during
 * this same copy. Relative alignment follows the rendered paragraph direction for text alignment
 * and the layout direction for view alignment.
 */
internal open class CapturedTextViewMapper<T : TextView>(
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    private val backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver =
        CapturedBackgroundShapeStyleResolver(),
    private val backgroundRasterizer: ViewBackgroundRasterizer = DefaultViewBackgroundRasterizer(),
    private val compoundDrawableRasterizer: CompoundDrawableRasterizer = DefaultCompoundDrawableRasterizer(),
    private val internalLogger: InternalLogger
) : CapturedViewMapper<T> {

    override fun map(view: T, mappingContext: CapturedMappingContext): CapturedViewMapperResult =
        capture(view, mappingContext).map(mappingContext)

    override fun capture(view: T, mappingContext: CapturedMappingContext): CapturedViewData {
        val layout = view.layout
        val bounds = mappingContext.bounds
            ?: viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity).toCaptured()
        val background = backgroundShapeStyleResolver.capture(view, internalLogger)
        return CapturedTextViewData(
            bounds = bounds,
            text = if (mappingContext.hasOptionSelectorParent &&
                mappingContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
            ) {
                "***"
            } else {
                resolveCapturedText(view, mappingContext.textAndInputPrivacy)
            },
            background = background,
            backgroundWireframes = if (background == null) {
                resolveBackgroundWireframe(view, mappingContext, bounds)
            } else {
                emptyList()
            },
            compoundDrawableWireframes = resolveCompoundDrawableWireframes(view, mappingContext, bounds),
            family = when (view.typeface) {
                Typeface.MONOSPACE -> "monospace"
                Typeface.SERIF -> "serif"
                else -> "roboto, sans-serif"
            },
            textSizePx = view.textSize.toLong(),
            textColor = if (view.text.isNullOrEmpty()) {
                if (view.hintTextColors != null) view.currentHintTextColor else view.currentTextColor
            } else {
                view.currentTextColor
            },
            truncationMode = when (view.ellipsize) {
                TextUtils.TruncateAt.START -> CapturedTruncationMode.HEAD
                TextUtils.TruncateAt.END -> CapturedTruncationMode.TAIL
                TextUtils.TruncateAt.MIDDLE -> CapturedTruncationMode.MIDDLE
                TextUtils.TruncateAt.MARQUEE -> CapturedTruncationMode.CLIP
                null -> null
            },
            paddingTopPx = if (layout != null) view.totalPaddingTop else view.paddingTop,
            paddingBottomPx = if (layout != null) view.totalPaddingBottom else view.paddingBottom,
            paddingLeftPx = if (layout != null) view.totalPaddingLeft else view.paddingLeft,
            paddingRightPx = if (layout != null) view.totalPaddingRight else view.paddingRight,
            textAlignment = view.textAlignment,
            gravity = view.gravity,
            isParagraphRtl = isParagraphRtl(view),
            isLayoutRtl = view.layoutDirection == TextView.LAYOUT_DIRECTION_RTL,
            hasLayout = layout != null,
            colorStringFormatter = colorStringFormatter
        )
    }

    @Suppress("ReturnCount")
    private fun resolveBackgroundWireframe(
        view: T,
        mappingContext: CapturedMappingContext,
        capturedBounds: CapturedBounds
    ): List<CapturedWireframe> {
        if (view.background == null || view.width <= 0 || view.height <= 0) return emptyList()

        val placeholderLabel = PixelCaptureEligibility.placeholderLabelForBackground(mappingContext.imagePrivacy)
        if (placeholderLabel != null) return emptyList()

        val displayMetrics = view.resources.displayMetrics
        val screenArea = displayMetrics.widthPixels.toLong() * displayMetrics.heightPixels.toLong()
        val viewArea = view.width.toLong() * view.height.toLong()
        if (PixelCaptureEligibility.isTooLargeToCapture(viewArea, screenArea)) return emptyList()

        val bitmap = backgroundRasterizer.rasterize(view) ?: return emptyList()
        val identity = mappingContext.identityFactory.imageWireframe(mappingContext.ownerIdentity)
        mappingContext.pendingPixelCaptureSink.register(
            PendingPixelCapture(
                wireframeIdentity = identity,
                ownerIdentity = mappingContext.ownerIdentity,
                bitmap = bitmap,
                isTextFree = true
            )
        )
        return listOf(
            CapturedWireframe.Pixel(
                identity = identity,
                bounds = capturedBounds,
                resource = PixelResource.Unresolved
            )
        )
    }

    /**
     * Ported from legacy `ViewUtilsInternal.resolveCompoundDrawableBounds`'s positioning math:
     * `compoundDrawables` is always ordered [left, top, right, bottom], each centered on the
     * view's cross-axis and pinned to its own edge (inset by that edge's padding) on the main axis.
     * Uses [PixelCaptureEligibility.placeholderLabelFor] - the "ordinary image" gate, not
     * [PixelCaptureEligibility.placeholderLabelForBackground] - since a compound drawable is real
     * icon/image content, not decorative chrome, matching legacy's `usePIIPlaceholder = true` for
     * this same call site.
     */
    private fun resolveCompoundDrawableWireframes(
        view: T,
        mappingContext: CapturedMappingContext,
        viewBounds: CapturedBounds
    ): List<CapturedWireframe> {
        val drawables = view.compoundDrawables
        if (drawables.all { it == null }) return emptyList()

        val density = mappingContext.screenDensity / mappingContext.viewScale
        val paddingStart = view.paddingStart.densityNormalized(density).toLong()
        val paddingTop = view.paddingTop.densityNormalized(density).toLong()
        val paddingEnd = view.paddingEnd.densityNormalized(density).toLong()
        val paddingBottom = view.paddingBottom.densityNormalized(density).toLong()
        val displayMetrics = view.resources.displayMetrics
        val screenAreaPx = displayMetrics.widthPixels.toLong() * displayMetrics.heightPixels.toLong()

        return drawables.mapIndexedNotNull { index, drawable ->
            if (drawable == null || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                return@mapIndexedNotNull null
            }

            val widthDp = drawable.intrinsicWidth.densityNormalized(density).toLong()
            val heightDp = drawable.intrinsicHeight.densityNormalized(density).toLong()
            val offset = when (index) {
                LEFT_DRAWABLE_INDEX -> paddingStart to centerOffset(viewBounds.height, heightDp)
                TOP_DRAWABLE_INDEX -> centerOffset(viewBounds.width, widthDp) to paddingTop
                RIGHT_DRAWABLE_INDEX ->
                    (viewBounds.width - (widthDp + paddingEnd)) to centerOffset(viewBounds.height, heightDp)
                BOTTOM_DRAWABLE_INDEX ->
                    centerOffset(viewBounds.width, widthDp) to (viewBounds.height - (heightDp + paddingBottom))
                else -> return@mapIndexedNotNull null
            }

            val iconBounds = CapturedBounds(
                x = viewBounds.x + offset.first,
                y = viewBounds.y + offset.second,
                width = widthDp,
                height = heightDp
            )

            // A dedicated per-slot owner, not mappingContext.ownerIdentity directly: imageWireframe/
            // placeholderWireframe mint exactly one identity per owner (every other call site in this
            // class calls each at most once), so reusing the view's own owner identity here would
            // collide with resolveBackgroundWireframe's imageWireframe(ownerIdentity) call - both
            // wireframes would land on the same wire id and one would silently clobber the other.
            val slotOwner = mappingContext.identityFactory.layer(
                mappingContext.ownerIdentity,
                "compound-drawable-$index"
            )

            resolveCompoundDrawableWireframe(mappingContext, slotOwner, drawable, iconBounds, screenAreaPx)
        }
    }

    @Suppress("ReturnCount")
    private fun resolveCompoundDrawableWireframe(
        mappingContext: CapturedMappingContext,
        slotOwner: CapturedIdentity,
        drawable: Drawable,
        iconBounds: CapturedBounds,
        screenAreaPx: Long
    ): CapturedWireframe? {
        val boundsDp = GlobalBounds(iconBounds.x, iconBounds.y, iconBounds.width, iconBounds.height)
        val placeholderLabel = PixelCaptureEligibility.placeholderLabelFor(mappingContext.imagePrivacy, boundsDp)
        if (placeholderLabel != null) {
            return CapturedWireframe.PrivacyPlaceholder(
                identity = mappingContext.identityFactory.placeholderWireframe(slotOwner),
                bounds = iconBounds,
                label = placeholderLabel
            )
        }

        val candidateAreaPx = drawable.intrinsicWidth.toLong() * drawable.intrinsicHeight.toLong()
        if (PixelCaptureEligibility.isTooLargeToCapture(candidateAreaPx, screenAreaPx)) return null

        val bitmap = compoundDrawableRasterizer.rasterize(
            drawable,
            drawable.intrinsicWidth,
            drawable.intrinsicHeight
        ) ?: return null
        val identity = mappingContext.identityFactory.imageWireframe(slotOwner)
        mappingContext.pendingPixelCaptureSink.register(
            PendingPixelCapture(
                wireframeIdentity = identity,
                ownerIdentity = mappingContext.ownerIdentity,
                bitmap = bitmap,
                isTextFree = true
            )
        )
        return CapturedWireframe.Pixel(
            identity = identity,
            bounds = iconBounds,
            resource = PixelResource.Unresolved
        )
    }

    protected open fun resolveCapturedText(view: T, textAndInputPrivacy: TextAndInputPrivacy): String {
        val text = resolveLayoutText(view)
        return when (textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            TextAndInputPrivacy.MASK_ALL_INPUTS -> text
            TextAndInputPrivacy.MASK_ALL -> StringObfuscator.getStringObfuscator().obfuscate(text)
        }
    }

    private fun isParagraphRtl(textView: TextView): Boolean {
        val layout = textView.layout
        if (layout != null && layout.lineCount > 0) {
            @Suppress("UnsafeThirdPartyFunctionCall") // The first line exists.
            return layout.getParagraphDirection(0) < 0
        }

        val heuristic = when (textView.textDirection) {
            TextView.TEXT_DIRECTION_LTR -> TextDirectionHeuristicsCompat.LTR
            TextView.TEXT_DIRECTION_RTL -> TextDirectionHeuristicsCompat.RTL
            TextView.TEXT_DIRECTION_ANY_RTL -> TextDirectionHeuristicsCompat.ANYRTL_LTR
            TextView.TEXT_DIRECTION_LOCALE -> TextDirectionHeuristicsCompat.LOCALE
            TextView.TEXT_DIRECTION_FIRST_STRONG_LTR -> TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR
            TextView.TEXT_DIRECTION_FIRST_STRONG_RTL -> TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL
            else -> if (textView.layoutDirection == TextView.LAYOUT_DIRECTION_RTL) {
                TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL
            } else {
                TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR
            }
        }
        val text = resolveLayoutText(textView)
        @Suppress("UnsafeThirdPartyFunctionCall") // Range covers the captured string.
        return heuristic.isRtl(text, 0, text.length)
    }
}

private data class CapturedTextViewData(
    val bounds: CapturedBounds,
    val text: String,
    val background: CapturedBackgroundColor?,
    val backgroundWireframes: List<CapturedWireframe>,
    val compoundDrawableWireframes: List<CapturedWireframe>,
    val family: String,
    val textSizePx: Long,
    val textColor: Int,
    val truncationMode: CapturedTruncationMode?,
    val paddingTopPx: Int,
    val paddingBottomPx: Int,
    val paddingLeftPx: Int,
    val paddingRightPx: Int,
    val textAlignment: Int,
    val gravity: Int,
    val isParagraphRtl: Boolean,
    val isLayoutRtl: Boolean,
    val hasLayout: Boolean,
    val colorStringFormatter: ColorStringFormatter
) : CapturedViewData {
    override val isOcclusionBounded: Boolean get() = compoundDrawableWireframes.isEmpty()
    override val hasOpaqueBackground: Boolean get() = background?.isOpaque == true

    override fun map(mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val wireframes = mutableListOf<CapturedWireframe>()
        background?.let {
            wireframes += CapturedWireframe.Shape(
                identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                bounds = bounds,
                style = it.resolve()
            )
        }
        wireframes += backgroundWireframes
        val density = mappingContext.screenDensity / mappingContext.viewScale
        wireframes += CapturedWireframe.Text(
            identity = mappingContext.identityFactory.textWireframe(mappingContext.ownerIdentity),
            bounds = bounds,
            text = text,
            textStyle = CapturedTextStyle(
                family = family,
                size = textSizePx.densityNormalized(density),
                color = colorStringFormatter.formatColorAsHexString(textColor),
                truncationMode = truncationMode
            ),
            textPosition = CapturedTextPosition(
                padding = CapturedPadding(
                    top = paddingTopPx.densityNormalized(density).toLong(),
                    bottom = paddingBottomPx.densityNormalized(density).toLong(),
                    left = paddingLeftPx.densityNormalized(density).toLong(),
                    right = paddingRightPx.densityNormalized(density).toLong()
                ),
                alignment = resolveAlignment()
            )
        )
        wireframes += compoundDrawableWireframes
        return CapturedViewMapperResult.Wireframes(wireframes)
    }

    private fun resolveAlignment(): CapturedAlignment {
        val horizontal = when (textAlignment) {
            TextView.TEXT_ALIGNMENT_CENTER -> CapturedHorizontalAlignment.CENTER
            TextView.TEXT_ALIGNMENT_TEXT_START -> relativeAlignment(isParagraphRtl, isEnd = false)
            TextView.TEXT_ALIGNMENT_TEXT_END -> relativeAlignment(isParagraphRtl, isEnd = true)
            TextView.TEXT_ALIGNMENT_VIEW_START -> relativeAlignment(isLayoutRtl, isEnd = false)
            TextView.TEXT_ALIGNMENT_VIEW_END -> relativeAlignment(isLayoutRtl, isEnd = true)
            TextView.TEXT_ALIGNMENT_GRAVITY -> return resolveAlignmentFromGravity()
            else -> CapturedHorizontalAlignment.LEFT
        }
        val vertical = if (hasLayout) CapturedVerticalAlignment.CENTER else resolveVerticalAlignment(gravity)
        return CapturedAlignment(horizontal, vertical)
    }

    private fun resolveAlignmentFromGravity(): CapturedAlignment {
        val horizontal = when (gravity.and(Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            Gravity.START -> relativeAlignment(isParagraphRtl, isEnd = false)
            Gravity.END -> relativeAlignment(isParagraphRtl, isEnd = true)
            Gravity.RIGHT -> CapturedHorizontalAlignment.RIGHT
            Gravity.CENTER_HORIZONTAL -> CapturedHorizontalAlignment.CENTER
            else -> CapturedHorizontalAlignment.LEFT
        }
        return CapturedAlignment(horizontal, resolveVerticalAlignment(gravity))
    }
}

private fun relativeAlignment(isRtl: Boolean, isEnd: Boolean): CapturedHorizontalAlignment =
    if (isRtl != isEnd) CapturedHorizontalAlignment.RIGHT else CapturedHorizontalAlignment.LEFT

private fun resolveVerticalAlignment(gravity: Int): CapturedVerticalAlignment =
    when (gravity.and(Gravity.VERTICAL_GRAVITY_MASK)) {
        Gravity.TOP -> CapturedVerticalAlignment.TOP
        Gravity.BOTTOM -> CapturedVerticalAlignment.BOTTOM
        else -> CapturedVerticalAlignment.CENTER
    }

private fun resolveLayoutText(textView: TextView): String =
    (textView.layout?.text ?: textView.text)?.toString().orEmpty().ifEmpty {
        textView.hint?.toString().orEmpty()
    }

/** [compoundDrawables] centers the icon on the view's cross-axis, pinned to its own edge on the main axis. */
private fun centerOffset(containerDp: Long, contentDp: Long): Long = (containerDp / 2) - (contentDp / 2)

/** [android.widget.TextView.getCompoundDrawables] is always ordered [left, top, right, bottom]. */
private const val LEFT_DRAWABLE_INDEX = 0
private const val TOP_DRAWABLE_INDEX = 1
private const val RIGHT_DRAWABLE_INDEX = 2
private const val BOTTOM_DRAWABLE_INDEX = 3

/**
 * Isolates the actual background-drawable-only draw so it can be substituted in tests. Draws only
 * [View.getBackground], never the full view - the view's own content (text, in
 * [CapturedTextViewMapper]'s case) is already captured separately, and baking it into this bitmap
 * too would render it twice.
 */
internal fun interface ViewBackgroundRasterizer {
    fun rasterize(view: View): Bitmap?
}

internal class DefaultViewBackgroundRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ViewBackgroundRasterizer {

    @Suppress("TooGenericExceptionCaught")
    override fun rasterize(view: View): Bitmap? {
        val background = view.background ?: return null
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val originalBounds = background.copyBounds()
            background.setBounds(0, 0, view.width, view.height)
            try {
                background.draw(Canvas(bitmap))
            } finally {
                // background is the live Drawable instance the real View still uses for its own
                // rendering - its bounds must be restored, not left at whatever this capture needed.
                background.bounds = originalBounds
            }
            bitmap
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to rasterize view background for pixel-fallback capture" },
                e
            )
            null
        }
    }
}

/**
 * Isolates a compound drawable's own draw so it can be substituted in tests. Draws only [drawable]
 * at its intrinsic size, never the owning [TextView] - its text is already captured separately.
 */
internal fun interface CompoundDrawableRasterizer {
    fun rasterize(drawable: Drawable, widthPx: Int, heightPx: Int): Bitmap?
}

internal class DefaultCompoundDrawableRasterizer(
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : CompoundDrawableRasterizer {

    @Suppress("TooGenericExceptionCaught")
    override fun rasterize(drawable: Drawable, widthPx: Int, heightPx: Int): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val originalBounds = drawable.copyBounds()
            drawable.setBounds(0, 0, widthPx, heightPx)
            try {
                drawable.draw(Canvas(bitmap))
            } finally {
                // drawable is the live instance the real View still uses for its own rendering -
                // its bounds must be restored, not left at whatever this capture needed.
                drawable.bounds = originalBounds
            }
            bitmap
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.TELEMETRY,
                { "Failed to rasterize compound drawable for pixel-fallback capture" },
                e
            )
            null
        }
    }
}
