/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

//noinspection SuspiciousImport
import android.R
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.VectorDrawable
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.utils.ALPHA_SHIFT_ANDROID
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.MASK_RGB
import com.datadog.android.sessionreplay.utils.MAX_ALPHA_VALUE

/**
 * Maps supported drawables to a single color for Session Replay wireframes.
 */
@Suppress("TooManyFunctions")
internal open class AndroidMDrawableToColorMapper(
    private val extensionMappers: List<DrawableToColorMapper> = emptyList()
) : DrawableToColorMapper {

    override fun mapDrawableToColor(drawable: Drawable, internalLogger: InternalLogger): Int? {
        // First check if extension mappers can resolve the drawable
        extensionMappers.forEach {
            val result = it.mapDrawableToColor(drawable, internalLogger)
            if (result != null) {
                return result
            }
        }
        val result = when (drawable) {
            is ColorDrawable -> resolveColorDrawable(drawable)
            is RippleDrawable -> resolveRippleDrawable(drawable, internalLogger)
            is LayerDrawable -> resolveLayerDrawable(drawable, internalLogger)
            is InsetDrawable -> resolveInsetDrawable(drawable, internalLogger)
            is GradientDrawable -> resolveGradientDrawable(drawable, internalLogger)
            is ShapeDrawable -> resolveShapeDrawable(drawable, internalLogger)
            is StateListDrawable -> resolveStateListDrawable(drawable, internalLogger)
            is BitmapDrawable,
            is VectorDrawable -> null // return null without reporting them by telemetry.
            else -> {
                val drawableType = drawable.javaClass.canonicalName ?: drawable.javaClass.name
                internalLogger.log(
                    level = InternalLogger.Level.INFO,
                    target = InternalLogger.Target.TELEMETRY,
                    messageBuilder = { "No mapper found for drawable $drawableType" },
                    throwable = null,
                    onlyOnce = true,
                    additionalProperties = mapOf(
                        "replay.drawable.type" to drawableType
                    )
                )
                null
            }
        }

        return result
    }

    /**
     * Resolves the paint color from a [ShapeDrawable].
     * @param drawable the shape drawable
     * @param internalLogger the internalLogger to report warnings
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveShapeDrawable(
        drawable: ShapeDrawable,
        internalLogger: InternalLogger
    ): Int {
        return drawable.paint.color
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
     * Resolves the color from a [LayerDrawable].
     * @param drawable the color drawable
     * @param internalLogger the internalLogger to report warnings
     * @param predicate a predicate to filter which layers should be taken into account (default: accept all layers)
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveLayerDrawable(
        drawable: LayerDrawable,
        internalLogger: InternalLogger,
        predicate: (Int, Drawable) -> Boolean = { _, _ -> true }
    ): Int? {
        return (0 until drawable.numberOfLayers).map { idx ->
            @Suppress("UnsafeThirdPartyFunctionCall") // layer index can't be out of bounds here
            val childDrawable = drawable.getDrawable(idx)
            if (childDrawable != null && predicate(idx, childDrawable)) {
                mapDrawableToColor(childDrawable, internalLogger)
            } else {
                null
            }
        }.firstOrNull { it != null }
    }

    /**
     * Resolves the state-dependent fill color of a [GradientDrawable], including its alpha.
     * @param drawable the gradient drawable
     * @param internalLogger the internalLogger to report warnings
     * @return the color to map to or null if not applicable
     */
    protected open fun resolveGradientDrawable(drawable: GradientDrawable, internalLogger: InternalLogger): Int? {
        val resolvedColor = resolveGradientFillColor(drawable)
        // Without an accessible color filter, a tint may be missing from the resolved fill color.
        if (resolvedColor == null || drawable.colorFilter == null) return null

        val colorAlpha = (resolvedColor ushr ALPHA_SHIFT_ANDROID) and MAX_ALPHA_VALUE
        val fillAlpha = (colorAlpha * drawable.alpha) / MAX_ALPHA_VALUE
        return if (fillAlpha == 0) null else mergeColorAndAlpha(resolvedColor, fillAlpha)
    }

    protected open fun resolveGradientFillColor(drawable: GradientDrawable): Int? {
        val colorStateList = drawable.color ?: return null
        return colorStateList.getColorForState(drawable.state, colorStateList.defaultColor)
    }

    /**
     * Merges a color (as an (A)RGB int) with an alpha value, replacing the alpha of the original color.
     * @param color a color (as an (A)RGB int)
     * @param alpha the alpha (between 0 and 255)
     * @return a color with the RGB component matching the input color, and alpha component matching the alpha input
     */
    protected fun mergeColorAndAlpha(color: Int, alpha: Int): Int {
        return ((color.toLong() and MASK_RGB) or (alpha.toLong() shl ALPHA_SHIFT_ANDROID)).toInt()
    }

    /**
     * Resolves the color from a [StateListDrawable].
     * @param drawable the color drawable
     * @param internalLogger the internalLogger to report warnings
     * @return the color to map to or null if not applicable
     */
    private fun resolveStateListDrawable(drawable: StateListDrawable, internalLogger: InternalLogger): Int? {
        // Drawable.getCurrent() can return null in case <selector> doesn't have an item for the default case.
        @Suppress("UNNECESSARY_SAFE_CALL")
        return drawable.current?.let { mapDrawableToColor(drawable = it, internalLogger = internalLogger) }
    }

    /**
     * Resolves the color from a [RippleDrawable].
     * @param drawable the color drawable
     * @param internalLogger the internalLogger to report warnings
     * @return the color to map to or null if not applicable
     */
    private fun resolveRippleDrawable(drawable: RippleDrawable, internalLogger: InternalLogger): Int? {
        // A ripple drawable can have a layer marked as mask, and which is not drawn
        // We can reuse the LayerDrawable by passing a way to filter the mask layer
        val maskLayerIndex = drawable.findIndexByLayerId(R.id.mask)
        return resolveLayerDrawable(drawable, internalLogger) { idx, _ -> idx != maskLayerIndex }
    }

    /**
     * Resolves the color from an [InsetDrawable].
     * @param drawable the color drawable
     * @param internalLogger the internalLogger to report warnings
     * @return the color to map to or null if not applicable
     */
    private fun resolveInsetDrawable(drawable: InsetDrawable, internalLogger: InternalLogger): Int? {
        return drawable.drawable?.let { mapDrawableToColor(it, internalLogger) }
    }
}
