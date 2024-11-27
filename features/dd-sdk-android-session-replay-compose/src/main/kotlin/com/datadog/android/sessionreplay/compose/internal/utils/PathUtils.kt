/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.CHECKBOX_SIZE_DP
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.CheckboxSemanticsNodeMapper.Companion.STROKE_WIDTH_DP
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper
import java.util.Locale
import android.graphics.Path as AndroidPath

internal class PathUtils(
    private val logger: InternalLogger = InternalLogger.UNBOUND,
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(logger),
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper()
) {
    internal fun parseColorSafe(color: String): Int? {
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // handling IllegalArgumentException
            Color.parseColor(color)
        } catch (e: IllegalArgumentException) {
            logger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.WARN,
                messageBuilder = { COLOR_PARSE_ERROR.format(Locale.US, color) },
                throwable = e
            )
            null
        }
    }

    internal fun convertRgbaToArgb(rgbaString: String): String {
        if (rgbaString.length < 2) return rgbaString

        // for takeLast: n > 0
        @Suppress("UnsafeThirdPartyFunctionCall")
        val alphaValue = rgbaString.takeLast(2)

        // for substring: length is necessarily > 1 at this point
        // for dropLast: n > 0
        @Suppress("UnsafeThirdPartyFunctionCall")
        val rgbColor = rgbaString
            .substring(1)
            .dropLast(2)
        return "#$alphaValue$rgbColor"
    }

    internal fun asAndroidPathSafe(path: Path): AndroidPath? {
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // handling UnsupportedOperationException
            path.asAndroidPath()
        } catch (e: UnsupportedOperationException) {
            logger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.WARN,
                messageBuilder = { PATH_CONVERSION_ERROR },
                throwable = e
            )
            null
        }
    }

    internal fun convertPathToBitmap(
        checkPath: Path,
        fillColor: Int,
        checkmarkColor: Int
    ): Bitmap? {
        val scaledPath = scalePathToBitmapSize(checkPath)
        val mutableBitmap =
            bitmapWrapper.createBitmap(CHECKBOX_SIZE_DP, CHECKBOX_SIZE_DP, Bitmap.Config.ARGB_8888)
                ?: return null

        return drawPathOntoBitmap(
            mutableBitmap,
            scaledPath,
            fillColor,
            checkmarkColor
        )
    }

    private fun drawPathToBitmap(checkmarkColor: Int, path: Path, canvas: Canvas?) {
        val paint = Paint().apply {
            color = checkmarkColor
            style = Paint.Style.STROKE
            strokeWidth = STROKE_WIDTH_DP
            isAntiAlias = true
        }

        // Draw the Path onto the Canvas
        asAndroidPathSafe(path)?.let {
            drawPathSafe(canvas, it, paint)
        }
    }

    private fun scalePathToBitmapSize(path: Path): Path {
        // path initial bounds
        val originalBounds = path.getBounds()

        // calculate the scale factor
        val scaleX = CHECKBOX_SIZE_DP / originalBounds.width
        val scaleY = CHECKBOX_SIZE_DP / originalBounds.height
        val scaleFactor = minOf(scaleX, scaleY)

        // current center
        val currentCenterX = (originalBounds.left + originalBounds.right) / 2
        val currentCenterY = (originalBounds.top + originalBounds.bottom) / 2

        // new center
        val newCenterX = CHECKBOX_SIZE_DP / 2
        val newCenterY = CHECKBOX_SIZE_DP / 2

        // center changes after scaling
        val scaledCenterX = currentCenterX * scaleFactor
        val scaledCenterY = currentCenterY * scaleFactor

        // translation needed to recenter
        val translateX = newCenterX - scaledCenterX
        val translateY = newCenterY - scaledCenterY

        // the order of operations is important
        val matrix = Matrix()
        matrix.translate(translateX, translateY)
        matrix.scale(scaleFactor, scaleFactor)
        path.transform(matrix)

        return path
    }

    private fun drawPathOntoBitmap(
        bitmap: Bitmap,
        scaledPath: Path,
        fillColor: Int,
        checkmarkColor: Int
    ): Bitmap? {
        val canvas = canvasWrapper.createCanvas(bitmap) ?: return null

        // draw the background
        canvas.drawColor(fillColor)

        // draw the checkmark
        drawPathToBitmap(checkmarkColor, scaledPath, canvas)

        return bitmap
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // handling IllegalArgumentException
    private fun drawPathSafe(canvas: Canvas?, path: AndroidPath, paint: Paint) {
        try {
            canvas?.drawPath(path, paint)
        } catch (e: IllegalArgumentException) {
            logger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.WARN,
                messageBuilder = { PATH_DRAW_ERROR },
                throwable = e
            )
        }
    }

    internal companion object {
        internal const val COLOR_PARSE_ERROR = "Failed to parse color: %s"
        internal const val PATH_CONVERSION_ERROR = "Failed to convert Compose Path to Android Path"
        internal const val PATH_DRAW_ERROR = "Failed to draw Path to Canvas"
    }
}
