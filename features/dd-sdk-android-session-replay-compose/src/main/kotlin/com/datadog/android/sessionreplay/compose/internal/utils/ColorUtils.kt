/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.graphics.Color
import com.datadog.android.api.InternalLogger
import java.util.Locale

internal class ColorUtils(
    private val logger: InternalLogger = InternalLogger.UNBOUND
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

    internal companion object {
        internal const val COLOR_PARSE_ERROR = "Failed to parse color: %s"
    }
}
