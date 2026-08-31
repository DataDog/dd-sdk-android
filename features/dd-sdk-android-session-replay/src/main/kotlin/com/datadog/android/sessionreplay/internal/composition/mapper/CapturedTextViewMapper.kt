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
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * The flagship semantic wireframe for this workstream: a text capture with [TextAndInputPrivacy]
 * masking already applied, matching legacy `TextViewMapper.resolveCapturedText`'s non-option
 * branch (`MASK_SENSITIVE_INPUTS`/`MASK_ALL_INPUTS` leave plain text as-is, `MASK_ALL` obfuscates
 * it). `EditText`-specific sensitive-field detection (password/email/phoneInputType) lives in
 * [CapturedEditTextMapper], registered ahead of this one. Font-family bucketing, truncation-mode
 * mapping, and padding/alignment resolution are pure functions over a [TextView] ported verbatim
 * from legacy `TextViewMapper`.
 *
 * Unlike a generic `ViewGroup`, a `TextView`/`Button` is a dedicated, directly-registered mapper -
 * it never falls through to [CapturedPixelFallbackMapper]. A background that
 * [backgroundShapeStyleResolver] can't reduce to a solid color (a ripple/selector/stateful
 * drawable, exactly what Material buttons commonly use) would otherwise be silently dropped
 * instead of shown at all, leaving styled buttons rendered as plain floating text with no visible
 * boundary. [backgroundRasterizer] rescues that case with the same privacy-gated pixel-capture
 * semantics [CapturedPixelFallbackMapper] applies elsewhere ([PixelCaptureEligibility]) - drawing
 * only the background drawable, never the full view, so the separately-emitted [CapturedWireframe.Text]
 * below isn't redundantly baked into the same bitmap.
 *
 * A compound drawable (a leading/trailing/top/bottom icon set via `drawableStart`/`drawableLeft`/
 * etc. in XML) is painted directly by [TextView.onDraw] itself - never as a separate child [View] -
 * so nothing else in the composition tree ever visits it. [resolveCompoundDrawableWireframes]
 * captures it the same way [resolveBackgroundWireframe] captures a non-solid background: pixel
 * capture rather than resolving to an Android resource id, keeping this mapper independent of
 * legacy's `ImageWireframeHelper`/`ResourceResolver` drawable-resolution path.
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

    override fun map(view: T, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val wireframes = mutableListOf<CapturedWireframe>()
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val capturedBounds = bounds.toCaptured()

        wireframes += resolveBackgroundWireframe(view, mappingContext, capturedBounds)

        wireframes += CapturedWireframe.Text(
            identity = mappingContext.identityFactory.textWireframe(mappingContext.ownerIdentity),
            bounds = capturedBounds,
            text = resolveCapturedText(view, mappingContext.textAndInputPrivacy),
            textStyle = resolveTextStyle(view, mappingContext.screenDensity, colorStringFormatter),
            textPosition = resolveTextPosition(view, mappingContext.screenDensity)
        )

        wireframes += resolveCompoundDrawableWireframes(view, mappingContext, capturedBounds)

        return CapturedViewMapperResult.Wireframes(wireframes)
    }

    /**
     * A solid color is resolved first and, if found, always wins - never privacy-sensitive, and
     * cheaper than a pixel capture. Only when that fails does a real (non-null) background become
     * a pixel-capture candidate; a null background (or zero-size/invisible view) is a plain
     * pass-through with nothing of its own to draw, matching [View.willNotDraw]'s
     * intent without depending on it - text views virtually always report `willNotDraw() == false`
     * regardless of background, since they always have their own text to draw.
     *
     * Deliberately does not fall back to [CapturedWireframe.PrivacyPlaceholder] the way
     * [CapturedPixelFallbackMapper] does for an ordinary View: a placeholder occupies these exact
     * same [capturedBounds] with its own "Content Image" label, but [CapturedWireframe.Text] below
     * is *always* emitted regardless (masked separately, per [TextAndInputPrivacy], not
     * [com.datadog.android.sessionreplay.ImagePrivacy]) - stacking a full-bounds masked-content
     * label under text that is deliberately shown unmasked produces a nonsensical, illegible
     * double-render, not a real privacy boundary. When privacy would otherwise require a
     * placeholder, this drops the background entirely instead - the same outcome legacy's
     * `TextViewMapper`/`ButtonMapper` produces for a non-solid background it can't rasterize
     * either (plain text, no visible chrome, not a masked box).
     */
    @Suppress("ReturnCount")
    private fun resolveBackgroundWireframe(
        view: T,
        mappingContext: CapturedMappingContext,
        capturedBounds: CapturedBounds
    ): List<CapturedWireframe> {
        backgroundShapeStyleResolver.resolve(view, internalLogger)?.let { style ->
            return listOf(
                CapturedWireframe.Shape(
                    identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                    bounds = capturedBounds,
                    style = style
                )
            )
        }

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

        val density = mappingContext.screenDensity
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

    /** Matches legacy `TextViewMapper.resolveCapturedText`'s non-option (`isOption = false`) branch. */
    protected open fun resolveCapturedText(view: T, textAndInputPrivacy: TextAndInputPrivacy): String {
        val originalText = resolveLayoutText(view)
        return when (textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            TextAndInputPrivacy.MASK_ALL_INPUTS -> originalText
            TextAndInputPrivacy.MASK_ALL -> StringObfuscator.getStringObfuscator().obfuscate(originalText)
        }
    }
}

// The functions below are pure functions of their arguments alone (no [CapturedTextViewMapper]
// instance state) - kept out of the class itself to stay within [TooManyFunctions]'s budget.

private fun resolveLayoutText(textView: TextView): String =
    (textView.layout?.text ?: textView.text)?.toString().orEmpty()

