/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.annotation.UiThread
import androidx.compose.ui.platform.AndroidComposeView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.utils.resolveComposeWindowOffset
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class AndroidComposeViewMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    private val rootSemanticsNodeMapper: RootSemanticsNodeMapper
) : BaseWireframeMapper<AndroidComposeView>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    @UiThread
    override fun map(
        view: AndroidComposeView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val rootSemanticsNode = view.semanticsOwner.unmergedRootSemanticsNode
        // Use the node's own density so it matches SemanticsUtils.resolveInnerBounds.
        val systemDensity =
            mappingContext.systemInformation.screenDensity.let { if (it == 0.0f) 1.0f else it }
        val density = rootSemanticsNode.layoutInfo.density.density.let { if (it == 0.0f) systemDensity else it }
        // positionInRoot is relative to the view, not the screen — offset to match other SR mappers.
        val windowOffset = view.resolveComposeWindowOffset(density)
        return rootSemanticsNodeMapper.createComposeWireframes(
            rootSemanticsNode,
            density,
            mappingContext,
            asyncJobStatusCallback,
            internalLogger,
            windowOffset = windowOffset
        )
    }
}
