/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

@RequiresApi(Build.VERSION_CODES.O)
internal class MaskSeekBarWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : SeekBarWireframeMapper(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    override fun resolveViewAsWireframesList(
        nonActiveTrackWireframe: MobileSegment.Wireframe.ShapeWireframe,
        activeTrackWireframe: MobileSegment.Wireframe.ShapeWireframe,
        thumbWireframe: MobileSegment.Wireframe.ShapeWireframe
    ): List<MobileSegment.Wireframe> {
        return listOf(nonActiveTrackWireframe)
    }
}
