/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class ImageSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils
) : AbstractSemanticsNodeMapper(colorStringFormatter) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val bounds = resolveBounds(semanticsNode)
        val rawBitmapInfo = semanticsUtils.resolveSemanticsPainter(semanticsNode)
        val containerFrames = resolveModifierWireframes(semanticsNode).toMutableList()
        val imagePrivacy =
            semanticsUtils.getImagePrivacyOverride(semanticsNode) ?: parentContext.imagePrivacy

        val imageWireframe = rawBitmapInfo?.bitmap?.let { bitmap ->
            parentContext.imageWireframeHelper.createImageWireframeByBitmap(
                id = semanticsNode.id.toLong(),
                globalBounds = bounds,
                bitmap = bitmap,
                density = parentContext.density,
                isContextualImage = rawBitmapInfo.isContextualImage,
                imagePrivacy = imagePrivacy,
                asyncJobStatusCallback = asyncJobStatusCallback,
                clipping = null,
                shapeStyle = null,
                border = null
            )
        }

        imageWireframe?.let {
            containerFrames.add(it)
        }
        return SemanticsWireframe(
            wireframes = containerFrames,
            uiContext = null
        )
    }
}
