/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Canvas
import com.datadog.android.api.InternalLogger

/**
 * Wraps the Canvas class to catch potential crashes.
 */
class CanvasWrapper(
    private val logger: InternalLogger
) {
    /**
     * Creates a canvas with the given bitmap.
     * @param bitmap the bitmap to use.
     * @return the created canvas or null if it failed.
     */
    fun createCanvas(bitmap: Bitmap): Canvas? {
        if (bitmap.isRecycled || !bitmap.isMutable) {
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { INVALID_BITMAP }
            )
            return null
        }

        @Suppress("TooGenericExceptionCaught")
        return try {
            Canvas(bitmap)
        } catch (e: IllegalStateException) {
            // should never happen since we are passing an immutable bitmap
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_CANVAS },
                e
            )
            null
        } catch (e: RuntimeException) {
            logger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                { FAILED_TO_CREATE_CANVAS },
                e
            )
            null
        }
    }

    private companion object {
        private const val INVALID_BITMAP = "Cannot create canvas: bitmap is either already recycled or immutable"
        private const val FAILED_TO_CREATE_CANVAS = "Failed to create canvas"
    }
}
