/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger

internal class BitmapWrapper(
    private val logger: InternalLogger = InternalLogger.UNBOUND
) {
    internal fun createBitmap(
        displayMetrics: DisplayMetrics,
        bitmapWidth: Int,
        bitmapHeight: Int,
        config: Config
    ): Bitmap? {
        return try {
            Bitmap.createBitmap(displayMetrics, bitmapWidth, bitmapHeight, config)
        } catch (e: IllegalArgumentException) {
            // should never happen since config is given as valid type and width/height are
            // normalized to be at least 1
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_BITMAP },
                e
            )
            null
        }
    }

    internal fun createScaledBitmap(
        src: Bitmap,
        dstWidth: Int,
        dstHeight: Int,
        filter: Boolean
    ): Bitmap? {
        return try {
            Bitmap.createScaledBitmap(src, dstWidth, dstHeight, filter)
        } catch (e: IllegalArgumentException) {
            // should never happen since config is given as valid type and width/height are
            // normalized to be at least 1
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_SCALED_BITMAP },
                e
            )
            null
        }
    }

    private companion object {
        private const val FAILED_TO_CREATE_BITMAP = "Failed to create bitmap"
        private const val FAILED_TO_CREATE_SCALED_BITMAP = "Failed to create scaled bitmap"
    }
}
