/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composition
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.Density
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.GetInnerLayerCoordinatorMethod
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import com.datadog.android.sessionreplay.utils.GlobalBounds

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

    internal fun resolveSemanticsModifierColor(
        semanticsNode: SemanticsNode
    ): Long? {
        val modifier = resolveSemanticsModifier(semanticsNode)
        return ComposeReflection.ColorField?.getSafe(modifier) as? Long
    }

    internal fun resolveInnerBounds(semanticsNode: SemanticsNode): GlobalBounds {
        val offset = semanticsNode.positionInRoot
        // Resolve the measured size.
        val size = resolveInnerSize(semanticsNode)
        val density = semanticsNode.layoutInfo.density.density
        val width = (size.width / density).toLong()
        val height = (size.height / density).toLong()
        val x = (offset.x / density).toLong()
        val y = (offset.y / density).toLong()
        return GlobalBounds(x, y, width, height)
    }

    private fun resolveInnerSize(semanticsNode: SemanticsNode): Size {
        val innerLayerCoordinator = GetInnerLayerCoordinatorMethod?.invoke(semanticsNode.layoutInfo)
        val placeable = innerLayerCoordinator as? Placeable
        val height = placeable?.height ?: 0
        val width = placeable?.width ?: 0
        return Size(width = width.toFloat(), height = height.toFloat())
    }

    internal fun resolveSemanticsModifierCornerRadius(
        semanticsNode: SemanticsNode,
        globalBounds: GlobalBounds,
        density: Density
    ): Float? {
        val size = Size(globalBounds.width.toFloat(), globalBounds.height.toFloat())
        val modifier = resolveSemanticsModifier(semanticsNode)
        val shape = ComposeReflection.ShapeField?.getSafe(modifier) as? RoundedCornerShape
        return shape?.let {
            // We only have a single value for corner radius, so we default to using the
            // top left (i.e.: topStart) corner's value and apply it to all corners
            it.topStart.toPx(size, density) / density.density
        }
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    internal fun resolveSemanticsModifier(semanticsNode: SemanticsNode): Modifier? {
        return semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            ComposeReflection.BackgroundElementClass?.isInstance(it.modifier) == true
        }?.modifier
    }
}
