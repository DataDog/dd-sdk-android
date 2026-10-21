/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.toSize
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.utils.GlobalBounds

internal data class RasterizedComposeText(val bitmap: Bitmap, val bounds: GlobalBounds)

/** Only paints the measured paragraph. It never draws or crops the host view or sibling content. */
internal fun interface ComposeTextRasterizer {
    fun rasterize(
        node: SemanticsNode,
        text: TextLayoutInfo,
        modifiers: List<ModifierInfo>,
        windowOffset: ComposeWindowOffset
    ): RasterizedComposeText?
}

internal class DefaultComposeTextRasterizer(
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : ComposeTextRasterizer {
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun rasterize(
        node: SemanticsNode,
        text: TextLayoutInfo,
        modifiers: List<ModifierInfo>,
        windowOffset: ComposeWindowOffset
    ): RasterizedComposeText? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        action.invoke(results)
        val layout = results.firstOrNull() ?: return null
        // The privacy decision was made for this exact text on the same main-thread traversal.
        val originalText = layout.layoutInput.text.text
        if (originalText != text.text &&
            !(layout.didOverflowHeight && originalText.startsWith(text.text))
        ) {
            return null
        }
        val size = layout.size
        if (size.width <= 0 || size.height <= 0 || size.width.toLong() * size.height > MAX_TEXT_PIXELS) return null
        val bounds = semanticsUtils.resolveTextBounds(node, windowOffset) ?: return null
        val density = node.layoutInfo.density
        val pixels = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        var completed = false
        return try {
            CanvasDrawScope().draw(
                density,
                layout.layoutInput.layoutDirection,
                Canvas(pixels.asImageBitmap()),
                size.toSize()
            ) {
                val color = if (layout.layoutInput.style.brush != null) Color.Unspecified else Color(text.color)
                drawText(layout, color = color)
            }
            val captured = RasterizedComposeText(pixels, bounds)
            completed = true
            captured
        } finally {
            if (!completed) pixels.recycle()
        }
    }

    private companion object {
        const val MAX_TEXT_PIXELS = 4_000_000L
    }
}
