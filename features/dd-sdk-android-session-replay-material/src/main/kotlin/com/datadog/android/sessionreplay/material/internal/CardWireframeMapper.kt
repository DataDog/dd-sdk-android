/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material.internal

import androidx.cardview.widget.CardView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseViewGroupMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.card.MaterialCardView

internal class CardWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseViewGroupMapper<CardView>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    override fun map(
        view: CardView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val shapeStyle = resolveShapeStyle(view, mappingContext)

        // Only MaterialCardView can have a built-in border.
        val shapeBorder = if (view is MaterialCardView) {
            resolveShapeBorder(view, mappingContext)
        } else {
            null
        }

        return listOf(
            MobileSegment.Wireframe.ShapeWireframe(
                resolveViewId(view),
                viewGlobalBounds.x,
                viewGlobalBounds.y,
                viewGlobalBounds.width,
                viewGlobalBounds.height,
                shapeStyle = shapeStyle,
                border = shapeBorder
            )
        )
    }

    private fun resolveShapeBorder(
        view: MaterialCardView,
        mappingContext: MappingContext
    ): MobileSegment.ShapeBorder {
        @Suppress("DEPRECATION")
        val strokeColor = view.strokeColorStateList?.defaultColor ?: view.strokeColor
        return MobileSegment.ShapeBorder(
            color = colorStringFormatter.formatColorAsHexString(strokeColor),
            width = view.strokeWidth.toLong().densityNormalized(mappingContext.systemInformation.screenDensity)
        )
    }

    private fun resolveShapeStyle(
        view: CardView,
        mappingContext: MappingContext
    ): MobileSegment.ShapeStyle {
        val backgroundColor = view.cardBackgroundColor.defaultColor
        return MobileSegment.ShapeStyle(
            backgroundColor = colorStringFormatter.formatColorAsHexString(backgroundColor),
            opacity = view.alpha,
            cornerRadius = view.radius.toLong().densityNormalized(mappingContext.systemInformation.screenDensity)
        )
    }
}
