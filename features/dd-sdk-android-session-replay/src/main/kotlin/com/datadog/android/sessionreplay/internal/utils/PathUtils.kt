/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.internal.recorder.resources.BitmapCachesManager
import com.datadog.android.sessionreplay.internal.recorder.resources.HashGenerator
import com.datadog.android.sessionreplay.internal.recorder.resources.MD5HashGenerator
import com.datadog.android.sessionreplay.recorder.wrappers.BitmapWrapper
import com.datadog.android.sessionreplay.recorder.wrappers.CanvasWrapper

internal class PathUtils(
    private val logger: InternalLogger = InternalLogger.UNBOUND,
    private val bitmapCachesManager: BitmapCachesManager,
    private val canvasWrapper: CanvasWrapper = CanvasWrapper(logger),
    private val bitmapWrapper: BitmapWrapper = BitmapWrapper(),
    private val md5Generator: HashGenerator = MD5HashGenerator(logger)
) {
    internal fun convertPathToBitmap(
        checkPath: Path,
        checkmarkColor: Int,
        desiredWidth: Int,
        desiredHeight: Int,
        strokeWidth: Int
    ): Bitmap? {
        val scaledPath = scalePathToTargetDimensions(checkPath, desiredWidth, desiredHeight)

        val mutableBitmap = bitmapCachesManager.getBitmapByProperties(
            width = desiredWidth,
            height = desiredHeight,
            config = Bitmap.Config.ARGB_8888
        )
            ?: bitmapWrapper.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888)
            ?: return null

        return drawPathOntoBitmap(
            bitmap = mutableBitmap,
            scaledPath = scaledPath,
            strokeWidth = strokeWidth,
            checkmarkColor = checkmarkColor
        )
    }

    private fun drawPathToBitmap(
        checkmarkColor: Int,
        path: Path,
        targetStrokeWidth: Int,
        canvas: Canvas?
    ) {
        val paint = Paint().apply {
            color = checkmarkColor
            style = Paint.Style.STROKE
            strokeWidth = targetStrokeWidth.toFloat()
            isAntiAlias = true
        }

        // Draw the Path onto the Canvas
        drawPathSafe(canvas, path, paint)
    }

    private fun scalePathToTargetDimensions(
        path: Path,
        targetWidth: Int,
        targetHeight: Int
    ): Path {
        // path initial bounds
        val originalBounds = RectF()

        @Suppress("DEPRECATION") // the new api is flagged as unstable
        path.computeBounds(originalBounds, true)

        // calculate the scale factor
        val scaleX = targetWidth / originalBounds.width()
        val scaleY = targetHeight / originalBounds.height()
        val scaleFactor = minOf(scaleX, scaleY)

        // current center
        val currentCenterX = (originalBounds.left + originalBounds.right) / 2
        val currentCenterY = (originalBounds.top + originalBounds.bottom) / 2

        // new center
        val newCenterX = targetWidth / 2
        val newCenterY = targetHeight / 2

        // center changes after scaling
        val scaledCenterX = currentCenterX * scaleFactor
        val scaledCenterY = currentCenterY * scaleFactor

        // translation needed to recenter
        val translateX = newCenterX - scaledCenterX
        val translateY = newCenterY - scaledCenterY

        // the order of operations is important
        val matrix = Matrix()
        matrix.preTranslate(translateX, translateY)
        matrix.preScale(scaleFactor, scaleFactor)
        path.transform(matrix)

        return path
    }

    private fun drawPathOntoBitmap(
        bitmap: Bitmap,
        scaledPath: Path,
        strokeWidth: Int,
        checkmarkColor: Int
    ): Bitmap? {
        val canvas = canvasWrapper.createCanvas(bitmap) ?: return null

        // draw the checkmark
        drawPathToBitmap(checkmarkColor, scaledPath, strokeWidth, canvas)

        return bitmap
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // handling IllegalArgumentException
    private fun drawPathSafe(canvas: Canvas?, path: Path, paint: Paint) {
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

    internal fun generateKeyForPath(
        path: Path,
        maxPoints: Int = DEFAULT_MAX_PATH_LENGTH,
        sampleInterval: Float = DEFAULT_SAMPLE_INTERVAL,
        pathMeasure: PathMeasure = PathMeasure(path, false)
    ): String? {
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val sampledPoints = StringBuilder()
        var pointCount = 0

        var distance = 0f
        while (distance < pathMeasure.length && pointCount < maxPoints) {
            @Suppress("UnsafeThirdPartyFunctionCall") // pos and tan size not lt 2
            pathMeasure.getPosTan(distance, pos, tan)

            sampledPoints.append("${pos[0]},${pos[1]};")
            pointCount++
            distance += sampleInterval
            if (!pathMeasure.nextContour()) break
        }

        val points = sampledPoints.toString()

        return if (points == EMPTY_POINTS) {
            null
        } else {
            md5Generator.generate(points.toByteArray())
        }
    }

    internal companion object {
        internal const val PATH_DRAW_ERROR = "Failed to draw Path to Canvas"
        internal const val EMPTY_POINTS = "0.0,0.0;"
        internal const val DEFAULT_MAX_PATH_LENGTH = 1000
        internal const val DEFAULT_SAMPLE_INTERVAL = 10f
    }
}
