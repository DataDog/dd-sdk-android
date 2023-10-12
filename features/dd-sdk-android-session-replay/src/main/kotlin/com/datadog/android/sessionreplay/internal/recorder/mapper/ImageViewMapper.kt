/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.ImageView
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelperCallback
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

internal class ImageViewMapper(
    private val base64Serializer: Base64Serializer,
    private val imageWireframeHelper: ImageWireframeHelper,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator
) : BaseAsyncBackgroundWireframeMapper<ImageView>(
    imageWireframeHelper = imageWireframeHelper,
    uniqueIdentifierGenerator = uniqueIdentifierGenerator
) {
    override fun map(
        view: ImageView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        // add background wireframes if any
        wireframes.addAll(super.map(view, mappingContext, asyncJobStatusCallback))

        val drawable = view.drawable?.current ?: return wireframes
        val resources = view.resources
        val density = resources.displayMetrics.density
        val bounds = resolveViewGlobalBounds(view, density)

        // This method should not be part of the serializer
        // TODO: RUM-0000 remove this method from the serializer and remove
        // the serializer dependency from this class
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
            callback = object : ImageWireframeHelperCallback {
                override fun onFinished() {
                    asyncJobStatusCallback.jobFinished()
                }

                override fun onStart() {
                    asyncJobStatusCallback.jobStarted()
                }
            }
        )?.let {
            wireframes.add(it)
        }

        return wireframes
    }
}
