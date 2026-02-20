/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.internal.generated.DdSdkAndroidSessionReplayLogger
import java.io.ByteArrayOutputStream

/**
 * Handle webp image compression.
 */
internal class WebPImageCompression(
    private val logger: InternalLogger,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : ImageCompression {
    private val srLogger = DdSdkAndroidSessionReplayLogger(logger)

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
            srLogger.logImageCompressionError(e)

            return EMPTY_BYTEARRAY
        }

        return byteArrayOutputStream.toByteArray()
    }

    private fun getImageCompressionFormat(): Bitmap.CompressFormat =
        if (buildSdkVersionProvider.isAtLeastR) {
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
