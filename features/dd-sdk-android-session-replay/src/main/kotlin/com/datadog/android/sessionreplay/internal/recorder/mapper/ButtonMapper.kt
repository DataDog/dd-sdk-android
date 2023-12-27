/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.Button
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class ButtonMapper(
    private val textWireframeMapper: TextViewMapper = TextViewMapper()
) :
    WireframeMapper<Button, MobileSegment.Wireframe> {
    override fun map(
        view: Button,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        return textWireframeMapper.map(view, mappingContext, asyncJobStatusCallback).map {
            if (it is MobileSegment.Wireframe.TextWireframe &&
                it.shapeStyle == null && it.border == null
            ) {
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
