/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils

/** Resolves only a uniform fill drawn by this modifier. It never draws content or reads host pixels. */
internal fun interface ComposeDrawBehindColorResolver {
    fun resolve(node: SemanticsNode, modifier: ModifierInfo): Color?
}

internal class DefaultComposeDrawBehindColorResolver(
    private val reflectionUtils: ReflectionUtils = ReflectionUtils()
) : ComposeDrawBehindColorResolver {
    @Suppress("UNCHECKED_CAST", "ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun resolve(node: SemanticsNode, modifier: ModifierInfo): Color? {
        val onDraw = reflectionUtils.getOnDraw(modifier.modifier) as? (DrawScope.() -> Unit) ?: return null
        val coordinates = modifier.coordinates
        if (!coordinates.isAttached) return null
        val size = coordinates.size
        if (size.width <= 0 || size.height <= 0 || size.width.toLong() * size.height > MAX_PIXELS) return null
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        return try {
            CanvasDrawScope().draw(
                node.layoutInfo.density,
                node.layoutInfo.layoutDirection,
                Canvas(bitmap.asImageBitmap()),
                Size(size.width.toFloat(), size.height.toFloat()),
                onDraw
            )
            val pixels = IntArray(size.width * size.height)
            bitmap.getPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
            val color = pixels[0]
            // Nonuniform drawing may contain text or images. Never treat it as a privacy-resolved fill.
            if (android.graphics.Color.alpha(color) != 0 && pixels.all { it == color }) Color(color) else null
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_PIXELS = 4_000_000L
    }
}
