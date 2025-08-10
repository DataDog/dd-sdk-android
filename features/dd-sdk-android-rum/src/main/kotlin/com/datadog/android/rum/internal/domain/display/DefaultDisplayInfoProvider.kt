/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import android.content.Context
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import kotlin.math.roundToInt

internal class DefaultDisplayInfoProvider(
    private val applicationContext: Context,
    private val systemSettingsWrapper: SystemSettingsWrapper = SystemSettingsWrapper(applicationContext)
) : DisplayInfoProvider {

    override fun getBrightnessLevel(): Float? {
        val rawBrightnessValue = systemSettingsWrapper.getInt(SCREEN_BRIGHTNESS).toFloat()
        val normalizedBrightness = rawBrightnessValue / MAX_BRIGHTNESS
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
