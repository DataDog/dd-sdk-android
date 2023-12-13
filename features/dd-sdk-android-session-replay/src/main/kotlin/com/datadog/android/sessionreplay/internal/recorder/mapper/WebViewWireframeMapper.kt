/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.webkit.WebView
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment

internal class WebViewWireframeMapper :
    BaseWireframeMapper<WebView, MobileSegment.Wireframe.WebviewWireframe>() {

    override fun map(
        view: WebView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe.WebviewWireframe> {
        // Uncomment if we want to tell the browser-sdk that we are ready to accept full snapshots
        // view.loadUrl("javascript:window.DD_RUM.takeSessionReplayFullSnapshot();")
        val viewGlobalBounds = resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val webViewId = resolveViewId(view)

        return listOf(
            MobileSegment.Wireframe.WebviewWireframe(
                webViewId,
                viewGlobalBounds.x,
                viewGlobalBounds.y,
                viewGlobalBounds.width,
                viewGlobalBounds.height,
                slotId = webViewId.toString()
            )
        )
    }
}
