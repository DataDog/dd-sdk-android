/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Locale

internal abstract class BaseWireframeMapper<T : View, S : MobileSegment.Wireframe> :
    WireframeMapper<T, S> {

    protected fun resolveViewId(view: View): Long {
        return if (view.id != View.NO_ID) view.id.toLong() else view.hashCode().toLong()
    }

    protected fun colorAndAlphaAsStringHexa(color: Int, alphaAsHexa: Long): String {
        // we shift left 8 bits to make room for alpha
        val colorAndAlphaAsHexa = (color.toLong().shl(8)).or(alphaAsHexa)
        return String.format(Locale.US, "#%08X", 0xFFFFFFFF and colorAndAlphaAsHexa)
    }

    companion object {
        internal const val OPAQUE_AS_HEXA: Long = 255
    }
}
