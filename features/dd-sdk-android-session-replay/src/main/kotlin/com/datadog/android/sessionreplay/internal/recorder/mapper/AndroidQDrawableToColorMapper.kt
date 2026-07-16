/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

//noinspection SuspiciousImport
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.ColorFilter
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.utils.ALPHA_SHIFT_ANDROID
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.MAX_ALPHA_VALUE

/**
 * Drawable utility object needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it carefully as it might change in time.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal open class AndroidQDrawableToColorMapper(
    extensionMappers: List<DrawableToColorMapper> = emptyList()
) : AndroidMDrawableToColorMapper(extensionMappers) {

    override fun resolveGradientDrawable(drawable: GradientDrawable, internalLogger: InternalLogger): Int? {
        // colorFilter is only present when a tint/blend was actually applied — absent for the
        // common case of a plain untinted <shape> drawable. resolvedColor (the drawable's own
        // <solid> fill color) is already valid on its own in that case, so colorFilter should
        // only ever refine it, never gate it away entirely.
        val resolvedColor = resolveGradientFillColor(drawable) ?: return null
        val colorFilter = resolveGradientColorFilter(drawable)
        val colorAlpha = (resolvedColor ushr ALPHA_SHIFT_ANDROID) and MAX_ALPHA_VALUE
        val fillAlpha = (colorAlpha * drawable.alpha) / MAX_ALPHA_VALUE
        val fillColor = if (colorFilter != null) {
            resolveBlendModeColorFilter(resolvedColor, colorFilter, internalLogger)
        } else {
            resolvedColor
        }
        return if (fillAlpha == 0) null else mergeColorAndAlpha(fillColor, fillAlpha)
    }

    protected open fun resolveGradientColorFilter(drawable: GradientDrawable): ColorFilter? =
        drawable.colorFilter

    /**
     * This is an oversimplification as the result image would only have some
     * pixels with the given color, but here we're reducing a background to a single color.
     * cf: https://developer.android.com/reference/android/graphics/BlendMode
     * TODO RUM-3469 resolve other blend modes
     */
    private fun resolveBlendModeColorFilter(
        fillColor: Int,
        colorFilter: ColorFilter,
        internalLogger: InternalLogger
    ): Int {
        return if (colorFilter is BlendModeColorFilter) {
            when (colorFilter.mode) {
                in blendModesReturningBlendColor -> colorFilter.color
                in blendModesReturningOriginalColor -> fillColor
                else -> {
                    internalLogger.log(
                        level = InternalLogger.Level.INFO,
                        target = InternalLogger.Target.TELEMETRY,
                        messageBuilder = { "No mapper found for gradient blend mode ${colorFilter.mode}" },
                        throwable = null,
                        onlyOnce = true,
                        additionalProperties = mapOf(
                            "replay.gradient.blend_mode" to colorFilter.mode
                        )
                    )
                    fillColor
                }
            }
        } else {
            internalLogger.log(
                level = InternalLogger.Level.INFO,
                target = InternalLogger.Target.TELEMETRY,
                messageBuilder = { "No mapper found for gradient color filter ${colorFilter.javaClass}" },
                throwable = null,
                onlyOnce = true,
                additionalProperties = mapOf(
                    "replay.gradient.filter_type" to colorFilter.javaClass.canonicalName
                )
            )
            fillColor
        }
    }

    companion object {
        internal val blendModesReturningBlendColor = listOf(
            BlendMode.SRC,
            BlendMode.SRC_ATOP,
            BlendMode.SRC_IN,
            BlendMode.SRC_OUT,
            BlendMode.SRC_OVER
        )

        internal val blendModesReturningOriginalColor = listOf(
            BlendMode.DST,
            BlendMode.DST_ATOP,
            BlendMode.DST_IN,
            BlendMode.DST_OUT,
            BlendMode.DST_OVER
        )
    }
}
