/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.BitmapField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.ContentPainterModifierClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.ImageField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterElementClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterFieldOfAsyncImagePainter
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.PainterFieldOfContentPainter
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class ImageSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter
) : AbstractSemanticsNodeMapper(colorStringFormatter) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val bounds = resolveBounds(semanticsNode)
        val bitmapInfo = resolveSemanticsPainter(semanticsNode)
        val imageWireframe = if (bitmapInfo != null) {
            parentContext.imageWireframeHelper.createImageWireframeByBitmap(
                id = semanticsNode.id.toLong(),
                globalBounds = bounds,
                bitmap = bitmapInfo.bitmap,
                density = parentContext.density,
                isContextualImage = bitmapInfo.isContextualImage,
                // TODO RUM-6192: Apply FGM here
                imagePrivacy = ImagePrivacy.MASK_NONE,
                asyncJobStatusCallback = asyncJobStatusCallback,
                clipping = null,
                shapeStyle = null,
                border = null
            )
        } else {
            null
        }
        return SemanticsWireframe(
            wireframes = listOfNotNull(imageWireframe),
            uiContext = null
        )
    }

    private fun resolveSemanticsPainter(
        semanticsNode: SemanticsNode
    ): BitmapInfo? {
        var isContextualImage = false
        var painter = tryParseLocalImagePainter(semanticsNode)
        if (painter == null) {
            painter = tryParseAsyncImagePainter(semanticsNode)
            if (painter != null) {
                isContextualImage = true
            }
        }
        // TODO RUM-6535: support more painters.
        val bitmap = when (painter) {
            is BitmapPainter -> tryParseBitmapPainterToBitmap(painter)
            is VectorPainter -> tryParseVectorPainterToBitmap(painter)
            else -> {
                null
            }
        }

        val newBitmap = bitmap?.let {
            @Suppress("UnsafeThirdPartyFunctionCall") // isMutable is always false
            it.copy(it.config, false)
        }
        return newBitmap?.let {
            BitmapInfo(it, isContextualImage)
        }
    }

    private fun tryParseVectorPainterToBitmap(vectorPainter: VectorPainter): Bitmap? {
        val vector = ComposeReflection.VectorField?.getSafe(vectorPainter)
        val cacheDrawScope = ComposeReflection.CacheDrawScopeField?.getSafe(vector)
        val mCachedImage = ComposeReflection.CachedImageField?.getSafe(cacheDrawScope)
        return BitmapField?.getSafe(mCachedImage) as? Bitmap
    }

    private fun tryParseBitmapPainterToBitmap(bitmapPainter: BitmapPainter): Bitmap? {
        val image = ImageField?.getSafe(bitmapPainter)
        return BitmapField?.getSafe(image) as? Bitmap
    }

    private fun tryParseLocalImagePainter(semanticsNode: SemanticsNode): Painter? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            PainterElementClass?.isInstance(it.modifier) == true
        }?.modifier
        return PainterField?.getSafe(modifier) as? Painter
    }

    private fun tryParseAsyncImagePainter(semanticsNode: SemanticsNode): Painter? {
        val modifier = semanticsNode.layoutInfo.getModifierInfo().firstOrNull {
            ContentPainterModifierClass?.isInstance(it.modifier) == true
        }?.modifier
        val asyncPainter = PainterFieldOfContentPainter?.getSafe(modifier)
        return PainterFieldOfAsyncImagePainter?.getSafe(asyncPainter) as? Painter
    }

    private data class BitmapInfo(
        val bitmap: Bitmap,
        val isContextualImage: Boolean
    )
}
