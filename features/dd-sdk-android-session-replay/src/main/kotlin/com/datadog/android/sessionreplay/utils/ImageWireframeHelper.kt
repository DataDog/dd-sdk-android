/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * A Helper to handle capturing images in Session replay wireframes.
 */
interface ImageWireframeHelper {

    /**
     * Asks the helper to create an image wireframe, and process the provided drawable in the background.
     * @param view the view owning the drawable
     * @param currentWireframeIndex the index of the wireframe in the list of wireframes for the view
     * @param x the x position of the image
     * @param y the y position of the image
     * @param width the width of the image
     * @param height the width of the image
     * @param usePIIPlaceholder whether to replace the image content with a placeholder when we suspect it contains PII
     * @param drawable the drawable to capture
     * @param asyncJobStatusCallback the callback for the async capture process
     * @param clipping the bounds of the image that are actually visible
     * @param shapeStyle provides a custom shape (e.g. rounded corners) to the image wireframe
     * @param border provides a custom border to the image wireframe
     * @param prefix a prefix identifying the drawable in the parent view's context
     */
    // TODO RUM-3666 limit the number of params to this function
    fun createImageWireframe(
        view: View,
        currentWireframeIndex: Int,
        x: Long,
        y: Long,
        width: Int,
        height: Int,
        usePIIPlaceholder: Boolean,
        drawable: Drawable,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        clipping: MobileSegment.WireframeClip? = null,
        shapeStyle: MobileSegment.ShapeStyle? = null,
        border: MobileSegment.ShapeBorder? = null,
        prefix: String? = DRAWABLE_CHILD_NAME
    ): MobileSegment.Wireframe?

    /**
     * Creates the wireframes for the compound drawables in a [TextView].
     * @param
     */
    fun createCompoundDrawableWireframes(
        textView: TextView,
        mappingContext: MappingContext,
        prevWireframeIndex: Int,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): MutableList<MobileSegment.Wireframe>

    companion object {
        internal const val DRAWABLE_CHILD_NAME = "drawable"
    }
}
