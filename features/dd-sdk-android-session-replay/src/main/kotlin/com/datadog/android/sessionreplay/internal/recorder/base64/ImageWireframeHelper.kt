/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

internal class ImageWireframeHelper(
    private val imageCompression: ImageCompression = WebPImageCompression(),
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    private val base64Serializer: Base64Serializer
) {
    @MainThread
    internal fun createImageWireframe(
        view: View,
        index: Int,
        x: Long,
        y: Long,
        width: Long,
        height: Long,
        drawable: Drawable? = null,
        shapeStyle: MobileSegment.ShapeStyle? = null,
        border: MobileSegment.ShapeBorder? = null,
        prefix: String = DRAWABLE_CHILD_NAME
    ): MobileSegment.Wireframe.ImageWireframe? {
        val id = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, prefix + index)

        @Suppress("ComplexCondition")
        if (drawable == null || id == null || drawable.intrinsicWidth < 0 || drawable.intrinsicHeight < 0) {
            return null
        }

        val displayMetrics = view.resources.displayMetrics
        val applicationContext = view.context.applicationContext
        val mimeType = imageCompression.getMimeType()

        val imageWireframe =
            MobileSegment.Wireframe.ImageWireframe(
                id = id,
                x = x,
                y = y,
                width = width,
                height = height,
                shapeStyle = shapeStyle,
                border = border,
                base64 = "",
                mimeType = mimeType,
                isEmpty = true
            )

        base64Serializer.handleBitmap(
            applicationContext = applicationContext,
            displayMetrics = displayMetrics,
            drawable = drawable,
            imageWireframe = imageWireframe
        )

        return imageWireframe
    }

    private companion object {
        private const val DRAWABLE_CHILD_NAME = "drawable"
    }
}
