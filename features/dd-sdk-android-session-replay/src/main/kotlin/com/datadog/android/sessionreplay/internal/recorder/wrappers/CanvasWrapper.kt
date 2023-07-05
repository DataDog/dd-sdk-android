/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.wrappers

import android.graphics.Bitmap
import android.graphics.Canvas

internal class CanvasWrapper {
    internal fun createCanvas(bitmap: Bitmap): Canvas? {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        return try {
            Canvas(bitmap)
        } catch (e: IllegalStateException) {
            // should never happen since we are passing an immutable bitmap
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            null
        } catch (e: RuntimeException) {
            // TODO: REPLAY-1364 Add logs here once the sdkLogger is added
            null
        }
    }
}
