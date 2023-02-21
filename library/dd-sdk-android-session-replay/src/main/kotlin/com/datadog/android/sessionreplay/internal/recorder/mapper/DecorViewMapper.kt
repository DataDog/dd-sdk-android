/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment

internal class DecorViewMapper(
    private val viewWireframeMapper: ViewWireframeMapper,
    private val uniqueIdentifierGenerator: UniqueIdentifierResolver = UniqueIdentifierResolver
) : BaseWireframeMapper<View, MobileSegment.Wireframe.ShapeWireframe>() {

    override fun map(view: View, systemInformation: SystemInformation):
        List<MobileSegment.Wireframe.ShapeWireframe> {
        val wireframes = viewWireframeMapper.map(view, systemInformation).toMutableList()
        if (systemInformation.themeColor != null) {
            // we add the background color from the theme to the decorView
            addShapeStyleFromThemeIfNeeded(
                systemInformation.themeColor,
                wireframes,
                view
            )
        }
        // we will add the window wireframe here which comes in handy whenever we have to record a
        // pop - up. Usually the pop - up gets displayed in a smaller decorView in a upper Window.
        // the window behind is blurred so we need this extra Wireframe here to achieve that
        // effect in the player.
        val windowWireframeId = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            view,
            WINDOW_KEY_NAME
        )
        if (windowWireframeId != null) {
            val windowWireframe = MobileSegment.Wireframe.ShapeWireframe(
                id = windowWireframeId,
                x = 0,
                y = 0,
                width = systemInformation.screenBounds.width,
                height = systemInformation.screenBounds.height,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = WINDOW_WIREFRAME_COLOR,
                    opacity = WINDOW_WIREFRAME_OPACITY
                )
            )
            wireframes.add(0, windowWireframe)
        }
        return wireframes
    }

    private fun addShapeStyleFromThemeIfNeeded(
        themeColor: String,
        wireframes: MutableList<MobileSegment.Wireframe.ShapeWireframe>,
        view: View
    ) {
        // we add a shapeStyle based on the Theme color in case the
        // root wireframe does not have a ShapeStyle
        if (wireframes.firstOrNull { it.shapeStyle != null } == null) {
            val shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = themeColor,
                opacity = view.alpha
            )
            for (i in 0 until wireframes.size) {
                wireframes[i] = wireframes[i].copy(shapeStyle = shapeStyle)
            }
        }
    }

    companion object {
        internal const val WINDOW_WIREFRAME_COLOR = "#000000FF"
        internal const val WINDOW_WIREFRAME_OPACITY = 0.6f
        internal const val WINDOW_KEY_NAME = "window"
    }
}
