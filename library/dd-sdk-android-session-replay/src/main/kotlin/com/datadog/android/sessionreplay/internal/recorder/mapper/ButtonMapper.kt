/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.Button
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class ButtonMapper(
    private val textWireframeMapper: TextViewMapper = TextViewMapper()
) :
    WireframeMapper<Button, MobileSegment.Wireframe.TextWireframe> {
    override fun map(view: Button, mappingContext: MappingContext):
        List<MobileSegment.Wireframe.TextWireframe> {
        return textWireframeMapper.map(view, mappingContext).map {
            if (it.shapeStyle == null && it.border == null) {
                // we were not able to resolve the background for this button so just add a border
                it.copy(border = MobileSegment.ShapeBorder(BLACK_COLOR, 1))
            } else {
                it
            }
        }
    }

    companion object {
        internal const val BLACK_COLOR = "#000000ff"
    }
}
