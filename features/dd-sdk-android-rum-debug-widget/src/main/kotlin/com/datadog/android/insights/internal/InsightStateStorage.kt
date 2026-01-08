/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal class InsightStateStorage(context: Context) {
    private val sharedPreferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        INSIGHT_PREFERENCES,
        Context.MODE_PRIVATE
    )

    @Suppress("UnsafeThirdPartyFunctionCall") // safe because there is a putFloat call for each getFloat
    var widgetPosition: Pair<Float, Float>
        get() = Pair(
            sharedPreferences.getFloat(WIDGET_X, INVALID),
            sharedPreferences.getFloat(WIDGET_Y, INVALID)
        )
        set(value) {
            sharedPreferences.edit {
                putFloat(WIDGET_X, value.first)
                putFloat(WIDGET_Y, value.second)
            }
        }

    @Suppress("UnsafeThirdPartyFunctionCall") // safe because there are putFloat calls for each getFloat
    var fabPosition: Pair<Float, Float>
        get() = Pair(
            sharedPreferences.getFloat(FAB_X, INVALID),
            sharedPreferences.getFloat(FAB_Y, INVALID)
        )
        set(value) {
            sharedPreferences.edit {
                putFloat(FAB_X, value.first)
                putFloat(FAB_Y, value.second)
            }
        }

    var widgetDisplayed: Boolean
        @Suppress("UnsafeThirdPartyFunctionCall") // safe because there is a putBoolean call for each getBoolean
        get() = sharedPreferences.getBoolean(WIDGET_DISPLAYED, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(WIDGET_DISPLAYED, value)
            }
        }

    companion object {
        private const val INVALID = -1f
        private const val INSIGHT_PREFERENCES = "DD.INSIGHT_PREFERENCES"
        private const val WIDGET_X = "DD.WIDGET_X"
        private const val WIDGET_Y = "DD.WIDGET_Y"
        private const val FAB_X = "DD.FAB_X"
        private const val FAB_Y = "DD.FAB_Y"
        private const val WIDGET_DISPLAYED = "DD.WIDGET_DISPLAYED"
    }
}
