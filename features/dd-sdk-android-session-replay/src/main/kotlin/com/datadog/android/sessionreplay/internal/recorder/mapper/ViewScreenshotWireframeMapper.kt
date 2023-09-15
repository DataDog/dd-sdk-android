/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

// TODO: RUMM-0000 This should take a screenshot of the current view and return it as
// and ImageWireframe. It will be handled in the Session Replay v1.
internal class ViewScreenshotWireframeMapper(
    private val viewWireframeMapper: ViewWireframeMapper = ViewWireframeMapper()
) : BaseWireframeMapper<View, MobileSegment.Wireframe.ShapeWireframe>() {

    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ):
        List<MobileSegment.Wireframe.ShapeWireframe> {
        return viewWireframeMapper.map(view, mappingContext).map {
            it.copy(border = MobileSegment.ShapeBorder("#000000ff", 1))
        }
    }
}
