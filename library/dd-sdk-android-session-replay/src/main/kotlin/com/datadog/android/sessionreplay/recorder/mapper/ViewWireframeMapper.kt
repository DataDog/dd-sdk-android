/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.densityNormalized

internal class ViewWireframeMapper :
    BaseWireframeMapper<View, MobileSegment.Wireframe.ShapeWireframe>() {

    override fun map(view: View, pixelsDensity: Float): MobileSegment.Wireframe.ShapeWireframe {
        val scaledHeight = view.height.densityNormalized(pixelsDensity).toLong()
        val scaledWidth = view.width.densityNormalized(pixelsDensity).toLong()
        val coordinates = IntArray(2)
        // this will always have size >= 2
        @Suppress("UnsafeThirdPartyFunctionCall")
        view.getLocationInWindow(coordinates)
        val x = coordinates[0].densityNormalized(pixelsDensity).toLong()
        val y = coordinates[1].densityNormalized(pixelsDensity).toLong()
        val styleBorderPair = view.background?.resolveShapeStyleAndBorder()
        return MobileSegment.Wireframe.ShapeWireframe(
            resolveViewId(view),
            x,
            y,
            scaledWidth,
            scaledHeight,
            shapeStyle = styleBorderPair?.first,
            border = styleBorderPair?.second
        )
    }

    private fun Drawable.resolveShapeStyleAndBorder():
        Pair<MobileSegment.ShapeStyle?, MobileSegment.ShapeBorder?>? {
        return if (this is ColorDrawable) {
            val color = colorAndAlphaAsStringHexa(color, alpha.toLong())
            MobileSegment.ShapeStyle(color, alpha) to null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            this is RippleDrawable &&
            numberOfLayers >= 1
        ) {
            getDrawable(0).resolveShapeStyleAndBorder()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this is InsetDrawable) {
            drawable?.resolveShapeStyleAndBorder()
        } else {
            // We cannot handle this drawable so we will use a border to delimit its container
            // bounds.
            // TODO: RUMM-0000 In case the background drawable could not be handled we should
            // instead resolve it as an ImageWireframe.
            null to MobileSegment.ShapeBorder("#000000FF", 1)
        }
    }
}
