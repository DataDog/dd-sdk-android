/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * A software [Canvas] that transparently converts any `Bitmap.Config.HARDWARE` bitmap to a
 * CPU-readable [Bitmap.Config.ARGB_8888] copy before drawing it, instead of a plain [Canvas]'s
 * behavior of throwing `IllegalArgumentException` ("Software rendering doesn't support hardware
 * bitmaps") — see [PixelCapture.captureViewRegion] for why that exception happens at all.
 *
 * [Bitmap.copy] performs exactly this GPU-to-CPU readback and is the standard, synchronous way
 * to make hardware bitmap pixels CPU-accessible — no `PixelCopy`, `Surface`, or minimum API level
 * beyond hardware bitmaps' own (26) is needed. Overriding every `drawBitmap` overload here works
 * because [View.draw] passes this exact [Canvas] instance down through the view hierarchy,
 * including through Compose's own Android-specific canvas wrapper (which holds and forwards to
 * the real platform [Canvas]) — so a hardware bitmap drawn by an ordinary `ImageView` or by
 * Compose's `Image`/`AsyncImage` is intercepted here the same way, with no dependency on Compose
 * or knowledge of what's inside the view being captured.
 *
 * Known gap: content Compose (or the View hierarchy) has promoted onto its own cached hardware
 * layer (`RenderNode`) is replayed via a single opaque `drawRenderNode` call rather than
 * individual `drawBitmap` calls through this [Canvas] — a hardware bitmap inside *that* pre-recorded
 * display list is not intercepted here, and still throws. That's a strict improvement over the
 * status quo, not a regression: every case this doesn't catch behaves exactly as it did before
 * (falls back to [PixelCapture.captureViewRegion]'s existing `IllegalArgumentException` handling).
 */
internal class HardwareBitmapConvertingCanvas(bitmap: Bitmap) : Canvas(bitmap) {

    override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        super.drawBitmap(convertIfHardware(bitmap), left, top, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
        super.drawBitmap(convertIfHardware(bitmap), src, dst, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        super.drawBitmap(convertIfHardware(bitmap), src, dst, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        super.drawBitmap(convertIfHardware(bitmap), matrix, paint)
    }

    private fun convertIfHardware(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            @Suppress("UnsafeThirdPartyFunctionCall") // copy() only throws on an already-recycled bitmap
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }
}
