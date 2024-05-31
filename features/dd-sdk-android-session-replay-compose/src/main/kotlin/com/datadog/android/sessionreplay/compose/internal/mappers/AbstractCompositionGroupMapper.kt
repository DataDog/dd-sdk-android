/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeContext
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.parameters
import com.datadog.android.sessionreplay.compose.internal.stableId
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import kotlin.math.roundToInt

internal abstract class AbstractCompositionGroupMapper(
    protected val colorStringFormatter: ColorStringFormatter
) : CompositionGroupMapper {

    final override fun map(
        compositionGroup: CompositionGroup,
        composeContext: ComposeContext,
        uiContext: UiContext
    ): ComposeWireframe? {
        val box = Box.from(compositionGroup) ?: return null

        val stableGroupId = compositionGroup.stableId()
        val parameters = compositionGroup.parameters(composeContext)
        val boxWithDensity = box.withDensity(uiContext.density)

        return map(
            stableGroupId,
            parameters,
            boxWithDensity,
            uiContext
        )
    }

    protected abstract fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe?

    /**
     * Converts a Compose [Color] value to a web String (e.g. #FF003E)
     */
    protected fun convertColor(color: Long): String? {
        return if (color == UNSPECIFIED_COLOR) {
            null
        } else {
            val c = Color(color shr COMPOSE_COLOR_SHIFT)
            colorStringFormatter.formatColorAndAlphaAsHexString(
                c.toArgb(),
                (c.alpha * MAX_ALPHA).roundToInt()
            )
        }
    }

    companion object {
        /** As defined in Compose's ColorSpaces. */
        private const val UNSPECIFIED_COLOR = 16L
        private const val COMPOSE_COLOR_SHIFT = 32
        private const val MAX_ALPHA = 255
    }
}
