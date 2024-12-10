/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.ImagePrivacySemanticsPropertyKey
import com.datadog.android.sessionreplay.compose.SessionReplayHidePropertyKey
import com.datadog.android.sessionreplay.compose.TextInputSemanticsPropertyKey
import com.datadog.android.sessionreplay.compose.TouchSemanticsPropertyKey
import com.datadog.android.sessionreplay.compose.internal.data.BitmapInfo
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.utils.GlobalBounds

@Suppress("TooManyFunctions")
internal class SemanticsUtils(private val reflectionUtils: ReflectionUtils = ReflectionUtils()) {

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
        if (painter == null) {
            isContextualImage = true
            painter = reflectionUtils.getAsyncImagePainter(semanticsNode)
        }
        // TODO RUM-6535: support more painters.
        if (painter != null && reflectionUtils.isAsyncImagePainter(painter)) {
            isContextualImage = true
            painter = reflectionUtils.getNestedPainter(painter)
        }
        val bitmap = when (painter) {
            is BitmapPainter -> reflectionUtils.getBitmapInBitmapPainter(painter)
            is VectorPainter -> reflectionUtils.getBitmapInVectorPainter(painter)
            else -> {
                null
            }
        }

        val newBitmap = bitmap?.let {
            @Suppress("UnsafeThirdPartyFunctionCall") // isMutable is always false
            it.copy(Bitmap.Config.ARGB_8888, false)
        }
        return newBitmap?.let {
            BitmapInfo(it, isContextualImage)
        }
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
}
