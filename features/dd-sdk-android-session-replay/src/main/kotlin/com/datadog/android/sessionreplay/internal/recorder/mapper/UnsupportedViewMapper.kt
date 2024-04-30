/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class UnsupportedViewMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<View>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    override fun map(
        view: View,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val pixelsDensity = mappingContext.systemInformation.screenDensity
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)

        return listOf(
            MobileSegment.Wireframe.PlaceholderWireframe(
                id = resolveViewId(view),
                x = viewGlobalBounds.x,
                y = viewGlobalBounds.y,
                width = viewGlobalBounds.width,
                height = viewGlobalBounds.height,
                label = resolveViewTitle(view)
            )
        )
    }

    // region Internal

    private fun resolveViewTitle(view: View): String {
        val viewUtilsInternal = ViewUtilsInternal()
        return if (viewUtilsInternal.isToolbar(view)) {
            return TOOLBAR_LABEL
        } else {
            DEFAULT_LABEL
        }
    }

    // endregion

    companion object {
        internal const val TOOLBAR_LABEL = "Toolbar"
        internal const val DEFAULT_LABEL = "Unsupported view"
    }
}
