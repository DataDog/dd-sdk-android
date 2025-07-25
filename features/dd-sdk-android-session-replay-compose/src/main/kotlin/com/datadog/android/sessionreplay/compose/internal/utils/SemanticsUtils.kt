/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
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
import com.datadog.android.sessionreplay.utils.GlobalBounds

@Suppress("TooManyFunctions")
internal class SemanticsUtils(
    private val reflectionUtils: ReflectionUtils = ReflectionUtils(),
    private val sampler: RateBasedSampler<Unit> = RateBasedSampler(BITMAP_TELEMETRY_SAMPLE_RATE)
) {

    internal fun findRootSemanticsNode(view: View): SemanticsNode? {
        reflectionUtils.apply {
            getComposition(view)?.takeIf { isWrappedCompositionClass(it) }?.let { composition ->
                getOwner(composition)?.takeIf { isAndroidComposeView(it) }?.let { owner ->
                    val semanticsOwner = reflectionUtils.getSemanticsOwner(owner)
                    return semanticsOwner?.unmergedRootSemanticsNode
                }
            }
        }
        return null
    }

    private fun resolveOuterBounds(semanticsNode: SemanticsNode): GlobalBounds {
        var currentBounds = resolveInnerBounds(semanticsNode)
        semanticsNode.layoutInfo.getModifierInfo().filter {
            reflectionUtils.isPaddingElement(it.modifier)
        }.forEach {
            val top = reflectionUtils.getTopPadding(it.modifier)
            val start = reflectionUtils.getStartPadding(it.modifier)
            val end = reflectionUtils.getEndPadding(it.modifier)
            val bottom = reflectionUtils.getBottomPadding(it.modifier)
            currentBounds = GlobalBounds(
                x = currentBounds.x - start.toLong(),
                y = currentBounds.y - top.toLong(),
                width = currentBounds.width + (end + start).toLong(),
                height = currentBounds.height + (bottom + top).toLong()
            )
        }
        return currentBounds
    }

    internal fun resolveBackgroundInfo(semanticsNode: SemanticsNode): List<BackgroundInfo> {
        val backgroundInfoList = mutableListOf<BackgroundInfo>()
        // CurrentBackgroundInfo is to store bounds, color and shape information in sequence of modifiers.
        var currentBackgroundInfo = BackgroundInfo()
        var currentBounds: GlobalBounds = resolveOuterBounds(semanticsNode)
        // If the currentBounds is already invalid, return with the existing wireframes
        if (!isGlobalBoundsValid(globalBounds = currentBounds)) {
            return backgroundInfoList
        }
        val density = semanticsNode.layoutInfo.density
        // Iterate all the modifiers in user calling sequence, when meet:
        // -> clip(): calculate the corner radius and update `currentBackgroundInfo`
        // -> padding(): shrink the bounds from the previous bounds and update `currentBackgroundInfo`
        // -> background(): retrieve the color and use `currentBackgroundInfo` to generate wireframes,
        //                  then reset `currentBackgroundInfo`.
        semanticsNode.layoutInfo.getModifierInfo().forEach { modifierInfo ->
            if (reflectionUtils.isBackgroundElement(modifierInfo.modifier)) {
                val color = reflectionUtils.getColor(modifierInfo.modifier)
                currentBackgroundInfo = currentBackgroundInfo.copy(globalBounds = currentBounds, color = color)
                backgroundInfoList.add(currentBackgroundInfo)
                currentBackgroundInfo = BackgroundInfo()
            } else if (reflectionUtils.isPaddingElement(modifierInfo.modifier)) {
                currentBounds = shrinkInnerBounds(modifierInfo.modifier, currentBounds)
                currentBackgroundInfo = currentBackgroundInfo.copy(globalBounds = currentBounds)
            } else if (reflectionUtils.isGraphicsLayerElement(modifierInfo.modifier)) {
                val cornerRadius =
                    resolveClipShape(modifierInfo.modifier, currentBounds, density) ?: 0f
                currentBackgroundInfo = currentBackgroundInfo.copy(cornerRadius = cornerRadius)
            }
        }
        return backgroundInfoList
    }

    internal fun resolveBackgroundColor(semanticsNode: SemanticsNode): Long? {
        val backgroundModifierInfo =
            semanticsNode.layoutInfo.getModifierInfo().firstOrNull { modifierInfo ->
                reflectionUtils.isBackgroundElement(modifierInfo.modifier)
            }
        return backgroundModifierInfo?.let { reflectionUtils.getColor(it.modifier) }
    }

    internal fun resolveBackgroundShape(semanticsNode: SemanticsNode): Shape? {
        val backgroundModifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            reflectionUtils.isBackgroundElement(it.modifier)
        }?.modifier
        return backgroundModifier?.let { reflectionUtils.getShape(it) }
    }

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

    private fun shrinkInnerBounds(
        modifier: Modifier,
        currentBounds: GlobalBounds
    ): GlobalBounds {
        val top = reflectionUtils.getTopPadding(modifier)
        val start = reflectionUtils.getStartPadding(modifier)
        val end = reflectionUtils.getEndPadding(modifier)
        val bottom = reflectionUtils.getBottomPadding(modifier)
        return GlobalBounds(
            x = currentBounds.x + start.toLong(),
            y = currentBounds.y + top.toLong(),
            width = currentBounds.width - (end + start).toLong(),
            height = currentBounds.height - (bottom + top).toLong()
        )
    }

    private fun isGlobalBoundsValid(globalBounds: GlobalBounds): Boolean {
        return (globalBounds.width > 0 && globalBounds.height > 0)
    }

    internal fun resolveInnerBounds(semanticsNode: SemanticsNode): GlobalBounds {
        val offset = semanticsNode.positionInRoot
        // Resolve the measured size.
        // Some semantics node doesn't have InnerLayerCoordinator, so use boundsInRoot as a fallback.
        val size = resolveInnerSize(semanticsNode) ?: semanticsNode.boundsInRoot.size
        val density = semanticsNode.layoutInfo.density.density
        val width = (size.width / density).toLong()
        val height = (size.height / density).toLong()
        val x = (offset.x / density).toLong()
        val y = (offset.y / density).toLong()
        return GlobalBounds(x, y, width, height)
    }

    private fun resolveInnerSize(semanticsNode: SemanticsNode): Size? {
        val placeable = reflectionUtils.getPlaceable(semanticsNode)
        val height = placeable?.height
        val width = placeable?.width
        return if (height != null && width != null) {
            Size(width = width.toFloat(), height = height.toFloat())
        } else {
            null
        }
    }

    private fun resolveClipShape(
        modifier: Modifier,
        currentBounds: GlobalBounds,
        density: Density
    ): Float? {
        return reflectionUtils.getClipShape(modifier)?.let { shape ->
            resolveCornerRadius(shape, currentBounds, density)
        }
    }

    internal fun resolveCornerRadius(
        shape: Shape,
        currentBounds: GlobalBounds,
        density: Density
    ): Float {
        val size = Size(
            currentBounds.width.toFloat() * density.density,
            currentBounds.height.toFloat() * density.density
        )
        // We only have a single value for corner radius, so we default to using the
        // top left (i.e.: topStart) corner's value and apply it to all corners
        // it.topStart.toPx(size, density) / density.density
        return if (shape is RoundedCornerShape) {
            shape.topStart.toPx(size, density) / density.density
        } else {
            0f
        }
    }

    internal fun resolveTextLayoutInfo(semanticsNode: SemanticsNode): TextLayoutInfo? {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        semanticsNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(
            textLayoutResults
        )
        return textLayoutResults.firstOrNull()?.let { textLayoutResult ->
            val layoutInput = textLayoutResult.layoutInput
            val multiParagraphCapturedText = if (textLayoutResult.didOverflowHeight) {
                reflectionUtils.getMultiParagraphCapturedText(textLayoutResult.multiParagraph)
            } else {
                null
            }
            val modifierColor = resolveModifierColor(semanticsNode)
            convertTextLayoutInfo(layoutInput, multiParagraphCapturedText, modifierColor)
        }
    }

    internal fun resolveSemanticsPainter(
        semanticsNode: SemanticsNode
    ): BitmapInfo? {
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
        val bitmap = when (painter) {
            is BitmapPainter -> reflectionUtils.getBitmapInBitmapPainter(painter)
            is VectorPainter -> reflectionUtils.getBitmapInVectorPainter(painter)
            else -> {
                logUnsupportedPainter(painter)
                null
            }
        }

        // Send telemetry about the original bitmap before copying it.
        bitmap?.let {
            sendBitmapInfoTelemetry(it, isContextualImage)
        }

        // Avoid copying hardware bitmap because it is slow and may violate [StrictMode#noteSlowCall]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap?.config == Bitmap.Config.HARDWARE) {
            return BitmapInfo(bitmap, isContextualImage)
        }
        val newBitmap = bitmap?.let {
            @Suppress("UnsafeThirdPartyFunctionCall") // isMutable is always false
            it.copy(Bitmap.Config.ARGB_8888, false)
        }
        return newBitmap?.let {
            BitmapInfo(it, isContextualImage)
        }
    }

    private fun sendBitmapInfoTelemetry(bitmap: Bitmap, isContextual: Boolean) {
        if (sampler.sample(Unit)) {
            (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
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

    private fun logUnsupportedPainter(painter: Painter?) {
        val painterType = painter?.javaClass?.simpleName ?: "null"
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
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

    private fun resolveModifierColor(semanticsNode: SemanticsNode): Color? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            reflectionUtils.isTextStringSimpleElement(it.modifier)
        }?.modifier
        return modifier?.let {
            reflectionUtils.getColorProducerColor(it)
        }
    }

    private fun convertTextLayoutInfo(
        layoutInput: TextLayoutInput,
        multiParagraphCapturedText: String?,
        modifierColor: Color?
    ): TextLayoutInfo {
        return TextLayoutInfo(
            text = multiParagraphCapturedText ?: resolveAnnotatedString(layoutInput.text),
            color = modifierColor?.value ?: layoutInput.style.color.value,
            textAlign = layoutInput.style.textAlign,
            fontSize = layoutInput.style.fontSize.value.toLong(),
            fontFamily = layoutInput.style.fontFamily
        )
    }

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

    internal companion object {
        internal enum class OnDrawFieldType {
            FILL_COLOR,
            CHECKMARK_COLOR,
            BORDER_COLOR,
            RADIO_BUTTON_COLOR
        }
        internal const val DEFAULT_COLOR_BLACK = "#000000FF"
        internal const val DEFAULT_COLOR_WHITE = "#FFFFFFFF"
        private const val BITMAP_TELEMETRY_SAMPLE_RATE = 1f
    }

    internal fun getProgressBarRangeInfo(semanticsNode: SemanticsNode): ProgressBarRangeInfo? {
        return semanticsNode.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
    }
}
