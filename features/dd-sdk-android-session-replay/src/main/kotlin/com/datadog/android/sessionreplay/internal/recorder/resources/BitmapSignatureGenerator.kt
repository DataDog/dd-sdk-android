/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import com.datadog.android.internal.system.BuildSdkVersionProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.security.MessageDigest
import java.util.WeakHashMap

/** Cache fingerprints include every pixel; sparse sampling can equate different glyphs or private regions. */
internal interface BitmapSignatureGenerator {
    /** Null for recycled, empty or hardware-backed bitmaps. */
    fun generateSignature(bitmap: Bitmap): Long?
}

internal class DefaultBitmapSignatureGenerator(
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) : BitmapSignatureGenerator {

    private data class BitmapState(
        val generation: Int,
        val width: Int,
        val height: Int,
        val config: Config?,
        val hasAlpha: Boolean,
        val premultiplied: Boolean
    )
    private data class CachedSignature(val state: BitmapState, val signature: Long)
    private val cacheLock = Any()
    private val signatures = WeakHashMap<Bitmap, CachedSignature>()
    private val workspaces = object : ThreadLocal<HashWorkspace>() {
        override fun initialValue(): HashWorkspace = HashWorkspace()
    }

    // Validity is checked before metadata access. Private weak keys never retain captured bitmaps.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun generateSignature(bitmap: Bitmap): Long? {
        if (!isValidBitmap(bitmap)) return null
        val state = stateOf(bitmap)
        synchronized(cacheLock) {
            signatures[bitmap]?.takeIf { it.state == state }?.let { return it.signature }
        }
        val signature = computeHash(bitmap)
        // Bitmap reconfiguration or drawing can happen between reads. Never memoize an unstable copy.
        if (!isValidBitmap(bitmap) || stateOf(bitmap) != state) return null
        synchronized(cacheLock) {
            if (signatures.size >= MAX_CACHED_BITMAPS) {
                val oldest = signatures.keys.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
            signatures[bitmap] = CachedSignature(state, signature)
        }
        return signature
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called only after checking the bitmap is valid.
    private fun stateOf(bitmap: Bitmap) = BitmapState(
        bitmap.generationId,
        bitmap.width,
        bitmap.height,
        bitmap.config,
        bitmap.hasAlpha(),
        bitmap.isPremultiplied
    )

    private fun isValidBitmap(bitmap: Bitmap): Boolean {
        val isHardwareBitmap = buildSdkVersionProvider.isAtLeastO && bitmap.config == Config.HARDWARE
        return !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0 && !isHardwareBitmap
    }

    // SHA-256 is present on every supported Android version. Pixel tiles and buffers are bounded,
    // and valid software bitmap coordinates are checked before this method is called.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun computeHash(bitmap: Bitmap): Long {
        val width = bitmap.width
        val height = bitmap.height
        val workspace = workspaces.get() ?: HashWorkspace().also { workspaces.set(it) }
        val digest = workspace.digest
        digest.reset()
        workspace.dimensions.clear()
        workspace.dimensions.putInt(width).putInt(height)
        digest.update(workspace.dimensions.array())
        val tileWidth = minOf(width, MAX_TILE_PIXELS)
        val tileHeight = minOf(height, MAX_TILE_PIXELS / tileWidth)
        val pixels = workspace.pixels
        val bytes = workspace.bytes
        val integers = workspace.integers
        for (x in 0 until width step tileWidth) {
            val columns = minOf(tileWidth, width - x)
            for (y in 0 until height step tileHeight) {
                val rows = minOf(tileHeight, height - y)
                val count = columns * rows
                bitmap.getPixels(pixels, 0, columns, x, y, columns, rows)
                integers.clear()
                integers.put(pixels, 0, count)
                digest.update(bytes.array(), 0, count * BYTES_PER_PIXEL)
            }
        }
        // Existing bounded caches use Long keys. Derive their 64 bits from the full-content digest,
        // rather than a few hundred sampled pixels that routinely collide for near-identical text.
        return ByteBuffer.wrap(digest.digest()).long
    }

    // Per-thread storage avoids a new 128 KiB of temporary arrays for every glyph or image.
    @Suppress("UnsafeThirdPartyFunctionCall") // SHA-256 and these positive bounded heap buffers are always available.
    private class HashWorkspace {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        val dimensions: ByteBuffer = ByteBuffer.allocate(DIMENSIONS_BYTES)
        val pixels = IntArray(MAX_TILE_PIXELS)
        val bytes: ByteBuffer = ByteBuffer.allocate(MAX_TILE_PIXELS * BYTES_PER_PIXEL).order(ByteOrder.LITTLE_ENDIAN)
        val integers: IntBuffer = bytes.asIntBuffer()
    }

    private companion object {
        const val MAX_CACHED_BITMAPS = 256
        const val MAX_TILE_PIXELS = 16_384
        const val BYTES_PER_PIXEL = 4
        const val DIMENSIONS_BYTES = 8
    }
}
