/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils

internal abstract class BaseWireframeMapper<T : View, S : MobileSegment.Wireframe>(
    private val stringUtils: StringUtils = StringUtils
) :
    WireframeMapper<T, S> {

    protected fun resolveViewId(view: View): Long {
        // we will use the System.identityHashcode in here which always returns the default
        // hashcode value whether or not a child class overrides this.
        return System.identityHashCode(view).toLong()
    }

    protected fun colorAndAlphaAsStringHexa(color: Int, alphaAsHexa: Int): String {
        return stringUtils.formatColorAndAlphaAsHexa(color, alphaAsHexa)
    }

    companion object {
        internal const val OPAQUE_ALPHA_VALUE: Int = 255
    }
}
