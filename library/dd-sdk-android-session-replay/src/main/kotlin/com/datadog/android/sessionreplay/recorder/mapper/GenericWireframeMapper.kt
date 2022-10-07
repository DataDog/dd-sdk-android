/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.datadog.android.sessionreplay.model.MobileSegment

internal abstract class GenericWireframeMapper(
    private val viewWireframeMapper: ViewWireframeMapper,
    internal val imageMapper: ViewScreenshotWireframeMapper,
    private val textMapper: TextWireframeMapper,
    private val buttonMapper: ButtonWireframeMapper
) : WireframeMapper<View, MobileSegment.Wireframe> {

    override fun map(view: View, pixelsDensity: Float): MobileSegment.Wireframe {
        return when {
            Button::class.java.isAssignableFrom(view::class.java) -> {
                buttonMapper.map(view as Button, pixelsDensity)
            }
            TextView::class.java.isAssignableFrom(view::class.java) -> {
                textMapper.map(view as TextView, pixelsDensity)
            }
            ImageView::class.java.isAssignableFrom(view::class.java) -> {
                imageMapper.map(view as ImageView, pixelsDensity)
            }
            else -> {
                viewWireframeMapper.map(view, pixelsDensity)
            }
        }
    }
}
