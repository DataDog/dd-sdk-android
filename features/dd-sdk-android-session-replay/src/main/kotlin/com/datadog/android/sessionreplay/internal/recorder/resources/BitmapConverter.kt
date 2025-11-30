/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger

internal interface BitmapConverter {
    @WorkerThread
    fun convertAlpha8BitmapToArgb8888(bitmap: Bitmap): Bitmap?
}

/**
 * Converts Alpha8 bitmaps to ARGB_8888 format for compression and display.
 *
 * Alpha8 bitmaps contain only alpha channel data (no color). To create a visible image,
 * we render white content on a black background, producing a grayscale representation
 * where the alpha values become luminance values.
 */
internal class Alpha8BitmapConverter(
    private val logger: InternalLogger
) : BitmapConverter {

    @WorkerThread
    @Suppress("ReturnCount")
    override fun convertAlpha8BitmapToArgb8888(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        val argbBitmap = try {
            // Safe: width/height validated > 0 above, config is non-null constant. OOM caught below.
            @Suppress("UnsafeThirdPartyFunctionCall")
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { BITMAP_CREATION_OOM },
                e
            )
            return null
        }

        return try {
            drawAlpha8ToArgb(argbBitmap, bitmap)
        } catch (e: IllegalStateException) {
            argbBitmap.recycle()
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { BITMAP_DRAWING_FAILED },
                e
            )
            null
        }
    }

    private fun drawAlpha8ToArgb(destination: Bitmap, source: Bitmap): Bitmap {
        // Safe: destination was just created by createBitmap, guaranteed mutable and not recycled
        @Suppress("UnsafeThirdPartyFunctionCall")
        val canvas = Canvas(destination)
        canvas.drawColor(Color.BLACK)

        val paint = Paint()
        paint.color = Color.WHITE

        // Safe: source validated at method entry. If recycled by another thread,
        // IllegalStateException is caught by caller.
        @Suppress("UnsafeThirdPartyFunctionCall")
        canvas.drawBitmap(source, 0f, 0f, paint)

        return destination
    }

    private companion object {
        private const val BITMAP_CREATION_OOM = "OutOfMemoryError creating ARGB_8888 bitmap for alpha8 conversion"
        private const val BITMAP_DRAWING_FAILED = "Failed to draw alpha8 bitmap to ARGB_8888"
    }
}
