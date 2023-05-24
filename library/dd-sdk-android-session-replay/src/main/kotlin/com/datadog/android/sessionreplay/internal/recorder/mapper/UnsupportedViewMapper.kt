/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.model.MobileSegment

internal class UnsupportedViewMapper :
    BaseWireframeMapper<View, MobileSegment.Wireframe.TextWireframe>() {

    override fun map(view: View, mappingContext: MappingContext):
        List<MobileSegment.Wireframe.TextWireframe> {
        val viewGlobalBounds = resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )

        return listOf(
            MobileSegment.Wireframe.TextWireframe(
                id = resolveViewId(view),
                x = viewGlobalBounds.x,
                y = viewGlobalBounds.y,
                width = viewGlobalBounds.width,
                height = viewGlobalBounds.height,
                shapeStyle = resolveShapeStyle(view),
                border = resolveBorder(),
                text = resolveViewTitle(view),
                textStyle = resolveTextStyle(),
                textPosition = resolveTextPosition()
            )
        )
    }

    // region Internal

    private fun resolveViewTitle(view: View): String {
        val viewUtilsInternal = ViewUtilsInternal()
        return if (viewUtilsInternal.isToolbar(view)) {
            return TOOLBAR_LABEL
        } else {
            DEFAULT_LABEL
        }
    }

    private fun resolveTextStyle():
        MobileSegment.TextStyle {
        return MobileSegment.TextStyle(
            family = SANS_SERIF_FAMILY_NAME,
            size = LABEL_TEXT_SIZE,
            color = TEXT_COLOR
        )
    }

    private fun resolveTextPosition():
        MobileSegment.TextPosition {
        return MobileSegment.TextPosition(
            alignment = MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.CENTER,
                vertical = MobileSegment.Vertical.CENTER
            )
        )
    }

    private fun resolveBorder():
        MobileSegment.ShapeBorder {
        return MobileSegment.ShapeBorder(
            color = BORDER_COLOR,
            width = BORDER_WIDTH
        )
    }

    private fun resolveShapeStyle(view: View):
        MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = BACKGROUND_COLOR,
            opacity = view.alpha,
            cornerRadius = CORNER_RADIUS
        )
    }

    // endregion

    companion object {
        internal const val TEXT_COLOR = "#FF0000FF"
        internal const val BACKGROUND_COLOR = "#F1F1F3FF"
        internal const val CORNER_RADIUS = 4
        internal const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
        internal const val BORDER_COLOR = "#D3D3D3FF"
        internal const val BORDER_WIDTH = 1L
        internal const val LABEL_TEXT_SIZE = 10L
        internal const val TOOLBAR_LABEL = "Toolbar"
        internal const val DEFAULT_LABEL = "Unsupported view"
    }
}
