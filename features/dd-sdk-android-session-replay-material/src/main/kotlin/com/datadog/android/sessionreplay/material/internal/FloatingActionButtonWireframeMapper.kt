/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material.internal

import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.ImageViewMapper
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.roundToLong

/**
 * Maps the background of a [FloatingActionButton] to a shape wireframe. Its background drawable
 * can't be captured as an image: the fill color depends on the view state, and the border layer is
 * shared with the live view.
 */
internal class FloatingActionButtonWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : ImageViewMapper(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper,
    DefaultDrawableCopier()
) {

    @UiThread
    override fun resolveViewBackground(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): MobileSegment.Wireframe? {
        val fab = view as? FloatingActionButton
        val tint = fab?.backgroundTintList
        val circle = Rect()
        fab?.getMeasuredContentRect(circle)
        val circleWidth = circle.right - circle.left
        val circleHeight = circle.bottom - circle.top
        val isCircleEmpty = circleWidth <= 0 || circleHeight <= 0
        if (fab == null || tint == null || isCircleEmpty) {
            return super.resolveViewBackground(view, mappingContext, asyncJobStatusCallback, internalLogger)
        }

        val density = mappingContext.systemInformation.screenDensity
        val viewBounds = viewBoundsResolver.resolveViewGlobalBounds(fab, density)
        val bounds = GlobalBounds(
            x = viewBounds.x + circle.left.toLong().densityNormalized(density),
            y = viewBounds.y + circle.top.toLong().densityNormalized(density),
            width = circleWidth.toLong().densityNormalized(density),
            height = circleHeight.toLong().densityNormalized(density)
        )
        val cornerSize = fab.shapeAppearanceModel.topLeftCornerSize.getCornerSize(RectF(circle))
        val shapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = colorStringFormatter.formatColorAsHexString(
                tint.getColorForState(fab.drawableState, tint.defaultColor)
            ),
            opacity = fab.alpha,
            cornerRadius = (cornerSize / density).roundToLong()
        )

        return resolveBackgroundAsShapeWireframe(fab, bounds, circleWidth, circleHeight, shapeStyle)
    }
}
