/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import com.datadog.android.sessionreplay.internal.utils.InvocationUtils

internal class BitmapPoolHelper(
    private val invocationUtils: InvocationUtils = InvocationUtils()
) {
    internal fun generateKey(bitmap: Bitmap): String =
        generateKey(bitmap.width, bitmap.height, bitmap.config)

    internal fun generateKey(width: Int, height: Int, config: Bitmap.Config?): String =
        "$width-$height-$config"

    internal fun <R> safeCall(call: () -> R): R? =
        invocationUtils.safeCallWithErrorLogging(
            call = { call() },
            failureMessage = BITMAP_OPERATION_FAILED
        )

    private companion object {
        private const val BITMAP_OPERATION_FAILED = "operation failed for bitmap pool"
    }
}
