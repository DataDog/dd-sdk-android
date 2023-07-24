/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.widget.ImageButton
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageCompression
import com.datadog.android.sessionreplay.internal.recorder.base64.WebPImageCompression
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

internal class ImageButtonMapper(
    webPImageCompression: ImageCompression = WebPImageCompression(),
    base64Serializer: Base64Serializer = Base64Serializer.Builder().build(),
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator
) : BaseWireframeMapper<ImageButton, MobileSegment.Wireframe>(
    webPImageCompression = webPImageCompression,
    base64Serializer = base64Serializer,
    uniqueIdentifierGenerator = uniqueIdentifierGenerator
) {
    override fun map(
        view: ImageButton,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe> {
        val resources = view.resources
        val drawable = view.drawable?.constantState?.newDrawable(resources)
        val id = resolveChildDrawableUniqueIdentifier(view)

        if (drawable == null || id == null) return emptyList()

        val screenDensity = mappingContext.systemInformation.screenDensity
        val bounds = resolveViewGlobalBounds(view, screenDensity)

        val (shapeStyle, border) = view.background?.resolveShapeStyleAndBorder(view.alpha)
            ?: (null to null)

        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        // if the drawable has no width/height then there's no point trying to get a bitmap
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            val imageWireframe = resolveImageWireframe(
                view,
                id,
                bounds,
                shapeStyle,
                border,
                drawable
            )
            wireframes.add(imageWireframe)
        }

        return wireframes
    }

    // region internal

    private fun resolveImageWireframe(
        view: ImageButton,
        id: Long,
        bounds: GlobalBounds,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?,
        drawable: Drawable
    ): MobileSegment.Wireframe.ImageWireframe {
        val mimeType = getWebPMimeType()
        val displayMetrics = view.resources.displayMetrics
        val applicationContext = view.context.applicationContext

        val imageWireframe =
            MobileSegment.Wireframe.ImageWireframe(
                id = id,
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                shapeStyle = shapeStyle,
                border = border,
                base64 = "",
                mimeType = mimeType,
                isEmpty = true
            )

        @Suppress("ThreadSafety") // TODO REPLAY-1861 caller thread of .map is unknown?
        handleBitmap(
            applicationContext = applicationContext,
            displayMetrics = displayMetrics,
            drawable = drawable,
            imageWireframe = imageWireframe
        )

        return imageWireframe
    }

    // endregion
}
