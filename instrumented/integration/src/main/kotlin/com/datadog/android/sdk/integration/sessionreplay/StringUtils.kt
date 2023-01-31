/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

internal object StringUtils {

    @Suppress("MagicNumber")
    internal fun formatColorAndAlphaAsHexa(color: Int, alphaAsHexa: Int): String {
        // we shift left 8 bits to make room for alpha
        val colorAndAlpha = (color.toLong().shl(8)).or(alphaAsHexa.toLong())
        // we are going to use the `Long.toString(radius)` method to produce the hexa
        // representation of the color and alpha long value because is much more faster than the
        // String.format(..) approach. Based on our benchmarks, because String.format uses regular
        // expressions under the hood, this approach is at least 2 times faster.
        val colorAndAlphaAsHexa = (0xffffffff and colorAndAlpha).toString(16)
        var requiredLength = 9
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
