/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import java.io.ByteArrayOutputStream

/**
 * Handle webp image compression.
 */
internal class WebPImageCompression(
    private val logger: InternalLogger
) : ImageCompression {

    @WorkerThread
    override fun compressBitmap(bitmap: Bitmap): ByteArray {
        // preallocate stream size
        val byteArrayOutputStream = ByteArrayOutputStream(bitmap.allocationByteCount)
        val imageFormat = getImageCompressionFormat()

        @Suppress("SwallowedException")
        try {
            // stream is not null and image quality is between 0 and 100
            @Suppress("UnsafeThirdPartyFunctionCall")
            bitmap.compress(imageFormat, IMAGE_QUALITY, byteArrayOutputStream)
        } catch (e: IllegalStateException) {
            // probably if the bitmap was recycled while we were working on it
            logger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { IMAGE_COMPRESSION_ERROR },
                e
            )

            return EMPTY_BYTEARRAY
        }

        return byteArrayOutputStream.toByteArray()
    }

    private fun getImageCompressionFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

    companion object {
        private val EMPTY_BYTEARRAY = ByteArray(0)

        // This is the default compression for webp when writing to the output stream -
        // a lower quality leads to a lower filesize and worse fidelity image
        private const val IMAGE_QUALITY = 75

        private const val IMAGE_COMPRESSION_ERROR = "Error while compressing the image."
    }
}
