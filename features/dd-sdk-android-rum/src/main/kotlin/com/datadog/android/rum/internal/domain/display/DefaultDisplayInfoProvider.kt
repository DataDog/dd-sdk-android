/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.domain.InfoProvider
import kotlin.math.roundToInt

internal class DefaultDisplayInfoProvider(
    private val applicationContext: Context,
    private val internalLogger: InternalLogger,
    private val systemSettingsWrapper: SystemSettingsWrapper = SystemSettingsWrapper(
        applicationContext = applicationContext,
        internalLogger = internalLogger
    ),
    private val contentResolver: ContentResolver = applicationContext.contentResolver,
    private val handler: Handler = Handler(Looper.getMainLooper())
) : InfoProvider {

    @Volatile
    private var currentState = DisplayInfo()

    private val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val brightness = systemSettingsWrapper.getInt(SCREEN_BRIGHTNESS)

            if (brightness != Integer.MIN_VALUE) {
                val normalizedValue = normalizeValue(brightness)

                currentState = currentState.copy(
                    screenBrightness = normalizedValue
                )
            }
        }
    }

    init {
        registerListeners()
        buildInitialState()
    }

    override fun getState(): Map<String, Any> {
        return currentState.toMap()
    }

    override fun cleanup() {
        contentResolver.unregisterContentObserver(brightnessObserver)
    }

    private fun registerListeners() {
        val brightnessUri = Settings.System.getUriFor(SCREEN_BRIGHTNESS)
        brightnessUri?.let {
            contentResolver.registerContentObserver(brightnessUri, false, brightnessObserver)
        }
    }

    private fun buildInitialState() {
        val brightness = systemSettingsWrapper.getInt(SCREEN_BRIGHTNESS)

        // if we got a valid value
        if (brightness != Integer.MIN_VALUE) {
            val normalizedValue = normalizeValue(brightness)
            currentState = DisplayInfo(
                screenBrightness = normalizedValue
            )
        }
    }

    private fun normalizeValue(value: Int): Float {
        val normalizedBrightness = value / MAX_BRIGHTNESS
        return roundToOneDecimalPlace(normalizedBrightness)
    }

    private fun roundToOneDecimalPlace(input: Float): Float {
        return (input * DECIMAL_SCALING).roundToInt() / DECIMAL_SCALING
    }

    private companion object {
        const val MAX_BRIGHTNESS = 255f
        const val DECIMAL_SCALING = 10f
    }
}
