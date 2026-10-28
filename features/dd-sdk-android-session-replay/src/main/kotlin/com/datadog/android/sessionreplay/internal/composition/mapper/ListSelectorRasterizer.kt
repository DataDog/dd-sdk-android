/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.widget.ListView
import androidx.annotation.UiThread

internal data class RasterizedListSelector(val bitmap: Bitmap, val bounds: Rect)

internal fun interface ListSelectorRasterizer {
    @UiThread
    fun rasterize(view: ListView): RasterizedListSelector?
}

/** Draws only selection chrome; no row or other child content is read or drawn. */
internal class DefaultListSelectorRasterizer : ListSelectorRasterizer {
    @UiThread
    // Owned bitmap and a selector already laid out by ListView.
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall")
    override fun rasterize(view: ListView): RasterizedListSelector? {
        if (!view.isPressed && (!view.hasFocus() || view.isInTouchMode)) return null
        val selector = view.selector ?: return null
        val bounds = Rect(selector.bounds)
        if (bounds.isEmpty || bounds.width().toLong() * bounds.height() > MAX_SELECTOR_PIXELS) return null
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        var completed = false
        return try {
            val canvas = Canvas(bitmap)
            canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            selector.draw(canvas)
            completed = true
            RasterizedListSelector(bitmap, bounds)
        } finally {
            if (!completed) bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_SELECTOR_PIXELS = 4_000_000L
    }
}
