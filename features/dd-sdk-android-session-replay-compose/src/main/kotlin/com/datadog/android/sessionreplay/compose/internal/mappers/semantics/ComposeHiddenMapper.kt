/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class ComposeHiddenMapper(
    colorStringFormatter: ColorStringFormatter,
    semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {
    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe? {
        val id = resolveId(semanticsNode)
        val viewGlobalBounds = resolveBounds(semanticsNode)
        return SemanticsWireframe(
            wireframes = MobileSegment.Wireframe.PlaceholderWireframe(
                id = id,
                x = viewGlobalBounds.x,
                y = viewGlobalBounds.y,
                width = viewGlobalBounds.width,
                height = viewGlobalBounds.height,
                label = HIDDEN_VIEW_PLACEHOLDER_TEXT
            ).let { listOf(it) },
            uiContext = parentContext
        )
    }

    internal companion object {
        internal const val HIDDEN_VIEW_PLACEHOLDER_TEXT = "Hidden"
    }
}
