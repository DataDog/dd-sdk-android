/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Build
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi

/**
 * Wraps the Bitmap class to catch potential crashes.
 */
@InternalApi
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

    // Creates a bitmap of its own on the buffer of a hardware bitmap, without copying pixels, so it stays readable
    // after the source bitmap is recycled. The buffer object is not closed: the source bitmap caches it, and it is the
    // application's own object when the application created the bitmap with Bitmap.wrapHardwareBuffer.
    @RequiresApi(Build.VERSION_CODES.S)
    internal fun wrapHardwareBuffer(bitmap: Bitmap): Bitmap? {
        return try {
            Bitmap.wrapHardwareBuffer(bitmap.hardwareBuffer, bitmap.colorSpace)
        } catch (e: IllegalStateException) {
            // The source bitmap was recycled.
            logWrapFailure(e)
            null
        } catch (e: IllegalArgumentException) {
            logWrapFailure(e)
            null
        }
    }

    private fun logWrapFailure(error: RuntimeException) {
        logger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { FAILED_TO_WRAP_HARDWARE_BUFFER },
            throwable = error,
            onlyOnce = true
        )
    }

    private companion object {
        private const val FAILED_TO_CREATE_BITMAP = "Failed to create bitmap"
        private const val FAILED_TO_CREATE_SCALED_BITMAP = "Failed to create scaled bitmap"
        private const val FAILED_TO_WRAP_HARDWARE_BUFFER = "Failed to wrap the buffer of a hardware bitmap"
    }
}
