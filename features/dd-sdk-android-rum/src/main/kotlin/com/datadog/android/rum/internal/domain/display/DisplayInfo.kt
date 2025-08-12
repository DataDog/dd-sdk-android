/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

internal data class DisplayInfo(
    val screenBrightness: Number? = null
) {
    fun toMap(): Map<String, Any> = buildMap {
        screenBrightness?.let { put(SCREEN_BRIGHTNESS_KEY, it) }
    }

    internal companion object {
        const val SCREEN_BRIGHTNESS_KEY = "screen_brightness"

        fun fromMap(map: Map<String, Any>): DisplayInfo {
            return DisplayInfo(
                screenBrightness = map[SCREEN_BRIGHTNESS_KEY] as? Number
            )
        }
    }
}
