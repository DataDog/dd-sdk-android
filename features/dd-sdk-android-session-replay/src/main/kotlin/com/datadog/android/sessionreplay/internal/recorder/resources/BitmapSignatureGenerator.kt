/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Build

/**
 * Generates a lightweight signature (hash) for a bitmap that can be used as a cache key.
 *
 * The signature is designed to be fast to compute while still being reasonably unique
 * for different bitmap contents. It samples pixels at regular intervals rather than
 * reading every pixel, making it efficient for large bitmaps.
 */
internal interface BitmapSignatureGenerator {
    /**
     * Generates a signature for the given bitmap.
     * @return A Long hash value, or null if the bitmap is invalid (recycled, empty, or HARDWARE config)
     */
    fun generateSignature(bitmap: Bitmap): Long?
}

internal class DefaultBitmapSignatureGenerator : BitmapSignatureGenerator {

    override fun generateSignature(bitmap: Bitmap): Long? {
        return if (isValidBitmap(bitmap)) {
            computeHash(bitmap)
        } else {
            null
        }
    }

    private fun isValidBitmap(bitmap: Bitmap): Boolean {
        // HARDWARE bitmaps don't support getPixel() - they exist only in GPU memory
        val isHardwareBitmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Config.HARDWARE
        return !bitmap.isRecycled &&
            bitmap.width > 0 &&
            bitmap.height > 0 &&
            !isHardwareBitmap
    }

    /**
     * Computes a hash by sampling pixels in an evenly-spaced grid across the bitmap.
     *
     * Instead of reading every pixel (which would be slow for large bitmaps), we sample
     * up to [SAMPLES_PER_AXIS] x [SAMPLES_PER_AXIS] pixels spread evenly across the image.
     * This gives us a representative fingerprint while keeping computation fast.
     *
     * Uses a polynomial rolling hash: hash = hash * 31 + value
     * This is the same approach used by Java's String.hashCode() and provides good distribution.
     */
    @Suppress("UnsafeThirdPartyFunctionCall") // bitmap has been checked for validity
    private fun computeHash(bitmap: Bitmap): Long {
        val width = bitmap.width
        val height = bitmap.height

        // Start with prime number to reduce collisions for small inputs
        var hash: Long = HASH_PRIME_SEED

        // Include dimensions in hash - same pixels at different sizes should have different signatures
        hash = HASH_MULTIPLIER * hash + width
        hash = HASH_MULTIPLIER * hash + height

        // Calculate stride to sample evenly across the bitmap
        // For a 100px wide bitmap with 16 samples, stride = 6, sampling at x = 0, 6, 12, 18, ...
        val strideX = (width / SAMPLES_PER_AXIS).coerceAtLeast(1)
        val strideY = (height / SAMPLES_PER_AXIS).coerceAtLeast(1)

        for (x in 0 until width step strideX) {
            for (y in 0 until height step strideY) {
                val pixelColor = bitmap.getPixel(x, y)
                hash = HASH_MULTIPLIER * hash + pixelColor
            }
        }

        return hash
    }

    private companion object {
        // Prime seed for polynomial hash - reduces collisions for small inputs
        private const val HASH_PRIME_SEED = 17L

        // Multiplier for polynomial rolling hash (same as Java's String.hashCode)
        private const val HASH_MULTIPLIER = 31L

        // Number of pixel samples to take along each axis (16x16 = 256 samples max)
        // This balances uniqueness vs performance - more samples = more unique but slower
        private const val SAMPLES_PER_AXIS = 16
    }
}
