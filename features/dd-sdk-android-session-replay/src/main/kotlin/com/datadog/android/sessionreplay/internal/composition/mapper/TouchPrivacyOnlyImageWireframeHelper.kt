/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper

/** Custom touch rules may require map(), but omitted visual content must never enqueue resources. */
internal object TouchPrivacyOnlyImageWireframeHelper : ImageWireframeHelper {
    override fun createImageWireframeByPath(
        id: Long,
        globalBounds: GlobalBounds,
        path: Path,
        strokeColor: Int,
        strokeWidth: Int,
        targetWidth: Int,
        targetHeight: Int,
        density: Float,
        isContextualImage: Boolean,
        imagePrivacy: ImagePrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip?,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?,
        customResourceIdCacheKey: String?
    ): MobileSegment.Wireframe? = null

    override fun createImageWireframeByBitmap(
        id: Long,
        globalBounds: GlobalBounds,
        bitmap: Bitmap,
        density: Float,
        isContextualImage: Boolean,
        imagePrivacy: ImagePrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip?,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?
    ): MobileSegment.Wireframe? = null

    override fun createImageWireframeByDrawable(
        view: View,
        imagePrivacy: ImagePrivacy,
        currentWireframeIndex: Int,
        x: Long,
        y: Long,
        width: Int,
        height: Int,
        usePIIPlaceholder: Boolean,
        drawable: Drawable,
        drawableCopier: DrawableCopier,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip?,
        shapeStyle: MobileSegment.ShapeStyle?,
        border: MobileSegment.ShapeBorder?,
        prefix: String?,
        customResourceIdCacheKey: String?
    ): MobileSegment.Wireframe? = null

    override fun createCompoundDrawableWireframes(
        textView: TextView,
        mappingContext: MappingContext,
        prevWireframeIndex: Int,
        customResourceIdCacheKey: String?,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MutableList<MobileSegment.Wireframe> = mutableListOf()
}
