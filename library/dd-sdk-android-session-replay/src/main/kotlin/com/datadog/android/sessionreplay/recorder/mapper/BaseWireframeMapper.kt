/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment

internal abstract class BaseWireframeMapper<T : View, S : MobileSegment.Wireframe> :
    WireframeMapper<T, S> {

    protected fun resolveViewId(view: View): Long {
        // we will use the System.identityHashcode in here which always returns the default
        // hashcode value whether or not a child class overrides this.
        return System.identityHashCode(view).toLong()
    }

    protected fun colorAndAlphaAsStringHexa(color: Int, alphaAsHexa: Long): String {
        // we shift left 8 bits to make room for alpha
        val colorAndAlpha = (color.toLong().shl(BITS_PER_COLOR_CHANNEL)).or(alphaAsHexa)
        // we are going to use the `Long.toString(radius)` method to produce the hexa
        // representation of the color and alpha long value because is much more faster than the
        // String.format(..) approach. Based on our benchmarks, because String.format uses regular
        // expressions under the hood, this approach is at least 2 times faster.
        val colorAndAlphaAsHexa = (RGBA_COLOR_MASK and colorAndAlpha).toString(HEX_RADIX)
        val sb = StringBuilder(RGBA_STRING_LENGTH)
        sb.append("#")
        repeat(RGBA_CODE_LENGTH - colorAndAlphaAsHexa.length) {
            sb.append('0')
        }
        sb.append(colorAndAlphaAsHexa)
        return sb.toString()
    }

    companion object {
        private const val HEX_RADIX = 16
        private const val RGBA_CODE_LENGTH = 8
        private const val RGBA_STRING_LENGTH = RGBA_CODE_LENGTH + 1
        private const val RGBA_COLOR_MASK = 0xffffffff

        private const val BITS_PER_COLOR_CHANNEL = 8
        internal const val COLOR_CHANNEL_MAX_VALUE: Long = 255
    }
}