private fun resolveTextStyle(
    textView: TextView,
    pixelsDensity: Float,
    colorStringFormatter: ColorStringFormatter
): CapturedTextStyle {
    return CapturedTextStyle(
        family = resolveFontFamily(textView.typeface),
        size = textView.textSize.toLong().densityNormalized(pixelsDensity),
        color = resolveTextColor(textView, colorStringFormatter),
        truncationMode = resolveTruncationMode(textView)
    )
}

private fun resolveTextColor(textView: TextView, colorStringFormatter: ColorStringFormatter): String {
    // An empty/absent real text falls back to the hint color (if any), matching what the platform
    // actually renders - merged in from a dedicated resolveHintTextColor to stay within
    // [TooManyFunctions]'s file-level budget.
    val color = if (textView.text.isNullOrEmpty()) {
        textView.hintTextColors?.defaultColor ?: textView.currentTextColor
    } else {
        textView.currentTextColor
    }
    return colorStringFormatter.formatColorAndAlphaAsHexString(color, OPAQUE_ALPHA_VALUE)
}

private fun resolveFontFamily(typeface: Typeface?): String {
    return when (typeface) {
        Typeface.SANS_SERIF -> SANS_SERIF_FAMILY_NAME
        Typeface.MONOSPACE -> MONOSPACE_FAMILY_NAME
        Typeface.SERIF -> SERIF_FAMILY_NAME
        else -> SANS_SERIF_FAMILY_NAME
    }
}

private fun resolveTruncationMode(textView: TextView): CapturedTruncationMode? {
    return textView.ellipsize?.let { truncationMode ->
        when (truncationMode) {
            TextUtils.TruncateAt.START -> CapturedTruncationMode.HEAD
            TextUtils.TruncateAt.END -> CapturedTruncationMode.TAIL
            TextUtils.TruncateAt.MIDDLE -> CapturedTruncationMode.MIDDLE
            TextUtils.TruncateAt.MARQUEE -> CapturedTruncationMode.CLIP
        }
    }
}

private fun resolveTextPosition(textView: TextView, pixelsDensity: Float): CapturedTextPosition {
    return CapturedTextPosition(
        padding = resolvePadding(textView, pixelsDensity),
        alignment = resolveAlignment(textView)
    )
}

private fun resolvePadding(textView: TextView, pixelsDensity: Float): CapturedPadding {
    return if (textView.layout != null) {
        CapturedPadding(
            top = textView.totalPaddingTop.densityNormalized(pixelsDensity).toLong(),
            bottom = textView.totalPaddingBottom.densityNormalized(pixelsDensity).toLong(),
            left = textView.totalPaddingStart.densityNormalized(pixelsDensity).toLong(),
            right = textView.totalPaddingEnd.densityNormalized(pixelsDensity).toLong()
        )
    } else {
        CapturedPadding(
            top = textView.paddingTop.densityNormalized(pixelsDensity).toLong(),
            bottom = textView.paddingBottom.densityNormalized(pixelsDensity).toLong(),
            left = textView.paddingStart.densityNormalized(pixelsDensity).toLong(),
            right = textView.paddingEnd.densityNormalized(pixelsDensity).toLong()
        )
    }
}

private fun resolveAlignment(textView: TextView): CapturedAlignment {
    return when (textView.textAlignment) {
        TextView.TEXT_ALIGNMENT_CENTER -> CapturedAlignment(
            horizontal = CapturedHorizontalAlignment.CENTER,
            vertical = CapturedVerticalAlignment.CENTER
        )

        TextView.TEXT_ALIGNMENT_TEXT_END,
        TextView.TEXT_ALIGNMENT_VIEW_END -> CapturedAlignment(
            horizontal = CapturedHorizontalAlignment.RIGHT,
            vertical = CapturedVerticalAlignment.CENTER
        )

        TextView.TEXT_ALIGNMENT_TEXT_START,
        TextView.TEXT_ALIGNMENT_VIEW_START -> CapturedAlignment(
            horizontal = CapturedHorizontalAlignment.LEFT,
            vertical = CapturedVerticalAlignment.CENTER
        )

        // Merged in from a dedicated resolveAlignmentFromGravity to stay within
        // [TooManyFunctions]'s file-level budget.
        TextView.TEXT_ALIGNMENT_GRAVITY -> {
            val horizontal = when (textView.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK)) {
                Gravity.START,
                Gravity.LEFT -> CapturedHorizontalAlignment.LEFT

                Gravity.END,
                Gravity.RIGHT -> CapturedHorizontalAlignment.RIGHT

                Gravity.CENTER,
                Gravity.CENTER_HORIZONTAL -> CapturedHorizontalAlignment.CENTER

                else -> CapturedHorizontalAlignment.LEFT
            }
            val vertical = when (textView.gravity.and(Gravity.VERTICAL_GRAVITY_MASK)) {
                Gravity.TOP -> CapturedVerticalAlignment.TOP
                Gravity.BOTTOM -> CapturedVerticalAlignment.BOTTOM
                Gravity.CENTER_VERTICAL,
                Gravity.CENTER -> CapturedVerticalAlignment.CENTER

                else -> CapturedVerticalAlignment.CENTER
            }
            CapturedAlignment(horizontal, vertical)
        }

        else -> CapturedAlignment(
            horizontal = CapturedHorizontalAlignment.LEFT,
            vertical = CapturedVerticalAlignment.CENTER
        )
    }
}

/** [compoundDrawables] centers the icon on the view's cross-axis, pinned to its own edge on the main axis. */
private fun centerOffset(containerDp: Long, contentDp: Long): Long = (containerDp / 2) - (contentDp / 2)

private const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
private const val SERIF_FAMILY_NAME = "serif"
private const val MONOSPACE_FAMILY_NAME = "monospace"

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
