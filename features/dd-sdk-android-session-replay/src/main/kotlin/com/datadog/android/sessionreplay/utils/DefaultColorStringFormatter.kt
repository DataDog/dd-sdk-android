/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import com.datadog.android.core.internal.utils.HEX_RADIX

/**
 * String utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object DefaultColorStringFormatter : ColorStringFormatter {

    override fun formatColorAsHexString(color: Int): String {
        // shift Android's ARGB to Web RGBA
        val alpha = (color.toLong() and MASK_ALPHA) shr ALPHA_SHIFT_ANDROID
        val colorRGBA = (color.toLong() shl ALPHA_SHIFT_WEB) or alpha
        val hexString = (MASK_COLOR and colorRGBA).toString(HEX_RADIX)
        return "#${hexString.padStart(WEB_COLOR_STR_LENGTH, '0')}"
    }

    override fun formatColorAndAlphaAsHexString(color: Int, alpha: Int): String {
        val colorRGBA = (color.toLong() shl ALPHA_SHIFT_WEB) or alpha.toLong()

        // we are going to use the `Long.toString(radius)` method to produce the hexa
        // representation of the color and alpha long value because is much more faster than the
        // String.format(..) approach. Based on our benchmarks, because String.format uses regular
        // expressions under the hood, this approach is at least 2 times faster.

        // We remove the original alpha value from the color by masking with 0xffffffff
        val hexString = (MASK_COLOR and colorRGBA).toString(HEX_RADIX)
        return "#${hexString.padStart(WEB_COLOR_STR_LENGTH, '0')}"
    }
}
