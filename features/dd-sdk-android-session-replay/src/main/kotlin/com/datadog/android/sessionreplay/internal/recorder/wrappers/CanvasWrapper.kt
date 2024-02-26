/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Canvas
import com.datadog.android.api.InternalLogger

internal class CanvasWrapper(
    private val logger: InternalLogger = InternalLogger.UNBOUND
) {
    internal fun createCanvas(bitmap: Bitmap): Canvas? {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
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
        private const val FAILED_TO_CREATE_CANVAS = "Failed to create canvas"
    }
}
