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
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeTextCaptureCache
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
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
    private val measuredTextState = ComposeTextCaptureCache()
    private val bitmapCache = ComposeTextBitmapCache()
    private val reflectionUtils = ReflectionUtils()

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun rasterize(
        node: SemanticsNode,
        text: TextLayoutInfo,
        modifiers: List<ModifierInfo>,
        windowOffset: ComposeWindowOffset
    ): RasterizedComposeText? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val sourceText = text.sourceText ?: text.text
        val state = measuredTextState.state(node, action)
        val bounds = semanticsUtils.resolveTextBounds(node, windowOffset) ?: return null
        val modifier = modifiers.firstOrNull { reflectionUtils.isTextStringSimpleElement(it.modifier) }?.modifier
        val paint = ComposeTextBitmapCache.Paint(modifier, text.color.toLong(), sourceText)
        bitmapCache.get(node.id, state, paint)?.let { return RasterizedComposeText(it, bounds) }
        val results = mutableListOf<TextLayoutResult>()
        action.invoke(results)
        val layout = results.firstOrNull() ?: return null
        // The privacy decision was made for this exact text on the same main-thread traversal.
        val originalText = layout.layoutInput.text.text
        if (originalText != sourceText) {
            return null
        }
        val paintStyle = layout.layoutInput.style
        val size = layout.size
        if (size.width <= 0 || size.height <= 0 || size.width.toLong() * size.height > MAX_TEXT_PIXELS) return null
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
                drawText(measuredLayout(layout), color = color)
            }
            bitmapCache.put(node.id, state, paint, paintStyle, pixels)
            val captured = RasterizedComposeText(pixels, bounds)
            completed = true
            captured
        } finally {
            if (!completed) pixels.recycle()
        }
    }

    /** Simple text semantics can reconstruct a paragraph at the parent's maximum width while
     * retaining the narrower measured size. Center/end alignment must use the measured width.
     * Unwrapped overflowing text deliberately retains its original paragraph width.
     */
    // Valid measured constraints; the ellipsis constructor supports older Compose binaries.
    @Suppress("DEPRECATION", "UnsafeThirdPartyFunctionCall")
    private fun measuredLayout(layout: TextLayoutResult): TextLayoutResult {
        val input = layout.layoutInput
        val unwrappedOverflow = !input.softWrap && input.overflow != TextOverflow.Ellipsis
        val invalidWidth = layout.size.width <= 0 || layout.multiParagraph.width <= layout.size.width
        if (unwrappedOverflow || invalidWidth) return layout
        return TextLayoutResult(
            input,
            MultiParagraph(
                intrinsics = layout.multiParagraph.intrinsics,
                constraints = Constraints(maxWidth = layout.size.width, maxHeight = input.constraints.maxHeight),
                maxLines = if (!input.softWrap && input.overflow == TextOverflow.Ellipsis) 1 else input.maxLines,
                ellipsis = input.overflow == TextOverflow.Ellipsis
            ),
            layout.size
        )
    }

    private companion object {
        const val MAX_TEXT_PIXELS = 4_000_000L
    }
}
