/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.WeakHashMap

/** Copies only immutable tint values, without retaining a native filter or its owner. */
internal data class VectorPainterTint(val color: Long, val blendMode: Int)

/** A clean renderer's state, read synchronously before deciding whether its output can be reused. */
internal data class VectorPainterState(
    val autoMirror: Boolean,
    val viewport: Size,
    val previousDrawSize: Long,
    val intrinsicTint: VectorPainterTint?,
    val cachedTint: VectorPainterTint?
)

internal data class VectorPainterPixels(val bitmap: Bitmap, val isShared: Boolean)

/** UI-thread-only, bounded bookkeeping. Both painter keys and recorder-owned pixel values are weak. */
internal class ComposeVectorPainterBitmapCache(
    private val stateOf: (VectorPainter) -> VectorPainterState?,
    private val sourceOf: (VectorPainter) -> Bitmap?,
    private val tintOf: (ColorFilter) -> VectorPainterTint?,
    maxEntries: Int = MAX_ENTRIES
) {
    constructor(reflectionUtils: ReflectionUtils) : this(
        reflectionUtils::getVectorPainterState,
        reflectionUtils::getBitmapInVectorPainter,
        reflectionUtils::getVectorColorFilterKey
    )

    private data class Key(
        val renderer: VectorPainterState,
        val alpha: Float,
        val tint: VectorPainterTint?,
        val density: Float,
        val fontScale: Float,
        val direction: LayoutDirection
    )

    private val entries = WeakHashMap<VectorPainter, ComposeImageBitmapCache<Key>>()
    private val maxEntries = maxEntries.coerceAtLeast(1)

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Finite primitive checks; owned fallback.
    fun capture(
        painter: VectorPainter,
        source: Bitmap,
        alpha: Float,
        colorFilter: ColorFilter?,
        density: Density,
        direction: LayoutDirection,
        render: () -> Bitmap?
    ): VectorPainterPixels? {
        if (!alpha.isFinite() || !isValidDensity(density)) {
            return renderOwned(painter, render)
        }
        val tint = colorFilter?.let(tintOf)
        if (colorFilter != null && tint == null) return renderOwned(painter, render)
        // Dirty, unreadable and unsupported renderer state preserves the existing owned-output path.
        val renderer = stateOf(painter) ?: return renderOwned(painter, render)
        val key = Key(renderer, alpha, tint, density.density, density.fontScale, direction)
        val cache = cacheFor(painter)
        val pixels = cache.getOrCreate(
            source,
            key,
            shouldCache = { stateOf(painter) == renderer && sourceOf(painter) === source },
            create = render
        ) ?: return null
        // The caller must not recycle shared pixels while another generation may still consume them.
        return VectorPainterPixels(pixels, isShared = true)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // UI-thread-only owned weak map.
    private fun renderOwned(painter: VectorPainter, render: () -> Bitmap?): VectorPainterPixels? {
        entries.remove(painter)
        return render()?.let { VectorPainterPixels(it, isShared = false) }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Primitive floating-point validation cannot call app code.
    private fun isValidDensity(density: Density): Boolean {
        val validDensity = density.density.isFinite() && density.density > 0f
        val validFontScale = density.fontScale.isFinite() && density.fontScale > 0f
        return validDensity && validFontScale
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Owned map; iterator is checked and used on one UI thread.
    private fun cacheFor(painter: VectorPainter): ComposeImageBitmapCache<Key> {
        entries[painter]?.let { return it }
        if (entries.size >= maxEntries) {
            val iterator = entries.keys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        // Keep at most one source/output variant per painter; alternate paint or sizes can miss safely.
        return ComposeImageBitmapCache<Key>(maxEntries = 1).also { entries[painter] = it }
    }

    private companion object {
        const val MAX_ENTRIES = 64
    }
}
