/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

internal data class Accessibility(
    val textSize: Float? = null,
    val isScreenReaderEnabled: Boolean? = null,
    val isColorInversionEnabled: Boolean? = null,
    val isSwitchAccessEnabled: Boolean? = null,
    val isClosedCaptioningEnabled: Boolean? = null,
    val isMonoAudioEnabled: Boolean? = null,
    val isReducedAnimationsEnabled: Boolean? = null,
    val isScreenPinningEnabled: Boolean? = null,
    val isSelectToSpeakEnabled: Boolean? = null
) {
    fun toMap(): Map<String, Any> = buildMap {
        textSize?.let { put(TEXT_SIZE_KEY, it) }
        isScreenReaderEnabled?.let { put(SCREEN_READER_ENABLED_KEY, it) }
        isColorInversionEnabled?.let { put(COLOR_INVERSION_ENABLED_KEY, it) }
        isSwitchAccessEnabled?.let { put(SWITCH_ACCESS_ENABLED_KEY, it) }
        isClosedCaptioningEnabled?.let { put(CLOSED_CAPTIONING_ENABLED_KEY, it) }
        isMonoAudioEnabled?.let { put(MONO_AUDIO_ENABLED_KEY, it) }
        isReducedAnimationsEnabled?.let { put(REDUCED_ANIMATIONS_ENABLED_KEY, it) }
        isScreenPinningEnabled?.let { put(SCREEN_PINNING_ENABLED_KEY, it) }
        isSelectToSpeakEnabled?.let { put(SELECT_TO_SPEAK_ENABLED_KEY, it) }
    }

    companion object {
        internal val EMPTY_STATE = Accessibility(
            textSize = null,
            isScreenReaderEnabled = null,
            isColorInversionEnabled = null,
            isSwitchAccessEnabled = null,
            isClosedCaptioningEnabled = null,
            isMonoAudioEnabled = null,
            isReducedAnimationsEnabled = null,
            isScreenPinningEnabled = null,
            isSelectToSpeakEnabled = null
        )

        internal const val TEXT_SIZE_KEY = "text_size"
        internal const val SCREEN_READER_ENABLED_KEY = "screen_reader_enabled"
        internal const val COLOR_INVERSION_ENABLED_KEY = "invert_colors_enabled"
        internal const val SWITCH_ACCESS_ENABLED_KEY = "assistive_switch_enabled"
        internal const val CLOSED_CAPTIONING_ENABLED_KEY = "closed_captioning_enabled"
        internal const val MONO_AUDIO_ENABLED_KEY = "mono_audio_enabled"
        internal const val REDUCED_ANIMATIONS_ENABLED_KEY = "reduced_animations_enabled"
        internal const val SCREEN_PINNING_ENABLED_KEY = "single_app_mode_enabled"
        internal const val SELECT_TO_SPEAK_ENABLED_KEY = "speak_selection_enabled"
    }
}
