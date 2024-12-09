/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.annotation.UiThread
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.internal.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext

/**
 * A Helper to handle capturing images in Session replay wireframes.
 */
interface ImageWireframeHelper {

    /**
     * Asks the helper to create an image wireframe based on a given bitmap.
     * @param id the unique id for the wireframe.
     * @param globalBounds the global bounds of the bitmap.
     * @param bitmap the bitmap to capture.
     * @param density the density of the screen.
     * @param isContextualImage if the image is contextual.
     * @param imagePrivacy defines which images should be hidden.
     * @param asyncJobStatusCallback the callback for the async capture process.
     * @param clipping the bounds of the image that are actually visible.
     * @param shapeStyle provides a custom shape (e.g. rounded corners) to the image wireframe.
     * @param border provides a custom border to the image wireframe.
     */
    fun createImageWireframeByBitmap(
        id: Long,
        globalBounds: GlobalBounds,
        bitmap: Bitmap,
        density: Float,
        isContextualImage: Boolean,
        imagePrivacy: ImagePrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip? = null,
        shapeStyle: MobileSegment.ShapeStyle? = null,
        border: MobileSegment.ShapeBorder? = null
    ): MobileSegment.Wireframe?

    /**
     * Asks the helper to create an image wireframe, and process the provided drawable in the background.
     * @param view the view owning the drawable
     * @param imagePrivacy defines which images should be hidden
     * @param currentWireframeIndex the index of the wireframe in the list of wireframes for the view
     * @param x the x position of the image
     * @param y the y position of the image
     * @param width the width of the image
     * @param height the width of the image
     * @param usePIIPlaceholder whether to replace the image content with a placeholder when we suspect it contains PII
     * @param drawable the drawable to capture
     * @param drawableCopier the callback to copy the original drawable to a new one.
     * @param asyncJobStatusCallback the callback for the async capture process
     * @param clipping the bounds of the image that are actually visible
     * @param shapeStyle provides a custom shape (e.g. rounded corners) to the image wireframe
     * @param border provides a custom border to the image wireframe
     * @param prefix a prefix identifying the drawable in the parent view's context
     * @param customResourceIdCacheKey an optional key with which to cache or retrieve from the resource cache.
     * If this key is not provided then one will be generated from the drawable.
     */
    // TODO RUM-3666 limit the number of params to this function
    fun createImageWireframeByDrawable(
        view: View,
        imagePrivacy: ImagePrivacy,
        currentWireframeIndex: Int,
        x: Long,
        y: Long,
        width: Int,
        height: Int,
        usePIIPlaceholder: Boolean,
        drawable: Drawable,
        drawableCopier: DrawableCopier = DefaultDrawableCopier(),
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip? = null,
        shapeStyle: MobileSegment.ShapeStyle? = null,
        border: MobileSegment.ShapeBorder? = null,
        prefix: String? = DRAWABLE_CHILD_NAME,
        customResourceIdCacheKey: String?
    ): MobileSegment.Wireframe?

    /**
     * Creates the wireframes for the compound drawables in a [TextView].
     * @param textView the [TextView] to capture the compound drawables from.
     * @param mappingContext the [MappingContext] for the [TextView].
     * @param prevWireframeIndex the index of the previous wireframe in the list of wireframes for the [TextView].
     * @param customResourceIdCacheKey an optional key with which to cache or retrieve from the resource cache.
     * If this key is not provided then one will be generated from the drawable.
     * @param asyncJobStatusCallback the callback for the async capture process.
     */
    @UiThread
    fun createCompoundDrawableWireframes(
        textView: TextView,
        mappingContext: MappingContext,
        prevWireframeIndex: Int,
        customResourceIdCacheKey: String?,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MutableList<MobileSegment.Wireframe>

    companion object {
        internal const val DRAWABLE_CHILD_NAME = "drawable"
    }
}
