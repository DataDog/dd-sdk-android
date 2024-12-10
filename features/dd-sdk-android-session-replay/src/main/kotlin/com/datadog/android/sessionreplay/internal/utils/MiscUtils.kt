/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.content.Context
import android.content.res.Resources.Theme
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.WindowManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.densityNormalized
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal object MiscUtils {

    private const val GET_STRING_FROM_JSON_ERROR = "Error getting string property from json"
    internal const val DESERIALIZE_JSON_ERROR = "Error deserializing json object"

    internal fun safeDeserializeToJsonObject(internalLogger: InternalLogger, jsonByteArray: ByteArray): JsonObject? {
        if (jsonByteArray.isEmpty()) return null

        val jsonString = String(jsonByteArray)
        return try {
            JsonParser.parseString(jsonString) as? JsonObject
        } catch (e: JsonParseException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { DESERIALIZE_JSON_ERROR },
                throwable = e
            )
            null
        }
    }

    internal fun safeGetStringFromJsonObject(internalLogger: InternalLogger, json: JsonObject, key: String): String? {
        return try {
            json.get(key)?.asString
        } catch (e: ClassCastException) {
            // this should never happen - element is a valid json already
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { GET_STRING_FROM_JSON_ERROR },
                throwable = e
            )
            null
        } catch (e: IllegalStateException) {
            // this should never happen - element is not jsonArray
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { GET_STRING_FROM_JSON_ERROR },
                throwable = e
            )
            null
        }
    }

    fun resolveThemeColor(theme: Theme): Int? {
        val a = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, a, true)
        return if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            a.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            // windowBackground is a color
            a.data
        } else {
            null
        }
    }

    fun resolveSystemInformation(context: Context): SystemInformation {
        val screenDensity = context.resources.displayMetrics.density
        val themeColorAsHexString = resolveThemeColor(context.theme)?.let {
            DefaultColorStringFormatter.formatColorAndAlphaAsHexString(it, OPAQUE_ALPHA_VALUE)
        }
        return SystemInformation(
            screenBounds = resolveScreenBounds(context, screenDensity),
            screenOrientation = context.resources.configuration.orientation,
            screenDensity = screenDensity,
            themeColor = themeColorAsHexString
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveScreenBounds(context: Context, screenDensity: Float): GlobalBounds {
        val windowManager = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?: return GlobalBounds(0, 0, 0, 0)
        val screenHeight: Long
        val screenWidth: Long
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentWindowMetrics = windowManager.currentWindowMetrics
            val screenBounds = currentWindowMetrics.bounds
            screenHeight = (screenBounds.bottom - screenBounds.top).toLong()
                .densityNormalized(screenDensity)
            screenWidth = (screenBounds.right - screenBounds.left).toLong()
                .densityNormalized(screenDensity)
        } else {
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            screenHeight = size.y.toLong().densityNormalized(screenDensity)
            screenWidth = size.x.toLong().densityNormalized(screenDensity)
        }
        return GlobalBounds(0, 0, screenWidth, screenHeight)
    }
}
