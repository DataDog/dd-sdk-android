/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Checkable
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal abstract class CheckableWireframeMapper<T>(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<T, MobileSegment.Wireframe>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) where T : View, T : Checkable {

    override fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val mainWireframes = resolveMainWireframes(view, mappingContext, asyncJobStatusCallback)
        val checkableWireframes = if (view.isChecked) {
            resolveCheckedCheckable(view, mappingContext)
        } else {
            resolveNotCheckedCheckable(view, mappingContext)
        }
        checkableWireframes?.let { wireframes ->
            return mainWireframes + wireframes
        }
        return mainWireframes
    }

    abstract fun resolveMainWireframes(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe>

    abstract fun resolveNotCheckedCheckable(
        view: T,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>?

    abstract fun resolveCheckedCheckable(
        view: T,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>?
}
