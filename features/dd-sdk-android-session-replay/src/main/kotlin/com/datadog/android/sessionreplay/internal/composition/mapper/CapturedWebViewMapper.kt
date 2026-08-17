/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.webkit.WebView
import com.datadog.android.sessionreplay.internal.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * WebView content is never decomposed - a single opaque placeholder wireframe is emitted whose
 * `id == slotId` (guaranteed by [com.datadog.android.sessionreplay.internal.composition.CapturedIdentityFactory.webViewWireframe]),
 * which an out-of-band JS bridge uses to correlate this rect with independently-recorded web
 * content. Direct port of legacy `WebViewWireframeMapper`.
 */
internal class CapturedWebViewMapper(
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver,
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
) : CapturedViewMapper<WebView> {

    override fun map(view: WebView, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val slotId = viewIdentifierResolver.resolveViewId(view)
        val identity = mappingContext.identityFactory.webViewWireframe(mappingContext.ownerIdentity, slotId)
        return CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.WebView(
                    identity = identity,
                    bounds = bounds.toCaptured(),
                    isVisible = true
                )
            )
        )
    }
}
