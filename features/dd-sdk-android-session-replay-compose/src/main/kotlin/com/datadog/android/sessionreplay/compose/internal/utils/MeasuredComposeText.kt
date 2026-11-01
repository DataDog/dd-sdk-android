/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Paint
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import com.datadog.android.sessionreplay.compose.internal.reflection.getClassSafe

/** A main-thread-only view of the plain paragraph that the application has already measured and drawn. */
internal class MeasuredComposeText(
    val paragraph: Paragraph,
    val text: String,
    val style: TextStyle,
    val size: IntSize,
    private val paint: Paint
) {
    // A changing ColorProducer or brush must use the existing semantics renderer instead of stale paint.
    @Suppress("UnsafeThirdPartyFunctionCall") // Paint metadata is read on the UI thread.
    fun matches(source: String, color: ULong): Boolean =
        text == source && style.brush == null && paint.shader == null &&
            Color(color).isSpecified && paint.color == Color(color).toArgb()

    @Suppress("UnsafeThirdPartyFunctionCall") // Draw the current paragraph without paint overrides.
    fun draw(canvas: Canvas) {
        // Null/unspecified overrides leave the application's current paint parameters intact.
        // TextStringSimpleNode itself paints with this same default SrcOver blend mode.
        paragraph.paint(canvas, Color.Unspecified, null, null, null)
    }

    companion object {
        @Suppress("UnsafeThirdPartyFunctionCall") // Method.returnType is never null.
        private val paintGetter = getClassSafe("androidx.compose.ui.text.AndroidParagraph", false)
            ?.methods?.firstOrNull {
                it.name.substringBefore('$') == "getTextPaint" && it.parameterTypes.isEmpty() &&
                    Paint::class.java.isAssignableFrom(it.returnType)
            }

        @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Optional getter on a checked paragraph owner.
        fun readPaint(paragraph: Paragraph): Paint? {
            val getter = paintGetter ?: return null
            if (!getter.declaringClass.isInstance(paragraph)) return null
            return try {
                getter.invoke(paragraph) as? Paint
            } catch (_: ReflectiveOperationException) {
                null
            }
        }
    }
}
