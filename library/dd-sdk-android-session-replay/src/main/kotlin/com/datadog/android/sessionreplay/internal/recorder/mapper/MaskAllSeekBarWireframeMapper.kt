/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@RequiresApi(Build.VERSION_CODES.O)
internal class MaskAllSeekBarWireframeMapper(
    viewUtils: ViewUtils = ViewUtils,
    stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator =
        UniqueIdentifierGenerator
) : SeekBarWireframeMapper(viewUtils, stringUtils, uniqueIdentifierGenerator) {

    override fun resolveViewAsWireframesList(
        nonActiveTrackWireframe: MobileSegment.Wireframe.ShapeWireframe,
        activeTrackWireframe: MobileSegment.Wireframe.ShapeWireframe,
        thumbWireframe: MobileSegment.Wireframe.ShapeWireframe
    ): List<MobileSegment.Wireframe> {
        return listOf(nonActiveTrackWireframe)
    }
}
