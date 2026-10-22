/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.sessionreplay.compose.internal.reflection.getClassSafe
import com.datadog.android.sessionreplay.compose.internal.reflection.getDeclaredFieldSafe
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset

internal data class ComposePlacementClip(
    val shape: Shape,
    val bounds: CapturedBounds,
    val shadowElevation: Float? = null,
    val spotShadowColor: Color? = null
)

/** Read an applied placement clip and elevation without invoking application layer blocks. */
internal class ComposePlacementClipResolver {
    private val coordinator = getClassSafe("androidx.compose.ui.node.NodeCoordinator", isCritical = false)
    private val clip = coordinator?.methods?.firstOrNull {
        it.parameterTypes.isEmpty() && it.name.substringBefore('$') == "getLastClip"
    }
    private val shape = coordinator?.methods?.firstOrNull {
        it.parameterTypes.isEmpty() && it.name.substringBefore('$') == "getLastShape"
    }

    private val ownerLayer = getClassSafe("androidx.compose.ui.platform.GraphicsLayerOwnerLayer", isCritical = false)
    private val graphicsField = ownerLayer?.getDeclaredFieldSafe("graphicsLayer", isCritical = false)
    private val graphicsLayer = getClassSafe("androidx.compose.ui.graphics.layer.GraphicsLayer", isCritical = false)
    private val elevation = graphicsLayer?.methods?.firstOrNull {
        it.parameterTypes.isEmpty() && it.name == "getShadowElevation"
    }
    private val shadowColor = graphicsLayer?.methods?.firstOrNull {
        it.parameterTypes.isEmpty() && it.name.substringBefore('-') == "getSpotShadowColor"
    }

    private val available = clip != null && shape != null

    // Optional Android layer metadata; no drawing or callbacks.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    private fun shadowOf(layer: Any): Pair<Float, Color?>? {
        if (ownerLayer?.isInstance(layer) != true) return null
        return try {
            val graphics = graphicsField?.getSafe(layer) ?: return null
            val value = (elevation?.invoke(graphics) as? Float)?.takeIf { it.isFinite() && it > 0f } ?: return null
            val color = (shadowColor?.invoke(graphics) as? Long)?.let { Color(it.toULong()) }
            value to color
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Older runtimes lacking optional cached properties retain modifier-based capture. */
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Optional metadata, UI thread.
    fun resolve(
        node: SemanticsNode,
        offset: ComposeWindowOffset,
        modifiers: List<ModifierInfo>
    ): ComposePlacementClip? {
        if (!available) return null
        return try {
            val modifier = modifiers.firstOrNull { ownerLayer?.isInstance(it.extra) == true } ?: return null
            val coordinates = modifier.coordinates
            if (!coordinates.isAttached || clip?.invoke(coordinates) != true) return null
            val ownedLayer = modifier.extra ?: return null
            val currentShape = shape?.invoke(coordinates) as? Shape ?: return null
            val position = coordinates.positionInRoot()
            val size = coordinates.size
            val density = node.layoutInfo.density.density
            val left = (position.x / density).toLong()
            val top = (position.y / density).toLong()
            val bounds = CapturedBounds(
                left + offset.xDp,
                top + offset.yDp,
                ((position.x + size.width) / density).toLong() - left,
                ((position.y + size.height) / density).toLong() - top
            )
            if (bounds.width <= 0 || bounds.height <= 0) return null
            val shadow = shadowOf(ownedLayer)
            ComposePlacementClip(currentShape, bounds, shadow?.first, shadow?.second)
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
