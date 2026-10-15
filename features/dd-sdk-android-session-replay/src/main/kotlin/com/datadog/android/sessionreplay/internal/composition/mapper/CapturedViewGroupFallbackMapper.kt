/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.composition.CapturedBounds
import com.datadog.android.sessionreplay.internal.composition.CapturedWireframe
import com.datadog.android.sessionreplay.internal.composition.toCaptured
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Generic fallback for any [View]/[ViewGroup] with no dedicated mapper: a single background-color
 * [CapturedWireframe.Shape] if the background resolves to a solid color, else nothing. Also what an
 * unmapped `ComposeView`/`AndroidComposeView` falls through to, since no Compose-aware decomposition
 * exists in this module yet. Direct port of legacy `ViewWireframeMapper`.
 */
internal class CapturedViewGroupFallbackMapper(
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    private val backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver =
        CapturedBackgroundShapeStyleResolver(),
    private val internalLogger: InternalLogger
) : CapturedViewMapper<View> {

    override fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult =
        capture(view, mappingContext).map(mappingContext)

    override fun capture(view: View, mappingContext: CapturedMappingContext): CapturedViewData {
        val background = backgroundShapeStyleResolver.capture(view, internalLogger)
            ?: return CapturedViewData.Mapped(CapturedViewMapperResult.None)
        return CapturedBackgroundViewData(
            (
                mappingContext.bounds
                    ?: viewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity).toCaptured()
                ),
            background
        )
    }
}

private data class CapturedBackgroundViewData(
    val bounds: CapturedBounds,
    val background: CapturedBackgroundColor
) : CapturedViewData {
    override val isOcclusionBounded: Boolean get() = true
    override val hasOpaqueBackground: Boolean get() = background.isOpaque

    override fun map(mappingContext: CapturedMappingContext): CapturedViewMapperResult =
        CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.Shape(
                    identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                    bounds = bounds,
                    style = background.resolve()
                )
            )
        )
}
