/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.graphics.drawable.Drawable
import android.os.Build
import com.datadog.android.api.InternalLogger

/**
 * A utility interface to convert a [Drawable] to a meaningful color.
 * This interface is meant for internal usage, please use it carefully.
 */
fun interface DrawableToColorMapper {

    /**
     * Maps the drawable to its meaningful color, or null if the drawable is mostly invisible.
     * @param drawable the drawable to convert
     * @param internalLogger the internalLogger to report warnings
     * @return the color as an Int (in 0xAARRGGBB order), or null if the drawable is mostly invisible
     */
    fun mapDrawableToColor(drawable: Drawable, internalLogger: InternalLogger): Int?

    companion object {
        /**
         * Provides a default implementation.
         * @return a default implementation based on the device API level
         */
        fun getDefault(): DrawableToColorMapper {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AndroidQDrawableToColorMapper()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AndroidMDrawableToColorMapper()
            } else {
                LegacyDrawableToColorMapper()
            }
        }
    }
}
