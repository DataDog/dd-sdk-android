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
import com.datadog.android.lint.InternalApi
import com.datadog.android.sessionreplay.internal.generated.DdSdkAndroidSessionReplayLogger

/**
 * Wraps the Bitmap class to catch potential crashes.
 */
@InternalApi
class BitmapWrapper(
    private val logger: InternalLogger = InternalLogger.UNBOUND
) {
    private val srLogger = DdSdkAndroidSessionReplayLogger(logger)
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
            srLogger.logFailedToCreateBitmap(e)
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
            srLogger.logFailedToCreateScaledBitmapMaintainer(e)
            null
        } catch (e: RuntimeException) {
            srLogger.logFailedToCreateScaledBitmapUser(e)
            null
        }
    }

    private companion object {
        private const val FAILED_TO_CREATE_BITMAP = "Failed to create bitmap"
        private const val FAILED_TO_CREATE_SCALED_BITMAP = "Failed to create scaled bitmap"
    }
}
