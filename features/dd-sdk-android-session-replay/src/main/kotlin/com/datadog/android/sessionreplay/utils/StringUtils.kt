/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

/**
 * String utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object StringUtils {

    /**
     * Removes the alpha value from the original color and adds the provided alpha at the end of
     * the it. It will then return the new color value as a HTML formatted hexa String (R,G,B,A)
     * @param color as the color value with or without alpha in the first 8 bits
     * @param alpha as Int. It can take values from 0 to 255.
     * @return new color value as a HTML formatted hexa String (R,G,B,A)
     */
    @Suppress("MagicNumber")
    fun formatColorAndAlphaAsHexa(color: Int, alpha: Int): String {
        // we shift left 8 bits to make room for alpha
        val colorAndAlpha = (color.toLong().shl(8)).or(alpha.toLong())
        // we are going to use the `Long.toString(radius)` method to produce the hexa
        // representation of the color and alpha long value because is much more faster than the
        // String.format(..) approach. Based on our benchmarks, because String.format uses regular
        // expressions under the hood, this approach is at least 2 times faster.

        // We remove the original alpha value from the color by masking with 0xffffffff
        val colorAndAlphaAsHexa = (0xffffffff and colorAndAlpha).toString(16)
        var requiredLength = 9

        @Suppress("UnsafeThirdPartyFunctionCall") // argument is not negative
        val sb = StringBuilder(requiredLength)
        sb.append("#")
        requiredLength--
        repeat(requiredLength - colorAndAlphaAsHexa.length) {
            sb.append('0')
        }
        sb.append(colorAndAlphaAsHexa)
        return sb.toString()
    }
}
