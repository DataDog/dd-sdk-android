/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Generic fallback for any [View]/[ViewGroup] with no dedicated mapper: a single background-color
 * [CapturedWireframe.Shape] if the background resolves to a solid color, else nothing. Also what a
 * `ComposeView`/`AndroidComposeView` falls through to when no `CompositionHostDecomposer` is
 * configured, or it declines/fails to decompose that host. Direct port of legacy `ViewWireframeMapper`.
 */
internal class CapturedViewGroupFallbackMapper(
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver =
        CapturedBackgroundShapeStyleResolver(),
    private val internalLogger: InternalLogger
) : CapturedViewMapper<View> {

    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult {
        val style = backgroundShapeStyleResolver.resolve(view, internalLogger)
            ?: return CapturedViewMapperResult.None
        val bounds = viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        val identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity)
        return CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.Shape(
                    identity = identity,
                    bounds = bounds.toCaptured(),
                    style = style
                )
            )
        )
    }
}
