/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.DisplayMetrics

internal class BitmapWrapper {
    internal fun createBitmap(
        displayMetrics: DisplayMetrics,
        bitmapWidth: Int,
        bitmapHeight: Int,
        config: Config
    ): Bitmap? {
        @Suppress("SwallowedException")
        return try {
            Bitmap.createBitmap(displayMetrics, bitmapWidth, bitmapHeight, config)
        } catch (e: IllegalArgumentException) {
            // should never happen since config is given as valid type and width/height are
            // normalized to be at least 1
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            null
        }
    }
}
