/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.attribute

import android.provider.Settings
import android.util.Log
import com.datadog.android.BuildConfig

/**
 * Represents accessibility state information collected from the device.
 * All properties are nullable to handle cases where values cannot be determined.
 */
internal data class Accessibility(
    val textSize: Float? = null,
    val isScreenReaderEnabled: Boolean? = null,
    val isColorInversionEnabled: Boolean? = null,
    val isSwitchAccessEnabled: Boolean? = null,
    val isClosedCaptioningEnabled: Boolean? = null,
    val isMonoAudioEnabled: Boolean? = null
) {
    
    /**
     * Converts the accessibility state to a map for serialization.
     * Only includes non-null values to avoid cluttering the output.
     */
    fun toMap(): Map<String, Any> = buildMap {
        textSize?.let { put(TEXT_SIZE_KEY, it) }
        isScreenReaderEnabled?.let { put(SCREEN_READER_ENABLED_KEY, it) }
        isColorInversionEnabled?.let { put(COLOR_INVERSION_ENABLED_KEY, it) }
        isSwitchAccessEnabled?.let { put(SWITCH_ACCESS_ENABLED_KEY, it) }
        isClosedCaptioningEnabled?.let { put(CLOSED_CAPTIONING_ENABLED_KEY, it) }
        isMonoAudioEnabled?.let { put(MONO_AUDIO_ENABLED_KEY, it) }
    }

    companion object {
        private const val TAG = "Accessibility"
        
        /**
         * Default accessibility state with all values unknown/unsupported.
         */
        val DEFAULT = Accessibility(
            textSize = null,
            isScreenReaderEnabled = null,
            isColorInversionEnabled = null,
            isSwitchAccessEnabled = null,
            isClosedCaptioningEnabled = null,
            isMonoAudioEnabled = null
        )
        
        // Serialization keys
        private const val TEXT_SIZE_KEY = "text_size"
        private const val SCREEN_READER_ENABLED_KEY = "screen_reader_enabled"
        private const val COLOR_INVERSION_ENABLED_KEY = "is_color_inversion_enabled"
        private const val SWITCH_ACCESS_ENABLED_KEY = "is_switch_access_enabled"
        private const val CLOSED_CAPTIONING_ENABLED_KEY = "is_closed_captioning_enabled"
        private const val MONO_AUDIO_ENABLED_KEY = "is_mono_audio_enabled"
        
        // Validation constants
        private const val MIN_FONT_SCALE = 0.5f
        private const val MAX_FONT_SCALE = 5.0f

        /**
         * Creates an Accessibility instance from a map, with robust error handling.
         * @param map The map to parse, can be null
         * @return Accessibility instance, defaults to DEFAULT if parsing fails
         */
        fun fromMap(map: Map<String, Any?>?): Accessibility {
            if (map == null) {
                return DEFAULT
            }
            
            return Accessibility(
                textSize = parseTextSize(map),
                isScreenReaderEnabled = parseBooleanSafely(map, SCREEN_READER_ENABLED_KEY),
                isColorInversionEnabled = parseBooleanSafely(map, COLOR_INVERSION_ENABLED_KEY),
                isSwitchAccessEnabled = parseBooleanSafely(map, SWITCH_ACCESS_ENABLED_KEY),
                isClosedCaptioningEnabled = parseBooleanSafely(map, CLOSED_CAPTIONING_ENABLED_KEY),
                isMonoAudioEnabled = parseBooleanSafely(map, MONO_AUDIO_ENABLED_KEY)
            )
        }
        
        private fun parseTextSize(map: Map<String, Any?>): Float? {
            return try {
                when (val value = map[TEXT_SIZE_KEY]) {
                    is Number -> {
                        val floatValue = value.toFloat()
                        if (floatValue in MIN_FONT_SCALE..MAX_FONT_SCALE) {
                            floatValue
                        } else {
                            logWarning("Text size out of valid range: $floatValue")
                            null
                        }
                    }
                    is String -> {
                        val floatValue = value.toFloatOrNull()
                        if (floatValue != null && floatValue in MIN_FONT_SCALE..MAX_FONT_SCALE) {
                            floatValue
                        } else {
                            logWarning("Invalid text size string: $value")
                            null
                        }
                    }
                    null -> null
                    else -> {
                        logWarning("Unexpected text size type: ${value::class.simpleName}")
                        null
                    }
                }
            } catch (e: SecurityException) {
                logWarning("SecurityException parsing text size: ${e.message}")
                null
            } catch (e: Settings.SettingNotFoundException) {
                logWarning("Text size setting not found: ${e.message}")
                null
            } catch (e: Exception) {
                logWarning("Unexpected error parsing text size: ${e.message}")
                null
            }
        }

        private fun parseBooleanSafely(map: Map<String, Any?>, key: String): Boolean? {
            return try {
                when (val value = map[key]) {
                    is Boolean -> value
                    is String -> value.toBooleanStrictOrNull()
                    is Number -> value.toInt() != 0
                    null -> null
                    else -> {
                        logWarning("Unexpected boolean type for $key: ${value::class.simpleName}")
                        null
                    }
                }
            } catch (e: SecurityException) {
                logWarning("SecurityException parsing $key: ${e.message}")
                null
            } catch (e: Settings.SettingNotFoundException) {
                logWarning("Setting not found for $key: ${e.message}")
                null
            } catch (e: Exception) {
                logWarning("Unexpected error parsing $key: ${e.message}")
                null
            }
        }
        
        private fun logWarning(message: String) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, message)
            }
        }
    }
}
