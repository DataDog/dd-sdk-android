/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.ImagePrivacySemanticsPropertyKey
import com.datadog.android.sessionreplay.compose.SessionReplayHidePropertyKey
import com.datadog.android.sessionreplay.compose.TextInputSemanticsPropertyKey
import com.datadog.android.sessionreplay.compose.TouchSemanticsPropertyKey
import com.datadog.android.sessionreplay.compose.internal.data.BitmapInfo
import com.datadog.android.sessionreplay.compose.internal.isLeafNode
import com.datadog.android.sessionreplay.compose.internal.isPositionedAtOrigin
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas

@Suppress("TooManyFunctions")
internal class SemanticsUtils(
    private val reflectionUtils: ReflectionUtils = ReflectionUtils(),
    private val sampler: RateBasedSampler<Unit> = RateBasedSampler(BITMAP_TELEMETRY_SAMPLE_RATE),
    private val textCaptureCache: ComposeTextCaptureCache? = null
) {
    private val backgroundResolver = BackgroundResolver(
        reflectionUtils,
        { node, offset -> resolveInnerBounds(node, offset) }
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned child-index range, read on the UI thread.
    internal fun findRootSemanticsNode(view: View): SemanticsNode? {
        return when (view) {
            is RootForTest -> view.semanticsOwner.unmergedRootSemanticsNode
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
                findRootSemanticsNode(view.getChildAt(index))
            }
            else -> null
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // View children are inspected on their owning UI thread.
    internal fun findComposeRootView(view: View): View? = when (view) {
        is RootForTest -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findComposeRootView(view.getChildAt(it)) }
        else -> null
    }

    internal fun resolveBackgroundInfo(
        semanticsNode: SemanticsNode,
        windowOffset: ComposeWindowOffset = ComposeWindowOffset.NONE
    ): List<BackgroundInfo> =
        backgroundResolver.resolveBackgroundInfo(semanticsNode, windowOffset)

    internal fun resolveBackgroundColor(semanticsNode: SemanticsNode): Long? =
        backgroundResolver.resolveBackgroundColor(semanticsNode)

    internal fun resolveBackgroundColor(modifiers: List<ModifierInfo>): Long? =
        backgroundResolver.resolveBackgroundColor(modifiers)

    internal fun resolveBackgroundShape(modifiers: List<ModifierInfo>): Shape? =
        backgroundResolver.resolveBackgroundShape(modifiers)

    internal fun resolveBackgroundShape(semanticsNode: SemanticsNode): Shape? =
        backgroundResolver.resolveBackgroundShape(semanticsNode)

    internal fun resolveCheckPath(semanticsNode: SemanticsNode): Path? =
        resolveOnDrawInstance(semanticsNode)?.let { onDraw ->
            reflectionUtils.getCheckCache(onDraw)?.let { checkCache ->
                reflectionUtils.getCheckPath(checkCache)
            }
        }

    internal fun resolveCheckboxFillColor(semanticsNode: SemanticsNode): Long? =
        resolveOnDrawProperty(
            semanticsNode,
            OnDrawFieldType.FILL_COLOR
        )

    internal fun resolveCheckmarkColor(semanticsNode: SemanticsNode): Long? =
        resolveOnDrawProperty(
            semanticsNode,
            OnDrawFieldType.CHECKMARK_COLOR
        )

    internal fun resolveRadioButtonColor(semanticsNode: SemanticsNode): Long? =
        resolveOnDrawProperty(
            semanticsNode,
            OnDrawFieldType.RADIO_BUTTON_COLOR
        )

    internal fun resolveBorderColor(semanticsNode: SemanticsNode): Long? =
        resolveOnDrawProperty(
            semanticsNode,
            OnDrawFieldType.BORDER_COLOR
        )

    internal fun resolveCornerRadius(shape: Shape, currentBounds: GlobalBounds, density: Density): Float =
        backgroundResolver.resolveCornerRadius(shape, currentBounds, density)

    internal fun resolveInnerBounds(
        semanticsNode: SemanticsNode,
        windowOffset: ComposeWindowOffset = ComposeWindowOffset.NONE
    ): GlobalBounds {
        val offset = semanticsNode.positionInRoot
        // Resolve the measured size.
        // Some semantics node doesn't have InnerLayerCoordinator, so use boundsInRoot as a fallback.
        val size = resolveInnerSize(semanticsNode) ?: semanticsNode.boundsInRoot.size
        val density = semanticsNode.layoutInfo.density.density
        val width = (size.width / density).toLong()
        val height = (size.height / density).toLong()
        // positionInRoot is relative to the AndroidComposeView, not the screen — add the host
        // view's screen offset so bounds line up with the rest of the (screen-absolute) wireframes.
        val x = (offset.x / density).toLong() + windowOffset.xDp
        val y = (offset.y / density).toLong() + windowOffset.yDp
        return GlobalBounds(x, y, width, height)
    }

    private fun resolveInnerSize(semanticsNode: SemanticsNode): Size? {
        val coordinates = semanticsNode.layoutInfo.coordinates
        if (!coordinates.isAttached) return null
        val size = coordinates.size
        return Size(size.width.toFloat(), size.height.toFloat())
    }

    /** Composition image scaling needs the layout size, even when scrolling clips most of it. */
    internal fun resolveUnclippedBounds(
        semanticsNode: SemanticsNode,
        windowOffset: ComposeWindowOffset
    ): GlobalBounds {
        val position = semanticsNode.positionInRoot
        val size = semanticsNode.size
        val density = semanticsNode.layoutInfo.density.density
        return GlobalBounds(
            (position.x / density).toLong() + windowOffset.xDp,
            (position.y / density).toLong() + windowOffset.yDp,
            (size.width / density).toLong(),
            (size.height / density).toLong()
        )
    }

    /** Text is drawn inside all layout modifiers; semantics may describe the padded outer box. */
    @Suppress("UnsafeThirdPartyFunctionCall") // Attached coordinates are read during the same UI-thread traversal.
    internal fun resolveTextBounds(
        semanticsNode: SemanticsNode,
        windowOffset: ComposeWindowOffset
    ): GlobalBounds? {
        val coordinates = semanticsNode.layoutInfo.coordinates
        if (!coordinates.isAttached) return null
        val position = coordinates.positionInRoot()
        val size = coordinates.size
        val density = semanticsNode.layoutInfo.density.density
        return GlobalBounds(
            (position.x / density).toLong() + windowOffset.xDp,
            (position.y / density).toLong() + windowOffset.yDp,
            (size.width / density).toLong(),
            (size.height / density).toLong()
        )
    }

    /** Image semantics may wrap padding (notably in Coil); scale within the painter's own layout. */
    internal fun resolvePainterBounds(
        semanticsNode: SemanticsNode,
        modifiers: List<ModifierInfo>,
        windowOffset: ComposeWindowOffset
    ): GlobalBounds {
        val painter = modifiers.firstOrNull { reflectionUtils.isImagePainterModifier(it.modifier) }
        return painter?.let { resolveModifierBounds(semanticsNode, it, windowOffset) }
            ?: resolveUnclippedBounds(semanticsNode, windowOffset)
    }

    /** Read each modifier's measured rectangle, including its own position within padding. */
    @Suppress("UnsafeThirdPartyFunctionCall") // Attached coordinates, on UI thread, inside the compatibility gate.
    internal fun resolveModifierBounds(
        semanticsNode: SemanticsNode,
        modifier: ModifierInfo,
        windowOffset: ComposeWindowOffset
    ): GlobalBounds? {
        val coordinates = modifier.coordinates
        if (!coordinates.isAttached) return null
        val position = coordinates.positionInRoot()
        val size = coordinates.size
        val density = semanticsNode.layoutInfo.density.density
        return GlobalBounds(
            (position.x / density).toLong() + windowOffset.xDp,
            (position.y / density).toLong() + windowOffset.yDp,
            (size.width / density).toLong(),
            (size.height / density).toLong()
        )
    }

    /** The visible rectangle includes clipping imposed by Compose scroll containers. */
    internal fun resolveVisibleBounds(
        semanticsNode: SemanticsNode,
        windowOffset: ComposeWindowOffset
    ): GlobalBounds {
        val bounds = semanticsNode.boundsInRoot
        val density = semanticsNode.layoutInfo.density.density
        return GlobalBounds(
            (bounds.left / density).toLong() + windowOffset.xDp,
            (bounds.top / density).toLong() + windowOffset.yDp,
            (bounds.width / density).toLong(),
            (bounds.height / density).toLong()
        )
    }

    internal fun resolveTextLayoutInfo(semanticsNode: SemanticsNode, internalLogger: InternalLogger): TextLayoutInfo? =
        resolveTextLayoutInfo(semanticsNode, internalLogger) { semanticsNode.layoutInfo.getModifierInfo() }

    internal fun resolveTextLayoutInfo(
        semanticsNode: SemanticsNode,
        internalLogger: InternalLogger,
        modifiers: List<ModifierInfo>
    ): TextLayoutInfo? = resolveTextLayoutInfo(semanticsNode, internalLogger) { modifiers }

    @Suppress("ReturnCount") // Missing actions and valid cache hits avoid layout work.
    private fun resolveTextLayoutInfo(
        semanticsNode: SemanticsNode,
        internalLogger: InternalLogger,
        modifiers: () -> List<ModifierInfo>
    ): TextLayoutInfo? {
        val action = semanticsNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val inspectedTextModifier = if (textCaptureCache != null) {
            modifiers().firstOrNull { reflectionUtils.isTextStringSimpleElement(it.modifier) }?.modifier
        } else {
            null
        }
        val state = if (inspectedTextModifier != null) textCaptureCache?.state(semanticsNode, action) else null
        textCaptureCache?.get(semanticsNode.id, state, inspectedTextModifier)?.let { cached ->
            // ColorProducer can change without remeasurement or a new modifier instance.
            val color = inspectedTextModifier?.let { reflectionUtils.getColorProducerColor(it) }?.value
            return if (color != null && color != cached.color) cached.copy(color = color) else cached
        }
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        action.invoke(textLayoutResults)
        return textLayoutResults.firstOrNull()?.let { textLayoutResult ->
            val layoutInput = textLayoutResult.layoutInput
            val multiParagraphCapturedText = if (textLayoutResult.didOverflowHeight) {
                reflectionUtils.getMultiParagraphCapturedText(textLayoutResult.multiParagraph)
            } else {
                null
            }
            val textModifier = modifiers().firstOrNull { reflectionUtils.isTextStringSimpleElement(it.modifier) }
                ?.modifier
            val modifierColor = textModifier?.let { reflectionUtils.getColorProducerColor(it) }
            val textOverflow = resolveTextStringSimpleElementOverflow(textModifier, internalLogger)
            convertTextLayoutInfo(
                layoutInput,
                multiParagraphCapturedText,
                modifierColor,
                textOverflow,
                isMultiline = textLayoutResult.lineCount > 1
            ).also {
                textCaptureCache?.put(semanticsNode.id, state, textModifier, it)
            }
        }
    }

    internal fun resolveSemanticsPainter(
        semanticsNode: SemanticsNode,
        internalLogger: InternalLogger
    ): BitmapInfo? {
        val (painter, isContextualImage) = resolvePainter(semanticsNode)
        if (painter == null) return null

        val bitmap = resolveBitmapFromPainter(painter, internalLogger)

        bitmap?.let {
            sendBitmapInfoTelemetry(it, isContextualImage, internalLogger)
        }

        val contentScale = reflectionUtils.getContentScale(semanticsNode)
        val alignment = reflectionUtils.getAlignment(semanticsNode)

        // ALPHA_8 bitmaps cannot be copied to ARGB_8888 directly (copy returns null),
        // but they are handled separately downstream via Alpha8BitmapConverter.
        // Hardware bitmaps MUST be copied because Bitmap.compress() throws
        // IllegalStateException on hardware bitmaps.
        val finalBitmap = if (bitmap?.config == Bitmap.Config.ALPHA_8) {
            bitmap
        } else {
            @Suppress("UnsafeThirdPartyFunctionCall") // isMutable is always false
            bitmap?.copy(Bitmap.Config.ARGB_8888, false)
        }

        return finalBitmap?.let {
            BitmapInfo(
                bitmap = it,
                isContextualImage = isContextualImage,
                contentScale = contentScale,
                alignment = alignment
            )
        }
    }

    /** Composition captures painter output, including alpha and tint, rather than the vector's
     * internal ALPHA_8 coverage cache. Only this permitted source image is drawn, never the host.
     */
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    internal fun resolveCompositionPainter(
        semanticsNode: SemanticsNode,
        internalLogger: InternalLogger
    ): BitmapInfo? {
        val (painter, contextual) = resolvePainter(semanticsNode)
        if (painter == null) return null
        val source = resolveBitmapFromPainter(painter, internalLogger) ?: return null
        if (source.width <= 0 || source.height <= 0 || source.width.toLong() * source.height > MAX_SOURCE_PIXELS) {
            return null
        }
        val colorFilter = reflectionUtils.getImageColorFilter(semanticsNode)
        val alpha = reflectionUtils.getImageAlpha(semanticsNode).coerceIn(0f, 1f)
        if (canCopyPainterSource(painter, source, alpha, colorFilter)) {
            // Own a frozen software copy without also allocating and drawing an identical second
            // bitmap. The tinted/transparent and vector paths still render their painter output.
            val frozen = source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            return BitmapInfo(
                frozen,
                contextual,
                reflectionUtils.getContentScale(semanticsNode),
                reflectionUtils.getAlignment(semanticsNode)
            )
        }
        val pixels = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        var completed = false
        try {
            if (painter is VectorPainter) {
                CanvasDrawScope().draw(
                    semanticsNode.layoutInfo.density,
                    semanticsNode.layoutInfo.layoutDirection,
                    Canvas(pixels.asImageBitmap()),
                    Size(source.width.toFloat(), source.height.toFloat())
                ) {
                    with(painter) { draw(size, alpha, colorFilter) }
                }
            } else {
                if (!drawBitmapSource(source, pixels, alpha, colorFilter)) return null
            }
            completed = true
            return BitmapInfo(
                pixels,
                contextual,
                reflectionUtils.getContentScale(semanticsNode),
                reflectionUtils.getAlignment(semanticsNode)
            )
        } finally {
            if (!completed) pixels.recycle()
        }
    }

    private fun canCopyPainterSource(
        painter: Painter,
        source: Bitmap,
        alpha: Float,
        colorFilter: androidx.compose.ui.graphics.ColorFilter?
    ): Boolean {
        val unmodified = alpha == 1f && colorFilter == null
        return unmodified && painter !is VectorPainter && source.config != Bitmap.Config.ALPHA_8
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Supported software copy; the temporary source is always recycled.
    private fun drawBitmapSource(
        source: Bitmap,
        pixels: Bitmap,
        alpha: Float,
        colorFilter: androidx.compose.ui.graphics.ColorFilter?
    ): Boolean {
        // ALPHA_8 is directly drawable as a transparent mask; hardware images need a software copy.
        val software = if (source.config ==
            Bitmap.Config.ALPHA_8
        ) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (software == null) return false
        try {
            val paint = Paint().apply {
                this.alpha = (alpha * OPAQUE_ALPHA_VALUE).roundToInt()
                this.colorFilter = colorFilter?.asAndroidColorFilter()
            }
            AndroidCanvas(pixels).drawBitmap(software, null, Rect(0, 0, pixels.width, pixels.height), paint)
            return true
        } finally {
            if (software !== source) software.recycle()
        }
    }

    private fun resolvePainter(semanticsNode: SemanticsNode): Pair<Painter?, Boolean> {
        var isContextualImage = false
        var painter = reflectionUtils.getLocalImagePainter(semanticsNode)

        // Try to resolve Coil AsyncImagePainter.
        if (painter == null) {
            isContextualImage = true
            painter = reflectionUtils.getAsyncImagePainter(semanticsNode)
        }
        // In some versions of Coil, bitmap painter is nested in `AsyncImagePainter`
        if (painter != null && reflectionUtils.isAsyncImagePainter(painter)) {
            isContextualImage = true
            painter = reflectionUtils.getNestedPainter(painter)
        }
        // Try to resolve Coil3 painter if is still null.
        if (painter == null) {
            painter = reflectionUtils.getCoil3AsyncImagePainter(semanticsNode)
        }
        return painter to isContextualImage
    }

    private fun resolveBitmapFromPainter(painter: Painter, internalLogger: InternalLogger): Bitmap? {
        return when (painter) {
            is BitmapPainter -> reflectionUtils.getBitmapInBitmapPainter(painter)
            is VectorPainter -> reflectionUtils.getBitmapInVectorPainter(painter)
            else -> {
                logUnsupportedPainter(painter, internalLogger)
                null
            }
        }
    }

    private fun sendBitmapInfoTelemetry(bitmap: Bitmap, isContextual: Boolean, internalLogger: InternalLogger) {
        if (sampler.sample(Unit)) {
            internalLogger.log(
                level = InternalLogger.Level.INFO,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { "Resolved the bitmap from semantics node with id:${bitmap.generationId}" },
                additionalProperties = mapOf(
                    "bitmap.id" to bitmap.generationId,
                    "bitmap.byteCount" to bitmap.byteCount,
                    "bitmap.width" to bitmap.width,
                    "bitmap.height" to bitmap.height,
                    "bitmap.config" to bitmap.config,
                    "bitmap.isContextual" to isContextual
                )
            )
        }
    }

    private fun logUnsupportedPainter(painter: Painter?, internalLogger: InternalLogger) {
        val painterType = painter?.javaClass?.simpleName ?: "null"
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(
                InternalLogger.Target.MAINTAINER,
                InternalLogger.Target.TELEMETRY
            ),
            messageBuilder = { "Unsupported painter type in Compose: $painterType" },
            onlyOnce = true,
            additionalProperties = mapOf(
                "painter.type" to painterType
            )
        )
    }

    private fun resolveTextStringSimpleElementOverflow(
        modifier: Modifier?,
        internalLogger: InternalLogger
    ): MobileSegment.TruncationMode? {
        if (modifier == null) return null
        val overflowValue = reflectionUtils.getTextStringSimpleElementOverflow(modifier)
        return overflowValue?.let { overflow ->
            when (overflow) {
                is Int -> resolveTextOverflow(overflow, internalLogger)
                else -> {
                    // TextOverflow value class may be boxed when accessed via reflection
                    val overflowInt = extractTextOverflowValue(overflow, internalLogger)
                    if (overflowInt != null) {
                        resolveTextOverflow(overflowInt, internalLogger)
                    } else {
                        logUnknownOverflowType(overflow, internalLogger)
                        null
                    }
                }
            }
        }
    }

    private fun resolveTextOverflow(overflowMode: Int, internalLogger: InternalLogger): MobileSegment.TruncationMode? {
        return when (overflowMode) {
            TEXT_OVERFLOW_CLIP -> MobileSegment.TruncationMode.CLIP
            TEXT_OVERFLOW_ELLIPSE -> MobileSegment.TruncationMode.TAIL
            TEXT_OVERFLOW_VISIBLE -> null
            TEXT_OVERFLOW_ELLIPSIS_START -> MobileSegment.TruncationMode.HEAD
            TEXT_OVERFLOW_ELLIPSIS_MIDDLE -> MobileSegment.TruncationMode.MIDDLE
            else -> {
                logUnknownOverflowOrdinal(overflowMode, internalLogger)
                null
            }
        }
    }

    private fun extractTextOverflowValue(overflowValue: Any, internalLogger: InternalLogger): Int? {
        if (overflowValue !is TextOverflow) return null
        return try {
            // A missing or inaccessible boxed-value field is an expected compatibility failure.
            @Suppress("UnsafeThirdPartyFunctionCall")
            val valueField = overflowValue.javaClass.getDeclaredField("value")
            valueField.isAccessible = true
            // Reflection access and receiver-type failures are handled explicitly below.
            @Suppress("UnsafeThirdPartyFunctionCall")
            valueField.get(overflowValue) as? Int
        } catch (e: ReflectiveOperationException) {
            logReflectionExtractionFailure(overflowValue, e, internalLogger)
            null
        } catch (e: SecurityException) {
            logReflectionExtractionFailure(overflowValue, e, internalLogger)
            null
        } catch (e: IllegalArgumentException) {
            logReflectionExtractionFailure(overflowValue, e, internalLogger)
            null
        }
    }

    private fun logReflectionExtractionFailure(overflowValue: Any, e: Throwable, internalLogger: InternalLogger) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER),
            messageBuilder = {
                "Failed to extract Int value from TextOverflow instance via reflection: ${e.message}. " +
                    "Type: ${overflowValue.javaClass.name}"
            },
            throwable = e,
            onlyOnce = true,
            additionalProperties = mapOf(
                OVERFLOW_TYPE_KEY to (overflowValue::class.simpleName ?: UNKNOWN_VALUE),
                ERROR_TYPE_KEY to (e::class.simpleName ?: UNKNOWN_VALUE),
                COMPONENT_KEY to COMPONENT_NAME
            )
        )
    }

    private fun logUnknownOverflowOrdinal(ordinal: Int, internalLogger: InternalLogger) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = {
                "Unknown TextOverflow Int value: $ordinal. " +
                    "This may indicate a new Compose TextOverflow value class instance that needs to be mapped."
            },
            onlyOnce = true,
            additionalProperties = mapOf(
                "overflow.value" to ordinal.toString(),
                COMPONENT_KEY to COMPONENT_NAME
            )
        )
    }

    private fun logUnknownOverflowType(overflowValue: Any, internalLogger: InternalLogger) {
        internalLogger.log(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = {
                "Unexpected type for TextOverflow value: ${overflowValue::class.simpleName}. " +
                    "Expected Int or TextOverflow value class instance, got ${overflowValue.javaClass.name}. " +
                    "This may indicate a Compose API change."
            },
            onlyOnce = true,
            additionalProperties = mapOf(
                OVERFLOW_TYPE_KEY to (overflowValue::class.simpleName ?: UNKNOWN_VALUE),
                COMPONENT_KEY to COMPONENT_NAME
            )
        )
    }

    private fun convertTextLayoutInfo(
        layoutInput: TextLayoutInput,
        multiParagraphCapturedText: String?,
        modifierColor: Color?,
        textOverflow: MobileSegment.TruncationMode?,
        isMultiline: Boolean
    ): TextLayoutInfo {
        return TextLayoutInfo(
            text = multiParagraphCapturedText ?: resolveAnnotatedString(layoutInput.text),
            sourceText = layoutInput.text.text,
            color = modifierColor?.value ?: layoutInput.style.color.value,
            textAlign = layoutInput.style.textAlign,
            fontSize = layoutInput.style.fontSize.value
                .let { if (it.isNaN()) DEFAULT_FONT_SIZE_SP else it.toLong() },
            fontFamily = layoutInput.style.fontFamily,
            textOverflow = textOverflow,
            // The flat text format cannot preserve the paragraph's measured breaks and baselines.
            requiresPixelCapture = isMultiline || requiresTextPixels(layoutInput)
        )
    }

    // The wire format cannot represent these styles. Keep the measured glyphs instead of
    // substituting a font or asking the player to reflow differently styled text.
    private fun requiresTextPixels(input: TextLayoutInput): Boolean {
        val style = input.style
        return input.text.spanStyles.isNotEmpty() || hasUnsupportedFontStyle(style) || hasCustomTextSpacing(style) ||
            style.brush != null || (style.shadow != null && style.shadow != Shadow.None) ||
            (style.textDecoration != null && style.textDecoration != TextDecoration.None)
    }

    private fun hasUnsupportedFontStyle(style: TextStyle): Boolean =
        (style.fontWeight != null && style.fontWeight != FontWeight.Normal) ||
            (style.fontStyle != null && style.fontStyle != FontStyle.Normal)

    private fun hasCustomTextSpacing(style: TextStyle): Boolean =
        ((style.letterSpacing.isSp || style.letterSpacing.isEm) && style.letterSpacing.value != 0f) ||
            style.lineHeight.isSp || style.lineHeight.isEm

    internal fun getImagePrivacyOverride(semanticsNode: SemanticsNode): ImagePrivacy? {
        return semanticsNode.config.getOrNull(ImagePrivacySemanticsPropertyKey)
    }

    internal fun getTextAndInputPrivacyOverride(semanticsNode: SemanticsNode): TextAndInputPrivacy? {
        return semanticsNode.config.getOrNull(TextInputSemanticsPropertyKey)
    }

    internal fun getTouchPrivacyOverride(semanticsNode: SemanticsNode): TouchPrivacy? {
        return semanticsNode.config.getOrNull(TouchSemanticsPropertyKey)
    }

    internal fun isNodeHidden(semanticsNode: SemanticsNode): Boolean {
        return semanticsNode.config.getOrNull(SessionReplayHidePropertyKey) ?: false
    }

    internal fun isEditable(semanticsNode: SemanticsNode): Boolean =
        semanticsNode.config.contains(SemanticsActions.SetText)

    internal fun isImage(semanticsNode: SemanticsNode): Boolean =
        semanticsNode.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Image

    internal fun isAdjustable(semanticsNode: SemanticsNode): Boolean =
        semanticsNode.config.contains(SemanticsActions.SetProgress)

    internal fun isPassword(semanticsNode: SemanticsNode): Boolean =
        semanticsNode.config.contains(SemanticsProperties.Password)

    internal fun editableText(semanticsNode: SemanticsNode): String? =
        semanticsNode.config.getOrNull(SemanticsProperties.EditableText)?.let(::resolveAnnotatedString)

    internal fun hasInputState(semanticsNode: SemanticsNode): Boolean =
        isEditable(semanticsNode) || semanticsNode.config.contains(SemanticsProperties.ToggleableState) ||
            semanticsNode.config.contains(SemanticsProperties.Selected) ||
            semanticsNode.config.contains(SemanticsProperties.ProgressBarRangeInfo)

    internal fun isNodePositionUnavailable(semanticsNode: SemanticsNode): Boolean {
        return semanticsNode.isLeafNode() && semanticsNode.isPositionedAtOrigin()
    }

    internal fun getInteropView(semanticsNode: SemanticsNode): View? {
        return reflectionUtils.getInteropView(semanticsNode)
    }

    private fun resolveOnDrawInstance(semanticsNode: SemanticsNode): Any? {
        val drawBehindElement =
            semanticsNode.layoutInfo.getModifierInfo().firstOrNull { modifierInfo ->
                reflectionUtils.isDrawBehindElementClass(modifierInfo.modifier)
            }?.modifier

        return drawBehindElement?.let {
            reflectionUtils.getOnDraw(it)
        }
    }

    private fun resolveOnDrawProperty(semanticsNode: SemanticsNode, fieldType: OnDrawFieldType): Long? {
        val onDrawInstance = resolveOnDrawInstance(semanticsNode)

        val color = onDrawInstance?.let {
            when (fieldType) {
                OnDrawFieldType.FILL_COLOR -> {
                    reflectionUtils.getBoxColor(onDrawInstance)
                }
                OnDrawFieldType.CHECKMARK_COLOR -> {
                    reflectionUtils.getCheckColor(onDrawInstance)
                }
                OnDrawFieldType.BORDER_COLOR -> {
                    reflectionUtils.getBorderColor(onDrawInstance)
                }
                OnDrawFieldType.RADIO_BUTTON_COLOR -> {
                    reflectionUtils.getRadioColor(onDrawInstance)
                }
            }
        }

        val result = (color?.value as? Color)?.value

        return result?.toLong()
    }

    internal fun getProgressBarRangeInfo(semanticsNode: SemanticsNode): ProgressBarRangeInfo? {
        return semanticsNode.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
    }

    internal companion object {
        private const val MAX_SOURCE_PIXELS = 4_000_000L
        internal enum class OnDrawFieldType {
            FILL_COLOR,
            CHECKMARK_COLOR,
            BORDER_COLOR,
            RADIO_BUTTON_COLOR
        }
        internal const val DEFAULT_COLOR_BLACK = "#000000FF"
        internal const val DEFAULT_COLOR_WHITE = "#FFFFFFFF"

        /** Raw Long encoding of [androidx.compose.ui.graphics.Color.Unspecified] (color-space id 16). */
        internal const val COLOR_UNSPECIFIED = 16L

        private const val BITMAP_TELEMETRY_SAMPLE_RATE = 1f

        private const val COMPONENT_NAME = "SemanticsUtils"
        private const val COMPONENT_KEY = "component"
        private const val UNKNOWN_VALUE = "unknown"
        private const val OVERFLOW_TYPE_KEY = "overflow.type"
        private const val ERROR_TYPE_KEY = "error.type"

        internal const val DEFAULT_FONT_SIZE_SP = 12L
        internal const val TEXT_OVERFLOW_CLIP = 1
        internal const val TEXT_OVERFLOW_ELLIPSE = 2
        internal const val TEXT_OVERFLOW_VISIBLE = 3
        internal const val TEXT_OVERFLOW_ELLIPSIS_START = 4
        internal const val TEXT_OVERFLOW_ELLIPSIS_MIDDLE = 5
    }
}
