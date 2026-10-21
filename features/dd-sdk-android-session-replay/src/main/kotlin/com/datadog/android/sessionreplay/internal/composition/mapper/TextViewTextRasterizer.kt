/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.TextView
import com.datadog.android.api.InternalLogger

internal fun interface TextViewTextRasterizer {
    fun rasterize(view: TextView): Bitmap?
}

/** Draw the text layout alone; compound drawables and the View hierarchy are never included. */
internal class DefaultTextViewTextRasterizer(
    private val internalLogger: InternalLogger
) : TextViewTextRasterizer {
    @Suppress("ReturnCount", "TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    override fun rasterize(view: TextView): Bitmap? {
        val layout = view.layout ?: return null
        val text = layout.text
        // An ImageSpan or another replacement can draw arbitrary image pixels inside the layout.
        if (text is Spanned && text.getSpans(0, text.length, ReplacementSpan::class.java).isNotEmpty()) return null
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_TEXT_PIXELS) return null
        var bitmap: Bitmap? = null
        return try {
            val pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap = pixels
            val canvas = Canvas(pixels)
            canvas.translate(
                (view.totalPaddingLeft - view.scrollX).toFloat(),
                (view.totalPaddingTop - view.scrollY).toFloat()
            )
            layout.draw(canvas)
            pixels
        } catch (e: Exception) {
            bitmap?.recycle()
            logFailure(e)
            null
        } catch (e: OutOfMemoryError) {
            bitmap?.recycle()
            logFailure(e)
            null
        }
    }

    private fun logFailure(error: Throwable) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Unable to rasterize the text layout." },
            error,
            onlyOnce = true
        )
    }

    private companion object {
        const val MAX_TEXT_PIXELS = 4_000_000L
    }
}
