/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils

/**
 * Estimates the bitmap bytes an image capture reserves in [PendingImageRequests], from the bitmap formats that decide
 * whether the capture copies, reads back or converts a bitmap.
 */
internal class ImageReservationEstimator(
    private val buildSdkVersionProvider: BuildSdkVersionProvider,
    private val maxPendingBytes: Long
) {

    fun isHardwareBitmap(bitmap: Bitmap): Boolean =
        buildSdkVersionProvider.isAtLeastO && bitmap.config == Bitmap.Config.HARDWARE

    fun isUnpremultiplied(bitmap: Bitmap): Boolean =
        bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.isPremultiplied && bitmap.hasAlpha()

    // A software Canvas cannot draw a hardware bitmap or one with unpremultiplied alpha, so a bitmap drawable's bitmap
    // of either kind is read back or copied at full size instead.
    fun isConvertedInFull(bitmap: Bitmap): Boolean = isHardwareBitmap(bitmap) || isUnpremultiplied(bitmap)

    fun isTooLargeToCopyInFull(bitmap: Bitmap): Boolean = isConvertedInFull(bitmap) && isTooLargeToConvert(bitmap)

    fun argbBytes(bitmap: Bitmap): Long = bitmap.width.toLong() * bitmap.height * ARGB_8888_PIXEL_SIZE_BYTES

    // A copy is scaled down when it would exceed the drawing limit or its long edge the WebP encoder's limit.
    fun isScaledDown(bitmap: Bitmap, byteCount: Long): Boolean =
        byteCount > MAX_COPY_SIZE_BYTES || maxOf(bitmap.width, bitmap.height) > DrawableUtils.MAX_WEBP_DIMENSION

    // A hardware bitmap is read back, an ALPHA_8 one converted and an unpremultiplied one copied at full size, so one
    // whose full-size ARGB_8888 copy, or a hardware bitmap's readback in its own format, alone exceeds the budget is
    // not captured rather than risk that allocation.
    fun isTooLargeToConvert(bitmap: Bitmap): Boolean {
        val isHardware = isHardwareBitmap(bitmap)
        val largestBytes =
            if (isHardware) maxOf(argbBytes(bitmap), bitmap.allocationByteCount.toLong()) else argbBytes(bitmap)
        return (bitmap.config == Bitmap.Config.ALPHA_8 || isHardware || isUnpremultiplied(bitmap)) &&
            largestBytes > maxPendingBytes
    }

    // A capture reserves the most pixels it holds at once. A large bitmap is scaled straight to the drawing limit, a
    // hardware one is read back in full and that copy scaled, a wrapped one also keeps the application's buffer alive
    // until the worker reads it back, an ALPHA_8 one is copied and then converted, and an unpremultiplied one is copied
    // at full size.
    fun estimateBitmapBytes(
        bitmap: Bitmap,
        byteCount: Long,
        isAlpha8: Boolean,
        isHardware: Boolean,
        isWrapped: Boolean
    ): Long {
        return when {
            isAlpha8 -> bitmap.width.toLong() * bitmap.height + byteCount
            isWrapped -> bitmap.allocationByteCount + readBackBytes(bitmap, byteCount)
            isHardware -> readBackBytes(bitmap, byteCount)
            isUnpremultiplied(bitmap) -> byteCount
            else -> minOf(byteCount, MAX_COPY_SIZE_BYTES)
        }
    }

    // The worker holds what a bitmap drawable's bitmap needs, or a raster of the drawable at its drawn size, which a
    // bitmap drawable falls back to when its bitmap cannot be used. A bitmap too large to copy in full is never captured
    // directly, though the drawable the copier returns for it can still be.
    fun estimateDrawableBytes(drawable: Drawable, drawableWidth: Int, drawableHeight: Int): Long {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        val rasterBytes =
            minOf(drawableWidth.toLong() * drawableHeight * ARGB_8888_PIXEL_SIZE_BYTES, MAX_COPY_SIZE_BYTES)
        val isCapturedDirectly = bitmap != null && !bitmap.isRecycled && !isTooLargeToCopyInFull(bitmap)
        val sourceBytes = if (isCapturedDirectly) drawableBitmapBytes(bitmap) else 0L
        return maxOf(sourceBytes, rasterBytes)
    }

    // A bitmap drawable's bitmap is copied scaled to the drawing limit, at most four bytes per pixel, read back in full
    // first when it is a hardware bitmap and kept at full size when it is unpremultiplied.
    fun drawableBitmapBytes(bitmap: Bitmap): Long = when {
        isHardwareBitmap(bitmap) -> readBackBytes(bitmap, argbBytes(bitmap))
        isUnpremultiplied(bitmap) -> argbBytes(bitmap)
        else -> minOf(argbBytes(bitmap), MAX_COPY_SIZE_BYTES)
    }

    // Copying a hardware bitmap first reads its buffer back into a temporary bitmap in the buffer's own format, the
    // size of its allocation, alongside the full-size ARGB_8888 copy. A copy that is scaled down is scaled once that
    // temporary is released.
    private fun readBackBytes(bitmap: Bitmap, byteCount: Long): Long {
        val scaledBytes = if (isScaledDown(bitmap, byteCount)) minOf(byteCount, MAX_COPY_SIZE_BYTES) else 0L
        return byteCount + maxOf(bitmap.allocationByteCount.toLong(), scaledBytes)
    }

    private companion object {
        private const val ARGB_8888_PIXEL_SIZE_BYTES = 4
        private const val MAX_COPY_SIZE_BYTES = DrawableUtils.MAX_BITMAP_SIZE_BYTES_WITH_RESOURCE_ENDPOINT.toLong()
    }
}
