/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Layout
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.composition.DefaultViewForegroundRasterizer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class BitmapFailureCleanupTest {
    @ParameterizedTest
    @CsvSource(
        "view,false,false", "view,true,false", "view,false,true",
        "foreground,false,false", "foreground,true,false", "foreground,false,true",
        "background,false,false", "background,true,false", "background,false,true",
        "compound,false,false", "compound,true,false", "compound,false,true",
        "text,false,false", "text,true,false", "text,false,true"
    )
    fun `M release unfinished pixels W drawing fails`(target: String, fatal: Boolean, allocation: Boolean) {
        // Given
        val view: TextView = mock()
        val drawable: Drawable = mock()
        val layout: Layout = mock()
        val bitmap: Bitmap = mock()
        val logger: InternalLogger = mock()
        val originalBounds = Rect(1, 2, 10, 20)
        whenever(view.width).thenReturn(100)
        whenever(view.height).thenReturn(40)
        whenever(view.background).thenReturn(drawable)
        whenever(view.layout).thenReturn(layout)
        whenever(layout.text).thenReturn("visible text")
        whenever(drawable.copyBounds()).thenReturn(originalBounds)
        val failure = if (fatal) OutOfMemoryError("simulated") else IllegalStateException("simulated")
        when (target) {
            "view" -> doThrow(failure).whenever(view).draw(any())
            "foreground" -> doThrow(failure).whenever(view).onDrawForeground(any())
            "text" -> doThrow(failure).whenever(layout).draw(any())
            else -> doThrow(failure).whenever(drawable).draw(any())
        }
        val rasterize = {
            when (target) {
                "view" -> DefaultViewRasterizer(logger).rasterize(view)
                "foreground" -> DefaultViewForegroundRasterizer(logger).rasterize(view)
                "background" -> DefaultViewBackgroundRasterizer(logger).rasterize(view)
                "compound" -> DefaultCompoundDrawableRasterizer(logger).rasterize(drawable, 100, 40)
                else -> DefaultTextViewTextRasterizer().rasterize(view)
            }
        }
        mockStatic(Bitmap::class.java).use { factory ->
            val createBitmap = factory.`when`<Bitmap> { Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888) }
            if (allocation) createBitmap.thenThrow(failure) else createBitmap.thenReturn(bitmap)
            mockConstruction(Canvas::class.java).use {
                // When + Then: only app drawing boundaries contain an ordinary callback failure.
                if (fatal || allocation || target == "text") {
                    assertThatThrownBy { rasterize() }.isSameAs(failure)
                } else {
                    assertThat(rasterize()).isNull()
                }
                if (!allocation) verify(bitmap).recycle()
                if (!allocation && (target == "background" || target == "compound")) {
                    verify(drawable).setBounds(originalBounds)
                }
            }
        }
    }
}
