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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.toSize
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
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
    private val reflectionUtils: ReflectionUtils = ReflectionUtils(),
    private val internalLogger: InternalLogger = InternalLogger.UNBOUND
) : ComposeTextRasterizer {
    @Suppress("ReturnCount", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    override fun rasterize(
        node: SemanticsNode,
        text: TextLayoutInfo,
        modifiers: List<ModifierInfo>,
        windowOffset: ComposeWindowOffset
    ): RasterizedComposeText? {
        var bitmap: Bitmap? = null
        return try {
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
            val coordinates = modifiers.firstOrNull {
                reflectionUtils.isTextStringSimpleElement(it.modifier)
            }?.coordinates
            val position = if (coordinates?.isAttached == true) coordinates.positionInRoot() else node.positionInRoot
            val density = node.layoutInfo.density
            val bounds = GlobalBounds(
                (position.x / density.density).toLong() + windowOffset.xDp,
                (position.y / density.density).toLong() + windowOffset.yDp,
                (size.width / density.density).toLong(),
                (size.height / density.density).toLong()
            )
            val pixels = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            bitmap = pixels
            CanvasDrawScope().draw(
                density,
                layout.layoutInput.layoutDirection,
                Canvas(pixels.asImageBitmap()),
                size.toSize()
            ) {
                val color = if (layout.layoutInput.style.brush != null) Color.Unspecified else Color(text.color)
                drawText(layout, color = color)
            }
            RasterizedComposeText(pixels, bounds)
        } catch (e: Exception) {
            bitmap?.recycle()
            logFailure(e)
            null
        } catch (e: OutOfMemoryError) {
            bitmap?.recycle()
            logFailure(e)
            null
        }
    }

    private fun logFailure(error: Throwable) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Unable to rasterize the Compose text layout." },
            error,
            onlyOnce = true
        )
    }

    private companion object {
        const val MAX_TEXT_PIXELS = 4_000_000L
    }
}
