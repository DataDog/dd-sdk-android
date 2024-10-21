/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import kotlin.math.roundToInt

internal abstract class AbstractSemanticsNodeMapper(
    private val colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : SemanticsNodeMapper {

    protected fun resolveBounds(semanticsNode: SemanticsNode): GlobalBounds {
        return semanticsUtils.resolveInnerBounds(semanticsNode)
    }

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
