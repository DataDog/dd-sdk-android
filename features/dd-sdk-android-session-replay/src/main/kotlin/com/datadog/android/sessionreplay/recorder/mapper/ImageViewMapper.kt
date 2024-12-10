/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.widget.ImageView
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.ImageViewUtils
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.internal.utils.toWireframeClip
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A [WireframeMapper] implementation to map an [ImageView] component.
 */
open class ImageViewMapper : BaseAsyncBackgroundWireframeMapper<ImageView> {
    private val imageViewUtils: ImageViewUtils
    private val drawableCopier: DrawableCopier

    @Suppress("Unused") // used by external mappers
    constructor(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper,
        drawableCopier: DrawableCopier
    ) : this(
        viewIdentifierResolver,
        colorStringFormatter,
        viewBoundsResolver,
        drawableToColorMapper,
        ImageViewUtils,
        drawableCopier
    )

    internal constructor(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper,
        imageViewUtils: ImageViewUtils,
        drawableCopier: DrawableCopier
    ) : super(
        viewIdentifierResolver,
        colorStringFormatter,
        viewBoundsResolver,
        drawableToColorMapper
    ) {
        this.imageViewUtils = imageViewUtils
        this.drawableCopier = drawableCopier
    }

    @UiThread
    override fun map(
        view: ImageView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        // add background wireframes if any
        wireframes.addAll(super.map(view, mappingContext, asyncJobStatusCallback, internalLogger))

        val drawable = view.drawable?.current ?: return wireframes

        val parentRect = imageViewUtils.resolveParentRectAbsPosition(view)
        val contentRect = imageViewUtils.resolveContentRectWithScaling(view, drawable)

        val resources = view.resources
        val density = resources.displayMetrics.density

        val clipping = if (view.cropToPadding) {
            imageViewUtils.calculateClipping(parentRect, contentRect, density).toWireframeClip()
        } else {
            null
        }

        val contentXPosInDp = contentRect.left.densityNormalized(density).toLong()
        val contentYPosInDp = contentRect.top.densityNormalized(density).toLong()
        val contentWidthPx = contentRect.width()
        val contentHeightPx = contentRect.height()

        // resolve foreground
        mappingContext.imageWireframeHelper.createImageWireframeByDrawable(
            view = view,
            imagePrivacy = mappingContext.imagePrivacy,
            currentWireframeIndex = wireframes.size,
            x = contentXPosInDp,
            y = contentYPosInDp,
            width = contentWidthPx,
            height = contentHeightPx,
            usePIIPlaceholder = true,
            drawable = drawable,
            drawableCopier = drawableCopier,
            asyncJobStatusCallback = asyncJobStatusCallback,
            clipping = clipping,
            shapeStyle = null,
            border = null,
            prefix = ImageWireframeHelper.DRAWABLE_CHILD_NAME,
            customResourceIdCacheKey = null
        )?.let {
            wireframes.add(it)
        }

        return wireframes
    }
}
