/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger

/**
 * Wraps the Bitmap class to catch potential crashes.
 */
class BitmapWrapper(
    private val logger: InternalLogger = InternalLogger.UNBOUND
) {
    /**
     * Creates a bitmap with the given parameters.
     * @param bitmapWidth the width of the bitmap.
     * @param bitmapHeight the height of the bitmap.
     * @param config the config of the bitmap.
     * @param displayMetrics the optional display metrics to use.
     * @return the created bitmap or null if it failed.
     */
    fun createBitmap(
        bitmapWidth: Int,
        bitmapHeight: Int,
        config: Config,
        displayMetrics: DisplayMetrics? = null
    ): Bitmap? {
        return try {
            if (displayMetrics != null) {
                Bitmap.createBitmap(displayMetrics, bitmapWidth, bitmapHeight, config)
            } else {
                Bitmap.createBitmap(bitmapWidth, bitmapHeight, config)
            }
        } catch (e: IllegalArgumentException) {
            // should never happen since config is given as valid type and width/height are
            // normalized to be at least 1
            // TODO RUM-806 Add logs here once the sdkLogger is added
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_BITMAP },
                e
            )
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
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
            // TODO RUM-806 Add logs here once the sdkLogger is added
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_SCALED_BITMAP },
                e
            )
            null
        } catch (e: RuntimeException) {
            // It's still possible that RuntimeException is thrown after checking the bitmap
            // is not recycled.
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
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
