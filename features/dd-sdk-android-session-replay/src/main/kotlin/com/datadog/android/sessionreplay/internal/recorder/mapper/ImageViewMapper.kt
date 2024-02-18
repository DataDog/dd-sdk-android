/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.ImageView
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageWireframeHelperCallback
import com.datadog.android.sessionreplay.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

internal class ImageViewMapper(
    private val imageWireframeHelper: ImageWireframeHelper,
    private val imageViewUtils: ImageViewUtils = ImageViewUtils,
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

        val parentRect = imageViewUtils.resolveParentRectAbsPosition(view)
        val contentRect = imageViewUtils.resolveContentRectWithScaling(view, drawable)

        val resources = view.resources
        val density = resources.displayMetrics.density
        val clipping = imageViewUtils.calculateClipping(parentRect, contentRect, density)
        val contentXPosInDp = contentRect.left.densityNormalized(density).toLong()
        val contentYPosInDp = contentRect.top.densityNormalized(density).toLong()
        val contentWidthPx = contentRect.width()
        val contentHeightPx = contentRect.height()
        val contentDrawable = drawable.constantState?.newDrawable(resources)

        // resolve foreground
        @Suppress("ThreadSafety") // TODO REPLAY-1861 caller thread of .map is unknown?
        imageWireframeHelper.createImageWireframe(
            view = view,
            currentWireframeIndex = wireframes.size,
            x = contentXPosInDp,
            y = contentYPosInDp,
            width = contentWidthPx,
            height = contentHeightPx,
            drawable = contentDrawable,
            usePIIPlaceholder = true,
            shapeStyle = null,
            border = null,
            clipping = clipping,
            prefix = ImageWireframeHelper.DRAWABLE_CHILD_NAME,
            imageWireframeHelperCallback = object : ImageWireframeHelperCallback {
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
