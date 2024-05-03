/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.DatadogActionBarContainerAccessor
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class ActionBarContainerMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseViewGroupMapper<ActionBarContainer>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    override fun map(
        view: ActionBarContainer,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        // The ActionBarContainer uses an internal Drawable implementation that redirects to some fields in the
        // ActionBarContainer class. It uses an ActionBarContainer.mBackground field (not to be confused with the
        // View.mBackground field which has a getBackground() accessor.
        // Fortunately, the ActionBarContainer.mBackground field we're interested in is package private,
        // which allows us to access it via the DatadogActionBarContainerAccessor.
        val background = DatadogActionBarContainerAccessor(view).getBackgroundDrawable()
        val shapeStyle = background?.let { resolveShapeStyle(it, view.alpha) }
        val id = viewIdentifierResolver.resolveChildUniqueIdentifier(view, PREFIX_BACKGROUND_DRAWABLE)

        if ((shapeStyle != null) && (id != null)) {
            val density = mappingContext.systemInformation.screenDensity
            val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, density)

            return listOf(
                MobileSegment.Wireframe.ShapeWireframe(
                    id,
                    x = bounds.x,
                    y = bounds.y,
                    width = bounds.width,
                    height = bounds.height,
                    shapeStyle = shapeStyle,
                    border = null
                )
            )
        } else {
            return emptyList()
        }
    }
}
