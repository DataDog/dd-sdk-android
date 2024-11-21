/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.view.View
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composition
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.GetInnerLayerCoordinatorMethod
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.LayoutNodeField
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import com.datadog.android.sessionreplay.utils.GlobalBounds

@Suppress("TooManyFunctions")
internal class SemanticsUtils {

    internal fun findRootSemanticsNode(view: View): SemanticsNode? {
        val composition = CompositionField?.getSafe(view) as? Composition
        if (ComposeReflection.WrappedCompositionClass?.isInstance(composition) == true) {
            val owner = ComposeReflection.OwnerField?.getSafe(composition)
            if (ComposeReflection.AndroidComposeViewClass?.isInstance(owner) == true) {
                val semanticsOwner = ComposeReflection.SemanticsOwner?.getSafe(owner) as? SemanticsOwner
                val rootNode = semanticsOwner?.unmergedRootSemanticsNode
                return rootNode
            }
        }
        return null
    }

    internal fun resolveOuterBounds(semanticsNode: SemanticsNode): GlobalBounds {
        var currentBounds = resolveInnerBounds(semanticsNode)
        semanticsNode.layoutInfo.getModifierInfo().filter {
            (ComposeReflection.PaddingElementClass?.isInstance(it.modifier) == true)
        }.forEach {
            val top = ComposeReflection.TopField?.getSafe(it.modifier) as? Float ?: 0.0f
            val start = ComposeReflection.StartField?.getSafe(it.modifier) as? Float ?: 0.0f
            val end = ComposeReflection.EndField?.getSafe(it.modifier) as? Float ?: 0.0f
            val bottom = ComposeReflection.BottomField?.getSafe(it.modifier) as? Float ?: 0.0f
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
            if (ComposeReflection.BackgroundElementClass?.isInstance(modifierInfo.modifier) == true) {
                val color = ComposeReflection.ColorField?.getSafe(modifierInfo.modifier) as? Long
                currentBackgroundInfo = currentBackgroundInfo.copy(globalBounds = currentBounds, color = color)
                backgroundInfoList.add(currentBackgroundInfo)
                currentBackgroundInfo = BackgroundInfo()
            } else if (ComposeReflection.PaddingElementClass?.isInstance(modifierInfo.modifier) == true) {
                currentBounds = shrinkInnerBounds(modifierInfo.modifier, currentBounds)
                currentBackgroundInfo = currentBackgroundInfo.copy(globalBounds = currentBounds)
            } else if (ComposeReflection.GraphicsLayerElementClass?.isInstance(modifierInfo.modifier) == true) {
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
                ComposeReflection.BackgroundElementClass?.isInstance(modifierInfo.modifier) == true
            }
        return backgroundModifierInfo?.let {
            ComposeReflection.ColorField?.getSafe(it.modifier) as? Long
        }
    }

    internal fun resolveBackgroundShape(semanticsNode: SemanticsNode): Shape? {
        val backgroundModifierInfo =
            semanticsNode.layoutInfo.getModifierInfo().firstOrNull { modifierInfo ->
                ComposeReflection.BackgroundElementClass?.isInstance(modifierInfo.modifier) == true
            }
        return backgroundModifierInfo?.let {
            ComposeReflection.ShapeField?.getSafe(it.modifier) as? Shape
        }
    }

    private fun shrinkInnerBounds(
        modifier: Modifier,
        currentBounds: GlobalBounds
    ): GlobalBounds {
        val top = ComposeReflection.TopField?.getSafe(modifier) as? Float ?: 0.0f
        val start = ComposeReflection.StartField?.getSafe(modifier) as? Float ?: 0.0f
        val end = ComposeReflection.EndField?.getSafe(modifier) as? Float ?: 0.0f
        val bottom = ComposeReflection.BottomField?.getSafe(modifier) as? Float ?: 0.0f
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
        val layoutNode = LayoutNodeField?.getSafe(semanticsNode)
        val innerLayerCoordinator = layoutNode?.let { GetInnerLayerCoordinatorMethod?.invoke(it) }
        val placeable = innerLayerCoordinator as? Placeable
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
        val shape = ComposeReflection.ClipShapeField?.getSafe(modifier) as? Shape
        return shape?.let {
            resolveCornerRadius(it, currentBounds, density)
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
        val layoutInput = textLayoutResults.firstOrNull()?.layoutInput
        return layoutInput?.let {
            convertTextLayoutInfo(it)
        }
    }

    private fun convertTextLayoutInfo(layoutInput: TextLayoutInput): TextLayoutInfo {
        return TextLayoutInfo(
            text = resolveAnnotatedString(layoutInput.text),
            color = layoutInput.style.color.value,
            textAlign = layoutInput.style.textAlign,
            fontSize = layoutInput.style.fontSize.value.toLong(),
            fontFamily = layoutInput.style.fontFamily
        )
    }
}
