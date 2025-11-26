/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore

internal interface BitmapCreationHelper {
    fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap?
    fun drawBitmap(destination: Bitmap, source: Bitmap): Bitmap?
}

internal class DefaultBitmapCreationHelper(
    private val logger: InternalLogger =
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger
            ?: InternalLogger.UNBOUND
) : BitmapCreationHelper {
    override fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        // IllegalStateException shouldn't happen - we check dimensions
        @Suppress("UnsafeThirdPartyFunctionCall")
        return Bitmap.createBitmap(width, height, config)
    }

    override fun drawBitmap(destination: Bitmap, source: Bitmap): Bitmap? {
        if (!destination.isMutable || destination.isRecycled || source.isRecycled) {
            logger.log(
                level = InternalLogger.Level.DEBUG,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { "Bitmap creation failed: destination is immutable or recycled" }
            )
            return null
        }

        // IllegalStateException shouldn't happen - we check for mutability
        @Suppress("UnsafeThirdPartyFunctionCall")
        val canvas = Canvas(destination)

        // Fill with Black (Transparency -> Black pixel)
        canvas.drawColor(android.graphics.Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // Draw content in White (Opacity -> White pixel)
        // Intermediate alpha levels will blend to create shades of gray (Grayscale)
        paint.color = android.graphics.Color.WHITE

        return try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            canvas.drawBitmap(source, 0f, 0f, paint)
            destination
        } catch (e: IllegalArgumentException) {
            // This can happen if:
            // - The source bitmap is recycled.
            // - The source and destination are the same.
            // - The bitmap has hardware features (e.g. Config.HARDWARE) incompatible with software rendering
            //   (throwIfHasHwFeaturesInSwMode).
            logger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.ERROR,
                messageBuilder = { BITMAP_DRAWING_FAILURE },
                throwable = e
            )
            null
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            // This can happen if the bitmap is recycled (on some Android versions) or on other native failures.
            logger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.ERROR,
                messageBuilder = { BITMAP_DRAWING_FAILURE },
                throwable = e
            )
            null
        } catch (e: OutOfMemoryError) {
            // This can happen if the native allocation fails during drawing.
            logger.log(
                target = InternalLogger.Target.MAINTAINER,
                level = InternalLogger.Level.ERROR,
                messageBuilder = { BITMAP_DRAWING_FAILURE },
                throwable = e
            )
            null
        }
    }

    private companion object {
        const val BITMAP_DRAWING_FAILURE = "Bitmap drawing failed"
    }
}
