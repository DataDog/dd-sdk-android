/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper

/**
 * Extracted style information from a drawable, including fill color, border, and corner radius.
 */
internal data class DrawableStyleInfo(
    val color: Int?,
    val cornerRadius: Float,
    val borderColor: Int?,
    val borderWidth: Float
)

/**
 * Extracts full style information (fill color, border, corner radius) from drawables.
 * Complements [DrawableToColorMapper] by also extracting border and corner radius from
 * [GradientDrawable] backgrounds via reflection.
 */
internal class DrawableStyleExtractor(
    private val drawableToColorMapper: DrawableToColorMapper
) {

    fun extractStyleInfo(drawable: Drawable, internalLogger: InternalLogger): DrawableStyleInfo {
        val color = drawableToColorMapper.mapDrawableToColor(drawable, internalLogger)
        val gradientDrawable = findGradientDrawable(drawable)

        val cornerRadius = gradientDrawable?.let { extractCornerRadius(it) } ?: 0f
        val borderColor: Int?
        val borderWidth: Float

        if (gradientDrawable != null) {
            val strokeInfo = extractStrokeInfo(gradientDrawable)
            borderColor = strokeInfo.first
            borderWidth = strokeInfo.second
        } else {
            borderColor = null
            borderWidth = 0f
        }

        return DrawableStyleInfo(
            color = color,
            cornerRadius = cornerRadius,
            borderColor = borderColor,
            borderWidth = borderWidth
        )
    }

    /**
     * Recursively unwraps wrapper drawables to find the inner [GradientDrawable].
     */
    private fun findGradientDrawable(drawable: Drawable): GradientDrawable? {
        return when (drawable) {
            is GradientDrawable -> drawable
            is StateListDrawable -> {
                @Suppress("UNNECESSARY_SAFE_CALL")
                drawable.current?.let { findGradientDrawable(it) }
            }
            is RippleDrawable -> findGradientDrawableInLayers(drawable)
            is LayerDrawable -> findGradientDrawableInLayers(drawable)
            is InsetDrawable -> drawable.drawable?.let { findGradientDrawable(it) }
            else -> null
        }
    }

    private fun findGradientDrawableInLayers(drawable: LayerDrawable): GradientDrawable? {
        for (i in 0 until drawable.numberOfLayers) {
            @Suppress("UnsafeThirdPartyFunctionCall")
            val layer = drawable.getDrawable(i)
            if (layer != null) {
                val result = findGradientDrawable(layer)
                if (result != null) return result
            }
        }
        return null
    }

    @Suppress("SwallowedException")
    private fun extractCornerRadius(drawable: GradientDrawable): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            drawable.cornerRadius
        } else {
            try {
                @Suppress("UnsafeThirdPartyFunctionCall")
                val state = gradientStateField?.get(drawable) ?: return 0f
                @Suppress("UnsafeThirdPartyFunctionCall")
                (radiusField?.get(state) as? Float) ?: 0f
            } catch (e: IllegalAccessException) {
                0f
            } catch (e: IllegalArgumentException) {
                0f
            }
        }
    }

    @Suppress("SwallowedException")
    private fun extractStrokeInfo(drawable: GradientDrawable): Pair<Int?, Float> {
        val strokePaint = try {
            @Suppress("UnsafeThirdPartyFunctionCall")
            strokePaintField?.get(drawable) as? Paint
        } catch (e: IllegalAccessException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: ExceptionInInitializerError) {
            null
        }

        @Suppress("UnsafeThirdPartyFunctionCall")
        return if (strokePaint != null && strokePaint.strokeWidth > 0f) {
            Pair(strokePaint.color, strokePaint.strokeWidth)
        } else {
            Pair(null, 0f)
        }
    }

    companion object {
        @SuppressLint("DiscouragedPrivateApi")
        @Suppress("PrivateAPI", "SwallowedException", "TooGenericExceptionCaught")
        internal val strokePaintField = try {
            GradientDrawable::class.java.getDeclaredField("mStrokePaint").apply {
                this.isAccessible = true
            }
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: NullPointerException) {
            null
        }

        @SuppressLint("DiscouragedPrivateApi")
        @Suppress("PrivateAPI", "SwallowedException", "TooGenericExceptionCaught")
        private val gradientStateField = try {
            GradientDrawable::class.java.getDeclaredField("mGradientState").apply {
                this.isAccessible = true
            }
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: NullPointerException) {
            null
        }

        @SuppressLint("DiscouragedPrivateApi")
        @Suppress("PrivateAPI", "SwallowedException", "TooGenericExceptionCaught")
        private val radiusField = try {
            gradientStateField?.type?.getDeclaredField("mRadius")?.apply {
                this.isAccessible = true
            }
        } catch (e: NoSuchFieldException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: NullPointerException) {
            null
        }
    }
}
