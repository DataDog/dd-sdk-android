/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

/**
 * Represents the accessibility settings state of the device.
 *
 * @property textSize The font scale factor (1.0 = normal, >1.0 = larger, <1.0 = smaller)
 * @property isScreenReaderEnabled Whether touch exploration is enabled (TalkBack, etc.)
 * @property isColorInversionEnabled Whether color inversion is enabled
 * @property isClosedCaptioningEnabled Whether closed captions are enabled
 * @property isReducedAnimationsEnabled Whether animations are disabled/reduced
 * @property isScreenPinningEnabled Whether the device is in single-app mode
 */
internal data class Accessibility(
    val textSize: Float? = null,
    val isScreenReaderEnabled: Boolean? = null,
    val isColorInversionEnabled: Boolean? = null,
    val isClosedCaptioningEnabled: Boolean? = null,
    val isReducedAnimationsEnabled: Boolean? = null,
    val isScreenPinningEnabled: Boolean? = null
) {
    fun toMap(): Map<String, Any> = buildMap {
        textSize?.let { put(TEXT_SIZE_KEY, it) }
        isScreenReaderEnabled?.let { put(SCREEN_READER_ENABLED_KEY, it) }
        isColorInversionEnabled?.let { put(COLOR_INVERSION_ENABLED_KEY, it) }
        isClosedCaptioningEnabled?.let { put(CLOSED_CAPTIONING_ENABLED_KEY, it) }
        isReducedAnimationsEnabled?.let { put(REDUCED_ANIMATIONS_ENABLED_KEY, it) }
        isScreenPinningEnabled?.let { put(SCREEN_PINNING_ENABLED_KEY, it) }
    }

    companion object {
        internal val EMPTY_STATE = Accessibility(
            textSize = null,
            isScreenReaderEnabled = null,
            isColorInversionEnabled = null,
            isClosedCaptioningEnabled = null,
            isReducedAnimationsEnabled = null,
            isScreenPinningEnabled = null
        )

        internal const val TEXT_SIZE_KEY = "text_size"
        internal const val SCREEN_READER_ENABLED_KEY = "screen_reader_enabled"
        internal const val COLOR_INVERSION_ENABLED_KEY = "invert_colors_enabled"
        internal const val CLOSED_CAPTIONING_ENABLED_KEY = "closed_captioning_enabled"
        internal const val REDUCED_ANIMATIONS_ENABLED_KEY = "reduced_animations_enabled"
        internal const val SCREEN_PINNING_ENABLED_KEY = "single_app_mode_enabled"
    }
}
