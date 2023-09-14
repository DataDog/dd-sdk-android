/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.ImageButton
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

internal class ImageButtonMapper(
    private val base64Serializer: Base64Serializer,
    private val imageWireframeHelper: ImageWireframeHelper,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator
) : BaseWireframeMapper<ImageButton, MobileSegment.Wireframe>(
    base64Serializer = base64Serializer,
    imageWireframeHelper = imageWireframeHelper,
    uniqueIdentifierGenerator = uniqueIdentifierGenerator
) {
    override fun map(
        view: ImageButton,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        // add background wireframes if any
        wireframes.addAll(super.map(view, mappingContext))

        val drawable = view.drawable?.current ?: return wireframes
        val resources = view.resources
        val density = resources.displayMetrics.density
        val bounds = resolveViewGlobalBounds(view, density)

        val (scaledDrawableWidth, scaledDrawableHeight) =
            base64Serializer.getDrawableScaledDimensions(view, drawable, density)

        val centerX = (bounds.x + view.width.densityNormalized(density) / 2) - (scaledDrawableWidth / 2)
        val centerY = (bounds.y + view.height.densityNormalized(density) / 2) - (scaledDrawableHeight / 2)

        // resolve foreground
        @Suppress("ThreadSafety") // TODO REPLAY-1861 caller thread of .map is unknown?
        imageWireframeHelper.createImageWireframe(
            view = view,
            currentWireframeIndex = wireframes.size,
            x = centerX,
            y = centerY,
            width = scaledDrawableWidth,
            height = scaledDrawableHeight,
            drawable = drawable.constantState?.newDrawable(resources),
            shapeStyle = null,
            border = null,
            asyncImageProcessingCallback = asyncImageProcessingCallback
        )?.let {
            wireframes.add(it)
        }

        return wireframes
    }
}
