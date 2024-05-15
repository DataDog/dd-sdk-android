/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper

/**
 * A wrapper holding a a [WireframeMapper] and the type it's associated with.
 *
 * @param T the type of [View] the mapper can handle
 * @param type the [Class] representing the view type
 * @param mapper the [WireframeMapper]
 */
data class MapperTypeWrapper<T : View>(
    internal val type: Class<T>,
    internal val mapper: WireframeMapper<T>
) {

    /**
     * Checks whether the underlying mapper would support mapping the given view.
     * @param view the view to map
     * @return true if the mapper can take the view as an input
     */
    fun supportsView(view: View): Boolean {
        @Suppress("UnsafeThirdPartyFunctionCall") // Can't have an NPE here
        return type.isAssignableFrom(view::class.java)
    }

    /**
     * Returns the mapper unsafely casted to [WireframeMapper<View>].
     */
    @Suppress("UNCHECKED_CAST")
    fun getUnsafeMapper(): WireframeMapper<View> {
        return mapper as WireframeMapper<View>
    }
}
