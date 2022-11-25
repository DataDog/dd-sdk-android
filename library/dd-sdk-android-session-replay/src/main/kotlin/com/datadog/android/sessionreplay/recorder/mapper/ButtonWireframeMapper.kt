/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.widget.Button
import com.datadog.android.sessionreplay.model.MobileSegment

internal class ButtonWireframeMapper(
    private val textWireframeMapper: TextWireframeMapper = TextWireframeMapper()
) :
    WireframeMapper<Button, MobileSegment.Wireframe.TextWireframe> {
    override fun map(view: Button, pixelsDensity: Float): MobileSegment.Wireframe.TextWireframe {
        return textWireframeMapper.map(view, pixelsDensity)
    }
}
