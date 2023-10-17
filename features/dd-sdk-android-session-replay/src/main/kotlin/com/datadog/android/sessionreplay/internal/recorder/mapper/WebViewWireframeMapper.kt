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

internal class WebViewWireframeMapper :
    BaseWireframeMapper<View, MobileSegment.Wireframe.WebviewWireframe>() {

    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe.WebviewWireframe> {
        val viewGlobalBounds = resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        return listOf(
            MobileSegment.Wireframe.WebviewWireframe(
                resolveViewId(view),
                viewGlobalBounds.x,
                viewGlobalBounds.y,
                viewGlobalBounds.width,
                viewGlobalBounds.height,
                nestedEnvId = resolveViewId(view)
            )
        )
    }
}
