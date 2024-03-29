/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

//noinspection SuspiciousImport
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore

/**
 * Drawable utility object needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it carefully as it might change in time.
 */
open class LegacyDrawableToColorMapper : DrawableToColorMapper {

    override fun mapDrawableToColor(drawable: Drawable): Int? {
        val result = when (drawable) {
            is ColorDrawable -> resolveColorDrawable(drawable)
            is RippleDrawable -> resolveRippleDrawable(drawable)
            is LayerDrawable -> resolveLayerDrawable(drawable)
            is InsetDrawable -> resolveInsetDrawable(drawable)
            is GradientDrawable -> resolveGradientDrawable(drawable)
            else -> {
                null
            }
        }

        return result
    }

    /**
     * Resolves the color from a [ColorDrawable].
     * @param drawable the color drawable
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveColorDrawable(drawable: ColorDrawable): Int? {
        return mergeColorAndAlpha(drawable.color, drawable.alpha)
    }

    /**
     * Resolves the color from a [RippleDrawable].
     * @param drawable the color drawable
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveRippleDrawable(drawable: RippleDrawable): Int? {
        return resolveLayerDrawable(drawable)
    }

    /**
     * Resolves the color from a [LayerDrawable].
     * @param drawable the color drawable
     * @param predicate a predicate to filter which ayers should be taken into account (default: accept all layers)
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveLayerDrawable(
        drawable: LayerDrawable,
        predicate: (Int, Drawable) -> Boolean = { _, _ -> true }
    ): Int? {
        return (0 until drawable.numberOfLayers).map { idx ->
            @Suppress("UnsafeThirdPartyFunctionCall") // layer index can't be out of bounds here
            val childDrawable = drawable.getDrawable(idx)
            if (childDrawable != null && predicate(idx, childDrawable)) {
                mapDrawableToColor(childDrawable)
            } else {
                null
            }
        }.firstOrNull { it != null }
    }

    /**
     * Resolves the color from a [GradientDrawable].
     * @param drawable the color drawable
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveGradientDrawable(drawable: GradientDrawable): Int? {
        val fillPaint = try {
            fillPaintField?.get(drawable) as? Paint
        } catch (e: IllegalArgumentException) {
            (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Unable to read GradientDrawable.mFillPaint field through reflection" },
                e
            )
            null
        } catch (e: IllegalAccessException) {
            (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Unable to read GradientDrawable.mFillPaint field through reflection" },
                e
            )
            null
        }

        if (fillPaint == null) return null

        val fillColor: Int = fillPaint.color
        val fillAlpha = (fillPaint.alpha * drawable.alpha) / MAX_ALPHA_VALUE

        return if (fillAlpha == 0) {
            null
        } else {
            // TODO RUM-3469 resolve other color filter types
            mergeColorAndAlpha(fillColor, fillAlpha)
        }
    }

    /**
     * Resolves the color from an [InsetDrawable].
     * @param drawable the color drawable
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveInsetDrawable(drawable: InsetDrawable): Int? {
        return null
    }

    /**
     * Merges a color (as an (A)RGB int) with an alpha value, replacing the alpha of the original color.
     * @param color a color (as an (A)RGB int)
     * @param alpha the alpha (between 0 and 255)
     * @return a color with the RGB component matching the input color, and alpha component matching the alpha input
     */
    protected fun mergeColorAndAlpha(color: Int, alpha: Int): Int {
        return ((color.toLong() and MASK_COLOR) or (alpha.toLong() shl ALPHA_SHIFT_ANDROID)).toInt()
    }

    companion object {
        @Suppress("PrivateAPI", "SwallowedException", "TooGenericExceptionCaught")
        internal val fillPaintField = try {
            GradientDrawable::class.java.getDeclaredField("mFillPaint").apply {
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
